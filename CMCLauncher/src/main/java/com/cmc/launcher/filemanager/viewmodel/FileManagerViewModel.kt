package com.cmc.launcher.filemanager.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cmc.launcher.R
import com.cmc.launcher.filemanager.config.FmConfig
import com.cmc.launcher.filemanager.logic.AccessScope
import com.cmc.launcher.filemanager.logic.FileManagerLogic
import com.cmc.launcher.filemanager.logic.compress.CompressOptions
import com.cmc.launcher.filemanager.logic.entry.FmEntry
import com.cmc.launcher.filemanager.logic.ops.ConflictResolution
import com.cmc.launcher.filemanager.logic.task.TaskManager
import com.cmc.launcher.filemanager.logic.task.TaskProgress
import com.cmc.launcher.filemanager.logic.task.TaskState
import com.cmc.launcher.filemanager.logic.trash.TrashItem
import com.cmc.launcher.filemanager.os.FmLog
import com.cmc.launcher.filemanager.viewmodel.controllers.BrowseController
import com.cmc.launcher.filemanager.viewmodel.controllers.CompressController
import com.cmc.launcher.filemanager.viewmodel.controllers.DirectoryScanController
import com.cmc.launcher.filemanager.viewmodel.controllers.EditorController
import com.cmc.launcher.filemanager.viewmodel.controllers.EntryController
import com.cmc.launcher.filemanager.viewmodel.controllers.ExtractController
import com.cmc.launcher.filemanager.viewmodel.controllers.ImportController
import com.cmc.launcher.filemanager.viewmodel.controllers.PasteController
import com.cmc.launcher.filemanager.viewmodel.controllers.SearchController
import com.cmc.launcher.filemanager.viewmodel.controllers.SelectionController
import com.cmc.launcher.filemanager.viewmodel.controllers.TrashController
import com.cmc.launcher.utils.file.shareFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject

sealed interface FmInitState {

    data object Pending : FmInitState

    data object Ready : FmInitState

    data class Failed(val message: String) : FmInitState
}

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val rootPathStr: String = savedStateHandle[KEY_ROOT_PATH]
        ?: throw IllegalStateException("Missing required argument: $KEY_ROOT_PATH")

    private val currentPathStr: String? = savedStateHandle[KEY_CURRENT_PATH]

    private val store = FmStateStore(context)

    private val _initState = MutableStateFlow<FmInitState>(FmInitState.Pending)

    val initState: StateFlow<FmInitState> = _initState.asStateFlow()

    private lateinit var taskManager: TaskManager
    private lateinit var scope: AccessScope
    private lateinit var logic: FileManagerLogic
    private lateinit var browseCtr: BrowseController
    private lateinit var selectionCtr: SelectionController
    private lateinit var pasteCtr: PasteController
    private lateinit var compressCtr: CompressController
    private lateinit var extractCtr: ExtractController
    private lateinit var importCtr: ImportController
    private lateinit var searchCtr: SearchController
    private lateinit var directoryScanCtr: DirectoryScanController
    private lateinit var entryCtr: EntryController
    private lateinit var trashCtr: TrashController
    private lateinit var editorCtr: EditorController

    val state: StateFlow<FileManagerUiState> get() = store.state
    val searchUi: StateFlow<SearchUiState> get() = store.searchUi
    val dirScan: StateFlow<DirScanUiState?> get() = store.dirScan
    val editorUi: StateFlow<EditorUiState> get() = store.editorUi
    val errorEvents: SharedFlow<String> get() = store.errorEvents

    private val _taskProgress = MutableStateFlow<TaskProgress?>(null)

    val taskProgress: StateFlow<TaskProgress?> = _taskProgress.asStateFlow()

    fun initialize() {
        if (_initState.value != FmInitState.Pending) return
        runCatching { createCore() }.fold(
            onSuccess = {
                _initState.value = FmInitState.Ready
                observeTasks()
                browseCtr.refreshDir(store.history.currentPath)
            },
            onFailure = { e ->
                FmLog.error(TAG, "Initialize failed: ${e.message}", e)
                _initState.value = FmInitState.Failed(
                    e.message ?: context.getString(R.string.generic_error)
                )
            }
        )
    }

    private fun createCore() {
        taskManager = TaskManager()
        scope = AccessScope(Paths.get(rootPathStr).normalize().toAbsolutePath())
        logic = FileManagerLogic(
            scope = scope,
            trashRoot = context.cacheDir.toPath().resolve(TRASH_SUBDIR).normalize().toAbsolutePath(),
            cacheRoot = context.cacheDir.toPath().normalize().toAbsolutePath(),
            taskManager = taskManager
        )

        browseCtr = BrowseController(logic, scope, store, viewModelScope)
        selectionCtr = SelectionController(store)
        pasteCtr = PasteController(logic, store, browseCtr, viewModelScope)
        compressCtr = CompressController(context, logic, store, browseCtr, viewModelScope)
        extractCtr = ExtractController(context, logic, scope, store, browseCtr, viewModelScope)
        importCtr = ImportController(context, logic, taskManager, store, pasteCtr, browseCtr, viewModelScope)
        searchCtr = SearchController(logic, taskManager, store, viewModelScope)
        directoryScanCtr = DirectoryScanController(store, viewModelScope)
        entryCtr = EntryController(context, logic, store, browseCtr, viewModelScope)
        trashCtr = TrashController(logic, store, browseCtr, viewModelScope)
        editorCtr = EditorController(store, browseCtr, viewModelScope)

        store.history = NavHistory(logic.resolveInitialCurrent(currentPathStr?.let { Paths.get(it) }))
        store.updateState {
            it.copy(
                currentDir = store.history.currentPath,
                sortConfig = SortConfig.load().also { c -> c.persist() },
                showHidden = FmConfig.showHidden(),
                trashSortConfig = TrashSortConfig.load().also { c -> c.persist() },
                taskState = TaskState.Idle,
                trashView = TrashViewState.Idle,
                canNavigateBack = store.history.canBack,
                canNavigateForward = store.history.canForward
            )
        }
    }

    fun refresh() {
        browseCtr.refreshDir()
    }
    fun navigateTo(path: Path) = browseCtr.navigateTo(path)
    fun enterDirectory(entry: FmEntry) = browseCtr.enterDirectory(entry)
    fun back(): Boolean = browseCtr.back()
    fun forward(): Boolean = browseCtr.forward()
    fun goParent(): Boolean = browseCtr.goParent()
    fun submitJump(targetInput: String): Boolean = browseCtr.submitJump(targetInput)
    fun navigateToSearchHit(hit: SearchHitView) = browseCtr.navigateToSearchHit(hit.path)
    fun setSortConfig(config: SortConfig) = browseCtr.setSortConfig(config)
    fun toggleHidden() = browseCtr.toggleHidden()

    fun toggleSelection(entry: FmEntry) = selectionCtr.toggleSelection(entry)
    fun swipeRangeSelect(entry: FmEntry) = selectionCtr.swipeRangeSelect(entry)
    fun selectAll() = selectionCtr.selectAll()
    fun clearSelection() = selectionCtr.clearSelection()

    fun consumeBack(): Boolean {
        if (store.stateValue().multiSelect) {
            store.clearSelectionAndExitMulti()
            return true
        }

        if (browseCtr.goParent()) return true

        return false
    }

    fun startDirectoryScan(path: Path) = directoryScanCtr.startDirectoryScan(path)
    fun stopDirectoryScan() = directoryScanCtr.stopDirectoryScan()

    fun showSearchDialog() = searchCtr.showSearchDialog()
    fun submitSearch(keyword: String, caseSensitive: Boolean) = searchCtr.submitSearch(keyword, caseSensitive)
    fun clearSearch() = searchCtr.clearSearch()
    fun backToSearchSetup() = searchCtr.backToSearchSetup()

    fun copyEntry(entry: FmEntry) {
        store.setClipboard(FmClipboard(listOf(entry.path), false))
    }
    fun cutEntry(entry: FmEntry) {
        store.setClipboard(FmClipboard(listOf(entry.path), true))
    }
    fun requestPaste() = pasteCtr.requestPaste()
    fun resolvePasteConflict(resolution: ConflictResolution) = pasteCtr.resolvePasteConflict(resolution)

    fun bulkCopy() {
        val src = store.selectedEntries().map { it.path }
        if (!src.isEmpty()) {
            store.setClipboard(FmClipboard(src, false))
        }
        store.dismissDialog()
    }
    fun bulkCut() {
        val src = store.selectedEntries().map { it.path }
        if (!src.isEmpty()) {
            store.setClipboard(FmClipboard(src, true))
        }
        store.dismissDialog()
    }

    fun bulkCompress() = compressCtr.bulkCompress()
    fun compressEntry(entry: FmEntry) = compressCtr.compressEntry(entry)
    fun onCompressSetupConfirmed(name: String, sources: List<Path>, options: CompressOptions) =
        compressCtr.onCompressSetupConfirmed(name, sources, options)
    fun onCompressOutputChoiceCurrent() = compressCtr.onCompressOutputChoiceCurrent()
    fun onCompressOutputChoiceSaf() = compressCtr.onCompressOutputChoiceSaf()
    fun onCompressOutputPickedCancelled() = compressCtr.onCompressOutputPickedCancelled()
    fun onCompressOutputPicked(treeUri: Uri) = compressCtr.onCompressOutputPicked(treeUri)
    fun resolveCompressConflict(resolution: ConflictResolution) = compressCtr.resolveCompressConflict(resolution)

    fun showExtract(entry: FmEntry) = extractCtr.showExtract(entry)
    fun onExtractSetupConfirmed(independentFolder: Boolean) = extractCtr.onExtractSetupConfirmed(independentFolder)
    fun onExtractPasswordConfirmed(password: String) = extractCtr.onExtractPasswordConfirmed(password)
    fun onExtractOutputChoiceCurrent() = extractCtr.onExtractOutputChoiceCurrent()
    fun onExtractOutputChoiceSaf() = extractCtr.onExtractOutputChoiceSaf()
    fun onExtractOutputPicked(treeUri: Uri) = extractCtr.onExtractOutputPicked(treeUri)
    fun onExtractOutputPickedCancelled() = extractCtr.onExtractOutputPickedCancelled()
    fun resolveExtractConflict(resolution: ConflictResolution) = extractCtr.resolveExtractConflict(resolution)

    fun showImportFilesDialog() {
        store.dismissDialog()
        store.updateState {
            it.copy(dialogIntent = DialogIntent.ImportFiles)
        }
    }
    fun showImportDirDialog() {
        store.dismissDialog()
        store.updateState {
            it.copy(dialogIntent = DialogIntent.ImportDir)
        }
    }
    fun onImportFiles(uris: List<Uri>) = importCtr.onImportFiles(uris)
    fun onImportDir(treeUri: Uri) = importCtr.onImportDir(treeUri)
    fun onImportCancelled() {
        store.dismissDialog()
    }

    fun stageSingleDelete(entry: FmEntry) = entryCtr.stageSingleDelete(entry)
    fun cancelStagedDelete() = entryCtr.cancelStagedDelete()
    fun deleteSelected(toTrash: Boolean) = entryCtr.deleteSelected(toTrash)
    fun rename(entry: FmEntry, newName: String, onSuccess: () -> Unit) = entryCtr.rename(entry, newName, onSuccess)
    fun submitCreate(name: String, isFolder: Boolean, onDone: (Boolean) -> Unit) = entryCtr.submitCreate(name, isFolder, onDone)
    fun submitRename(entry: FmEntry, newName: String, onSuccess: () -> Unit) = entryCtr.submitRename(entry, newName, onSuccess)
    fun validateRename(entry: FmEntry, newName: String): String? = entryCtr.validateRename(entry, newName)
    fun showShare(entry: FmEntry) = entryCtr.showShare(entry)

    fun loadTrashList() = trashCtr.loadTrashList()
    fun refreshTrashList() = trashCtr.refreshTrashList()
    fun closeTrash() = trashCtr.closeTrash()
    fun setTrashSortConfig(config: TrashSortConfig) = trashCtr.setTrashSortConfig(config)
    fun trashRestore(items: List<TrashItem>, resolutions: Map<String, ConflictResolution>) = trashCtr.trashRestore(items, resolutions)
    fun beginTrashRestore(items: List<TrashItem>) = trashCtr.beginTrashRestore(items)
    fun resolveTrashRestoreConflict(resolution: ConflictResolution) = trashCtr.resolveTrashRestoreConflict(resolution)
    fun restoreTrashItem(item: TrashItem) = trashCtr.restoreTrashItem(item)
    fun trashRestoreAll() = trashCtr.trashRestoreAll()
    fun purgeTrashItem(item: TrashItem) = trashCtr.purgeTrashItem(item)
    fun trashPurge(items: List<TrashItem>) = trashCtr.trashPurge(items)
    fun trashClear() = trashCtr.trashClear()
    fun toggleTrashSelection(uuid: String) = trashCtr.toggleTrashSelection(uuid)
    fun selectAllTrash() = trashCtr.selectAllTrash()
    fun clearTrashSelection() = trashCtr.clearTrashSelection()
    fun trashRangeSelect(swipeItem: TrashItem) = trashCtr.trashRangeSelect(swipeItem)
    fun selectedTrashItems(): List<TrashItem> = trashCtr.selectedTrashItems()

    fun editorOpen(path: Path) = editorCtr.open(path)

    fun editorTextChanged() = editorCtr.onTextChanged()

    fun editorSave(onDone: (Boolean) -> Unit = {}) = editorCtr.save(onDone)

    fun editorCancelSave() = editorCtr.cancelSave()

    fun editorRequestExitConfirm() = editorCtr.requestExitConfirm()

    fun editorCancelExitConfirm() = editorCtr.cancelExitConfirm()

    fun editorHasDirty(): Boolean = editorCtr.hasDirty()

    fun browseInExternalManager() {
        val dir = store.stateValue().currentDir?.toString() ?: return
        shareFile(context, File(dir))
    }

    fun dismissDialog() = store.dismissDialog()
    fun consumeSnackbar() = store.updateState { it.copy(snackbar = null) }
    fun consumeLocateHighlight() = store.updateState { it.copy(locateHighlightPath = null) }

    fun observeTasks() {
        viewModelScope.launch {
            taskManager.state.collect { st ->
                store.updateState { it.copy(taskState = st) }
            }
        }
        viewModelScope.launch {
            taskManager.progress.collect { p ->
                _taskProgress.value = p
            }
        }
    }

    fun cancelCurrentTask() {
        taskManager.cancel()
    }

    fun appContext(): Context = context

    companion object {
        private const val TAG = "FileManagerViewModel"
        private const val TRASH_SUBDIR = "fileManagerTrash"

        const val KEY_ROOT_PATH = "fm.rootPath"

        const val KEY_CURRENT_PATH = "fm.currentPath"
    }
}
