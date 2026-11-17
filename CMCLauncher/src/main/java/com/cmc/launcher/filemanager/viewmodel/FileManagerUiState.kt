package com.cmc.launcher.filemanager.viewmodel

import com.cmc.launcher.filemanager.logic.entry.FmEntry
import com.cmc.launcher.filemanager.logic.entry.FmListResult
import com.cmc.launcher.filemanager.logic.ops.ConflictResolution
import com.cmc.launcher.filemanager.logic.ops.DirStats
import com.cmc.launcher.filemanager.logic.ops.PasteRequest
import com.cmc.launcher.filemanager.logic.task.TaskState
import com.cmc.launcher.filemanager.logic.trash.TrashItem
import com.cmc.launcher.ui.code_editor.EditorState
import java.nio.file.Path

data class DirScanUiState(
    val running: Boolean,
    val stats: DirStats?
)

data class EditorUiState(

    val path: Path? = null,

    val state: EditorState = EditorState.Loading,

    val writable: Boolean = true,

    val dirty: Boolean = false,

    val saving: Boolean = false,

    val exitConfirm: Boolean = false,

    val error: String? = null
)

data class SearchUiState(

    val running: Boolean = false,

    val currentDir: Path? = null,

    val hits: List<SearchHitView> = emptyList(),

    val lastKeyword: String = ""
)

data class SearchHitView(
    val path: Path,
    val name: String,
    val isDirectory: Boolean,
    val size: Long
)

data class FileManagerUiState(
    val currentDir: Path? = null,

    val refreshing: Boolean = false,
    val visibleEntries: List<FmEntry> = emptyList(),
    val rawList: RawList? = null,
    val folderCount: Int = 0,
    val fileCount: Int = 0,
    val selection: Set<String> = emptySet(),
    val multiSelect: Boolean = false,
    val clipboard: FmClipboard? = null,
    val sortConfig: SortConfig = SortConfig(),
    val showHidden: Boolean = false,
    val trashSortConfig: TrashSortConfig = TrashSortConfig(),
    val taskState: TaskState = TaskState.Idle,
    val trashView: TrashViewState = TrashViewState.Idle,
    val snackbar: FmSnackbar? = null,
    val locateHighlightPath: Path? = null,
    val canNavigateBack: Boolean = false,
    val canNavigateForward: Boolean = false,

    val dialogIntent: DialogIntent? = null
) {
    val canBack: Boolean get() = rawList?.let { it.currentDir != it.rootDir } ?: false
}

data class RawList(
    val currentDir: Path,
    val rootDir: Path,
    val ancestors: List<Path>,
    val entries: List<FmEntry>,
    val hasSubdirectory: Boolean,
    val writable: Boolean
) {
    companion object {
        fun of(result: FmListResult): RawList = RawList(
            currentDir = result.currentDir,
            rootDir = result.rootDir,
            ancestors = result.ancestors,
            entries = result.entries,
            hasSubdirectory = result.hasSubdirectory,
            writable = result.writable
        )
    }
}

sealed interface TrashViewState {
    data object Idle : TrashViewState

    data class Opened(
        val rawItems: List<TrashItem>,
        val trashListView: TrashListView
    ) : TrashViewState
}

data class TrashItemView(
    val uuid: String,
    val name: String,
    val deletedAt: Long,
    val isFolder: Boolean,
    val size: Long,
    val corrupted: Boolean
)

data class TrashListView(
    val items: List<TrashItemView> = emptyList(),
    val totalSize: Long = 0L,
    val total: Int = 0,
    val loading: Boolean = false,
    val selection: Set<String> = emptySet(),
    val multiSelect: Boolean = false
)

data class FmSnackbar(
    val text: String,
    val long: Boolean = true
)

sealed interface DialogIntent {

    data object Search : DialogIntent

    data object SearchTask : DialogIntent

    data object SearchResult : DialogIntent

    data class CompressSetup(
        val defaultName: String,
        val sources: List<Path>
    ) : DialogIntent

    data object CompressOutputChoice : DialogIntent

    data object CompressOutputPick : DialogIntent

    data class CompressConflict(
        val fileName: String
    ) : DialogIntent

    data class ExtractSetup(
        val archivePath: Path,
        val archiveName: String
    ) : DialogIntent

    data object ExtractOutputChoice : DialogIntent

    data object ExtractOutputPick : DialogIntent

    data class ExtractConflict(
        val name: String
    ) : DialogIntent

    data class ExtractPassword(
        val errorText: String? = null
    ) : DialogIntent

    data object ImportFiles : DialogIntent

    data object ImportDir : DialogIntent

    data class PasteConflict(
        val request: PasteRequest.ResolveRequest,
        val decidedResolutions: List<ConflictResolution> = emptyList(),
        val currentIndex: Int = 0
    ) : DialogIntent

    data class TrashRestoreConflict(
        val trashItems: List<TrashItem>,
        val conflictItems: List<Pair<TrashItem, Int>>,
        val resolutions: Map<String, ConflictResolution> = emptyMap(),
        val pendingIndex: Int = 0
    ) : DialogIntent
}
