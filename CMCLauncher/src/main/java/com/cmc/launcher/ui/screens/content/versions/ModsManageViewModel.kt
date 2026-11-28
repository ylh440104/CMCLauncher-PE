package com.cmc.launcher.ui.screens.content.versions

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.cmc.launcher.ui.theme.FieldShape
import com.cmc.launcher.ui.theme.Size
import com.cmc.launcher.ui.theme.Space
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import com.cmc.launcher.ui.components.StaticBusyIndicator
import com.cmc.launcher.ui.components.SimpleAlertDialog
import com.cmc.launcher.ui.components.StaticDropdownMenu as DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cmc.launcher.R
import com.cmc.launcher.coroutine.TaskSystem
import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.utils.getMcmodTitle
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.installed.VersionFolders
import com.cmc.launcher.game.version.mod.AllModReader
import com.cmc.launcher.game.version.mod.LocalMod
import com.cmc.launcher.game.version.mod.ModIssue
import com.cmc.launcher.game.version.mod.ModIssueChecker
import com.cmc.launcher.game.version.mod.ModIssueType
import com.cmc.launcher.game.version.mod.RemoteMod
import com.cmc.launcher.game.version.mod.isDisabled
import com.cmc.launcher.game.version.mod.isEnabled
import com.cmc.launcher.game.version.mod.update.ModManifest
import com.cmc.launcher.game.version.mod.update.ModUpdater
import com.cmc.launcher.game.version.mod.update.SelectableModManifest
import com.cmc.launcher.game.version.mod.update.toSelectableList
import com.cmc.launcher.ui.AndroidStringText
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.ui.base.BaseScreen
import com.cmc.launcher.ui.components.CardTitleLayout
import com.cmc.launcher.ui.components.EdgeDirection
import com.cmc.launcher.ui.components.IconTextButton
import com.cmc.launcher.ui.components.LittleTextLabel
import com.cmc.launcher.ui.components.ScalingLabel
import com.cmc.launcher.ui.components.SimpleTextInputField
import com.cmc.launcher.ui.components.TooltipIconButton
import com.cmc.launcher.ui.components.animated
import com.cmc.launcher.ui.components.fadeEdge
import com.cmc.launcher.ui.components.rememberAnimatedContext
import com.cmc.launcher.ui.screens.NestedNavKey
import com.cmc.launcher.ui.screens.NormalNavKey
import com.cmc.launcher.ui.screens.TitledNavKey
import com.cmc.launcher.ui.screens.content.download.addon.elements.AddonIcon
import com.cmc.launcher.ui.screens.content.elements.ImportMultipleFileButton
import com.cmc.launcher.ui.screens.content.elements.ModLoaderIcon
import com.cmc.launcher.ui.screens.content.elements.SortByDropdownMenu
import com.cmc.launcher.ui.screens.content.elements.SortByEnum
import com.cmc.launcher.ui.screens.content.elements.rememberMultipleUriImportTaskBuilder
import com.cmc.launcher.ui.screens.content.versions.elements.ByteArrayIcon
import com.cmc.launcher.ui.screens.content.versions.elements.DeleteAllOperation
import com.cmc.launcher.ui.screens.content.versions.elements.LoadingState
import com.cmc.launcher.ui.screens.content.versions.elements.ModStateFilter
import com.cmc.launcher.ui.screens.content.versions.elements.ModsConfirmOperation
import com.cmc.launcher.ui.screens.content.versions.elements.ModsOperation
import com.cmc.launcher.ui.screens.content.versions.elements.ModsUpdateOperation
import com.cmc.launcher.ui.screens.content.versions.elements.filterMods
import com.cmc.launcher.ui.screens.content.versions.layouts.LibrarySelection
import com.cmc.launcher.ui.screens.content.versions.layouts.PackToolbar
import com.cmc.launcher.ui.screens.content.versions.layouts.VersionChunkBackground
import com.cmc.launcher.ui.screens.content.versions.layouts.rememberPackImport
import com.cmc.launcher.ui.theme.itemColor
import com.cmc.launcher.ui.theme.onItemColor
import com.cmc.launcher.utils.animation.getAnimateTween
import com.cmc.launcher.utils.file.FolderFileCounter
import com.cmc.launcher.utils.file.formatFileSize
import com.cmc.launcher.utils.string.isNotEmptyOrBlank
import com.cmc.launcher.viewmodel.ErrorViewModel
import com.cmc.launcher.viewmodel.EventViewModel
import com.cmc.launcher.viewmodel.sendKeepScreen
import com.cmc.launcher.viewmodel.sendToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.LinkedList
import java.util.Locale
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

internal class ModsManageViewModel(
    modsDir: File
) : ViewModel() {
    val modReader = AllModReader(modsDir)

    var nameFilter by mutableStateOf("")
        private set
    var stateFilter by mutableStateOf(ModStateFilter.All)
        private set
    var sortByEnum by mutableStateOf(SortByEnum.FileName)
        private set
    var isAscending by mutableStateOf(true)
        private set

    var allMods by mutableStateOf<List<RemoteMod>>(emptyList())
        private set
    var filteredMods by mutableStateOf<List<RemoteMod>?>(null)
        private set

    var enabledCount by mutableIntStateOf(-1)
        private set
    var issuePaths by mutableStateOf<Set<String>>(emptySet())
        private set

    var disabledCount by mutableIntStateOf(-1)
        private set

    val selectedMods = mutableStateListOf<RemoteMod>()

    var canUpdate by mutableStateOf(false)
        private set

    var deleteAllOperation by mutableStateOf<DeleteAllOperation>(DeleteAllOperation.None)

    private val modsToLoad = mutableListOf<RemoteMod>()
    private val loadQueue = LinkedList<Pair<RemoteMod, Boolean>>()
    private val semaphore = Semaphore(8)
    private var initialQueueSize = 0
    private val queueMutex = Mutex()

    var modsState by mutableStateOf<LoadingState>(LoadingState.None)

    private var modsCount = FolderFileCounter(modsDir)

    private var job: Job? = null

    fun refresh(
        context: Context? = null,
        checkCount: Boolean = true
    ) {
        job?.cancel()
        job = viewModelScope.launch {
            withContext(Dispatchers.Main) {
                enabledCount = -1
                disabledCount = -1
            }
            modsState = LoadingState.Loading
            selectedMods.clear()
            canUpdate = false

            if (checkCount) modsCount.checkDir()
            try {
                allMods = modReader.readAllForRemote()
                issuePaths = ModIssueChecker.check(allMods.map { it.localMod }).map { it.filePath }.toSet()
                filterMods(context)
            } catch (_: CancellationException) {

            }
            modsState = LoadingState.None
            job = null
        }
    }

    fun checkCountAndRefresh(
        context: Context? = null,
    ) {
        val isUnchecked = modsCount.isUnchecked()
        if (modsCount.checkDir() && !isUnchecked && job == null) {
            refresh(context = context, checkCount = false)
        }
    }

    fun refreshCounter() {
        allMods.also { list ->
            val counts = list.fold(Pair(0, 0)) { (enabled, disabled), mod ->
                when {
                    mod.localMod.file.isEnabled() -> Pair(enabled + 1, disabled)
                    mod.localMod.file.isDisabled() -> Pair(enabled, disabled + 1)
                    else -> Pair(enabled, disabled)
                }
            }
            enabledCount = counts.first
            disabledCount = counts.second
        }
    }

    init {
        refresh(checkCount = false)
        startQueueProcessor()
    }

    fun updateFilter(name: String, context: Context? = null) {
        this.nameFilter = name
        filterMods(context)
    }

    fun updateStateFilter(filter: ModStateFilter, context: Context? = null) {
        this.stateFilter = filter
        filterMods(context)
    }

    fun updateSortBy(sortByEnum: SortByEnum, context: Context? = null) {
        this.sortByEnum = sortByEnum
        filterMods(context)
    }

    fun updateSortOrder(context: Context? = null) {
        this.isAscending = !this.isAscending
        filterMods(context)
    }

    val supportedSortByEnums = listOf(
        SortByEnum.FileName, SortByEnum.FileModifiedTime
    )

    private var filterJob: Job? = null

    private fun filterMods(context: Context? = null) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            refreshCounter()
            val result = allMods
                .takeIf { it.isNotEmpty() }
                ?.filterMods(nameFilter, stateFilter, context)
                ?.let { mods ->

                    val entries = mods.map { mod ->
                        val file = mod.localMod.file
                        mod to ModsSortKey(
                            name = file.name,
                            lowercaseName = file.name.lowercase(Locale.ROOT),
                            lastModified = file.lastModified()
                        )
                    }
                    entries
                        .sortedWith(compareBy(modsComparator(sortByEnum, isAscending)) { it.second })
                        .map { (mod, _) -> mod }
                }
            if (!isActive) return@launch
            filteredMods = result
        }
    }

    fun selectAllMods() {
        filteredMods?.forEach { mod ->
            if (!selectedMods.contains(mod)) {
                selectedMods.add(mod)
            }
        }
        checkCanUpdate()
    }

    fun clearSelected() {
        filteredMods?.let {
            selectedMods.removeAll(it)
        }
        checkCanUpdate()
    }

    fun checkCanUpdate() {

        canUpdate = selectedMods.any { it.localMod.checkRemote }
    }

    fun doInScope(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
        }
    }

    private fun startQueueProcessor() {
        viewModelScope.launch {
            while (true) {
                try {
                    ensureActive()
                } catch (_: Exception) {
                    break
                }

                val task = queueMutex.withLock {
                    loadQueue.poll()
                } ?: run {
                    delay(100.milliseconds)
                    continue
                }

                val (mod, loadFromCache) = task
                semaphore.acquire()

                launch {
                    try {
                        mod.load(loadFromCache)
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
    }

    fun loadMod(mod: RemoteMod, loadFromCache: Boolean = true) {

        if (!loadFromCache) {
            doInScope {
                queueMutex.withLock {
                    loadQueue.removeAll { it.first == mod }
                    loadQueue.addFirst(mod to false)
                }
            }
            if (modsToLoad.contains(mod)) return
            modsToLoad.add(mod)
            return
        }

        if (modsToLoad.contains(mod)) return

        modsToLoad.add(mod)
        doInScope {
            queueMutex.withLock {
                val canJoin = loadQueue.size <= (initialQueueSize / 2)
                if (canJoin || loadQueue.none { it.first == mod }) {
                    loadQueue.add(mod to true)
                    modsToLoad.add(mod)

                    if (initialQueueSize == 0 || canJoin) {
                        initialQueueSize = loadQueue.size
                    }
                }
            }
        }
    }

    override fun onCleared() {
        viewModelScope.cancel()
    }
}

private data class ModsSortKey(

    val name: String,

    val lowercaseName: String,
    val lastModified: Long
)

private fun modsComparator(sortByEnum: SortByEnum, isAscending: Boolean): Comparator<ModsSortKey> {
    val comparator: Comparator<ModsSortKey> = when (sortByEnum) {
        SortByEnum.FileName -> compareBy({ it.lowercaseName }, { it.name })
        SortByEnum.FileModifiedTime -> compareByDescending { it.lastModified }
        else -> error("This sorting method is not supported: $sortByEnum")
    }

    return if (isAscending) comparator else comparator.reversed()
}

internal class ModsUpdaterViewModel(
    private val modsDir: File,
    private val version: Version
) : ViewModel() {
    var modsUpdateOperation by mutableStateOf<ModsUpdateOperation>(ModsUpdateOperation.None)
    var modsConfirmOperation by mutableStateOf<ModsConfirmOperation>(ModsConfirmOperation.None)
        private set

    private var waitingUserContinuation: (Continuation<List<SelectableModManifest>>)? = null
    suspend fun waitingForUserConfirm(list: List<ModManifest>): List<SelectableModManifest> {
        return suspendCancellableCoroutine { cont ->
            waitingUserContinuation = cont
            modsConfirmOperation = ModsConfirmOperation.WaitingConfirm(list.toSelectableList())
        }
    }

    fun modsUserConfirm(manifests: List<SelectableModManifest>) {
        waitingUserContinuation?.resume(manifests)
        waitingUserContinuation = null
        modsConfirmOperation = ModsConfirmOperation.None
    }

    var modsUpdater by mutableStateOf<ModUpdater?>(null)

    fun update(
        mods: List<RemoteMod>,
        refreshMods: () -> Unit,
        showToast: (AndroidStringText, duration: Int) -> Unit,
        onStart: () -> Unit = {},
        onStop: () -> Unit = {}
    ) {
        val minecraftVer = version.getVersionInfo()!!.minecraftVersion

        val modLoader = version.getVersionInfo()!!.primaryLoader?.loader ?: ModLoader.UNKNOWN

        modsUpdater = ModUpdater(
            mods = mods,
            modsDir = modsDir,
            minecraft = minecraftVer,
            modLoader = modLoader,
            scope = viewModelScope,
            waitForUserConfirm = ::waitingForUserConfirm
        ).also {
            modsUpdateOperation = ModsUpdateOperation.Update
            it.updateAll(
                onUpdated = {
                    modsUpdater = null
                    refreshMods()
                    modsUpdateOperation = ModsUpdateOperation.Success
                    onStop()
                },
                onNoModUpdates = {
                    showToast(androidText(R.string.mods_update_no_mods_update), Toast.LENGTH_SHORT)
                    modsUpdater = null
                    modsUpdateOperation = ModsUpdateOperation.None
                    onStop()
                },
                onCancelled = {
                    modsUpdater = null
                    modsUpdateOperation = ModsUpdateOperation.None
                    onStop()
                },
                onError = { th ->
                    modsUpdater = null
                    refreshMods()
                    modsUpdateOperation = ModsUpdateOperation.Error(th)
                    onStop()
                }
            )
        }
        onStart()
    }

    fun cancel() {
        modsUpdater?.cancel()
        modsUpdater = null
        modsUpdateOperation = ModsUpdateOperation.None
    }

    override fun onCleared() {
        cancel()
    }
}

@Composable
internal fun rememberModsManageViewModel(
    version: Version,
    modsDir: File
): ModsManageViewModel {
    return viewModel(
        key = version.toString() + "_" + VersionFolders.MOD.folderName
    ) {
        ModsManageViewModel(modsDir)
    }
}

@Composable
internal fun rememberModsUpdaterViewModel(
    version: Version,
    modsDir: File
): ModsUpdaterViewModel {
    return viewModel(
        key = version.toString() + "_ModsUpdater"
    ) {
        ModsUpdaterViewModel(modsDir = modsDir, version = version)
    }
}

