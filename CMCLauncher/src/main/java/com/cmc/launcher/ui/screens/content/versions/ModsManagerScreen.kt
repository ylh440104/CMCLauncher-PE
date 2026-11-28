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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModsManagerScreen(
    mainScreenKey: TitledNavKey?,
    versionsScreenKey: TitledNavKey?,
    version: Version,
    backToMainScreen: () -> Unit,
    swapToDownload: () -> Unit,
    onSwapMoreInfo: (id: String, Platform) -> Unit,
    eventViewModel: EventViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    val context = LocalContext.current

    if (!version.isValid()) {
        backToMainScreen()
        return
    }

    val hasModLoader = remember(version) {
        version.getVersionInfo()?.loaderInfos?.any { it.loader.isLoader } == true
    }
    val modsDir = remember(version) {
        VersionFolders.MOD.getDir(version.getGameDir())
    }

    BaseScreen(
        levels1 = listOf(
            Pair(NestedNavKey.VersionSettings::class.java, mainScreenKey)
        ),
        Triple(NormalNavKey.Versions.ModsManager, versionsScreenKey, false)
    ) { isVisible ->
        val viewModel = rememberModsManageViewModel(version, modsDir)
        val updaterViewModel = rememberModsUpdaterViewModel(version, modsDir)

        LaunchedEffect(Unit) {
            viewModel.checkCountAndRefresh(context)
        }

        DeleteAllOperation(
            operation = viewModel.deleteAllOperation,
            changeOperation = { viewModel.deleteAllOperation = it },
            submitError = submitError,
            onRefresh = { viewModel.refresh(context) }
        )

        ModsUpdateOperation(
            operation = updaterViewModel.modsUpdateOperation,
            changeOperation = { updaterViewModel.modsUpdateOperation = it },
            modsUpdater = updaterViewModel.modsUpdater,
            onUpdate = { mods ->
                updaterViewModel.update(
                    mods = mods,
                    refreshMods = {

                        viewModel.refresh(context)
                    },
                    showToast = { text, duration ->
                        eventViewModel.sendToast(text, duration)
                    },
                    onStart = {
                        eventViewModel.sendKeepScreen(true)
                    },
                    onStop = {
                        eventViewModel.sendKeepScreen(false)
                    }
                )
            },
            onCancel = {
                updaterViewModel.cancel()
                eventViewModel.sendKeepScreen(false)
            }
        )

        ModsConfirmOperation(
            operation = updaterViewModel.modsConfirmOperation,
            onCancel = {
                updaterViewModel.modsUserConfirm(emptyList())
            },
            onConfirm = { manifests ->
                updaterViewModel.modsUserConfirm(manifests)
            }
        )

        val ctx = rememberAnimatedContext(isVisible = isVisible)

        VersionChunkBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(all = 12.dp)
                .animated(ctx),
            paddingValues = PaddingValues()
        ) {
            PullToRefreshBox(
                modifier = Modifier.fillMaxSize(),
                isRefreshing = false,
                enabled = viewModel.modsState !is LoadingState.Loading,
                onRefresh = { viewModel.refresh(context) }
            ) {
                when (viewModel.modsState) {
                    LoadingState.None -> {
                        var modsOperation by remember { mutableStateOf<ModsOperation>(ModsOperation.None) }
                        var dependencyIssues by remember { mutableStateOf<List<ModIssue>?>(null) }
                        dependencyIssues?.let { issues ->
                            SimpleAlertDialog(
                                title = stringResource(R.string.mods_dependency_check),
                                text = {
                                    Column {
                                        if (issues.isEmpty()) {
                                            Text(stringResource(R.string.mods_dependency_check_empty))
                                        } else {
                                            issues.forEach { issue ->
                                                Text(
                                                    text = when (issue.type) {
                                                        ModIssueType.MISSING_DEPENDENCY -> stringResource(
                                                            R.string.mods_dependency_missing,
                                                            issue.modName,
                                                            issue.detail
                                                        )
                                                        ModIssueType.DUPLICATE_ID -> stringResource(
                                                            R.string.mods_dependency_duplicate,
                                                            issue.modName,
                                                            issue.versions.ifBlank { issue.detail }
                                                        )
                                                        ModIssueType.VERSION_MISMATCH -> stringResource(
                                                            R.string.mods_dependency_version_mismatch,
                                                            issue.modName,
                                                            issue.detail,
                                                            issue.versions
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                onDismiss = { dependencyIssues = null }
                            )
                        }

                        fun runProgress(task: () -> Unit) {
                            viewModel.doInScope {
                                withContext(Dispatchers.IO) {
                                    modsOperation = ModsOperation.Progress
                                    task()
                                    modsOperation = ModsOperation.None
                                    viewModel.refresh(context)
                                }
                            }
                        }

                        ModsOperation(
                            modsOperation = modsOperation,
                            updateOperation = { modsOperation = it },
                            onDelete = { mod ->
                                runProgress {
                                    FileUtils.deleteQuietly(mod.file)
                                }
                            }
                        )

                        Column {
                            val idle = updaterViewModel.modsUpdateOperation == ModsUpdateOperation.None &&
                                viewModel.deleteAllOperation == DeleteAllOperation.None
                            PackToolbar(
                                query = viewModel.nameFilter,
                                onQuery = { viewModel.updateFilter(it, context) },
                                sortOptions = viewModel.supportedSortByEnums,
                                sortBy = viewModel.sortByEnum,
                                onSortBy = { viewModel.updateSortBy(it, context) },
                                ascending = viewModel.isAscending,
                                onReverse = { viewModel.updateSortOrder(context) },
                                onRefresh = { viewModel.refresh(context) },
                                onDownload = swapToDownload,
                                onImport = rememberPackImport("ContentManager.Mods.Import", modsDir, submitError, extension = "jar") {
                                    viewModel.refresh(context)
                                },
                                selection = LibrarySelection(
                                    count = viewModel.selectedMods.size,
                                    selectAll = viewModel::selectAllMods,
                                    clear = viewModel::clearSelected,
                                    requestDelete = {
                                        val files = viewModel.selectedMods.map { it.localMod.file }
                                        if (idle && files.isNotEmpty()) {
                                            viewModel.deleteAllOperation = DeleteAllOperation.Warning(files = files)
                                        }
                                    },
                                    checkUpdates = if (idle && hasModLoader && viewModel.canUpdate) ({
                                        updaterViewModel.modsUpdateOperation = ModsUpdateOperation.Warning(viewModel.selectedMods.toList())
                                    }) else null
                                ),
                                filterMenuItem = { dismiss ->
                                    ModStateFilter.entries.forEach { filter ->
                                        val count = when (filter) {
                                            ModStateFilter.Enabled -> viewModel.enabledCount
                                            ModStateFilter.Disabled -> viewModel.disabledCount
                                            ModStateFilter.All -> viewModel.allMods.size
                                        }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(filter.textRes) + if (count >= 0) " ($count)" else "") },
                                            trailingIcon = {
                                                if (filter == viewModel.stateFilter) Icon(painterResource(R.drawable.ic_check), null)
                                            },
                                            onClick = { viewModel.updateStateFilter(filter, context); dismiss() }
                                        )
                                    }
                                    if (hasModLoader) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.mods_dependency_check)) },
                                            onClick = {
                                                dependencyIssues = ModIssueChecker.check(viewModel.allMods.map { it.localMod })
                                                dismiss()
                                            }
                                        )
                                    }
                                }
                            )

                            ModsList(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                hasModLoader = hasModLoader,
                                modsList = viewModel.filteredMods,
                                issuePaths = viewModel.issuePaths,
                                selectedMods = viewModel.selectedMods,
                                removeFromSelected = { mod ->
                                    viewModel.selectedMods.remove(mod)
                                    viewModel.checkCanUpdate()
                                },
                                addToSelected = { mod ->
                                    viewModel.selectedMods.add(mod)
                                    viewModel.checkCanUpdate()
                                },
                                onLoad = { mod ->
                                    viewModel.loadMod(mod)
                                },
                                onForceRefresh = { mod ->
                                    viewModel.loadMod(mod, loadFromCache = false)
                                },
                                onEnable = { mod ->

                                    viewModel.doInScope {
                                        withContext(Dispatchers.IO) {
                                            mod.localMod.enable()
                                        }
                                        withContext(Dispatchers.Main) {
                                            viewModel.refreshCounter()
                                        }
                                    }
                                },
                                onDisable = { mod ->
                                    viewModel.doInScope {
                                        withContext(Dispatchers.IO) {
                                            mod.localMod.disable()
                                        }
                                        withContext(Dispatchers.Main) {
                                            viewModel.refreshCounter()
                                        }
                                    }
                                },
                                onSwapMoreInfo = onSwapMoreInfo,
                                onDelete = { mod ->
                                    modsOperation = ModsOperation.Delete(mod.localMod)
                                }
                            )
                        }
                    }
                    LoadingState.Loading -> {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            StaticBusyIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModsList(
    modifier: Modifier = Modifier,
    hasModLoader: Boolean,
    modsList: List<RemoteMod>?,
    issuePaths: Set<String>,
    selectedMods: List<RemoteMod>,
    removeFromSelected: (RemoteMod) -> Unit,
    addToSelected: (RemoteMod) -> Unit,
    onLoad: (RemoteMod) -> Unit,
    onForceRefresh: (RemoteMod) -> Unit,
    onEnable: (RemoteMod) -> Unit,
    onDisable: (RemoteMod) -> Unit,
    onSwapMoreInfo: (id: String, Platform) -> Unit,
    onDelete: (RemoteMod) -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val scrollState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nonInteractiveScrollbar(
                    state = scrollState.scrollIndicatorState!!,
                    orientation = Orientation.Vertical,
                ),
            contentPadding = PaddingValues(all = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = scrollState,
        ) {
            if (!hasModLoader) {
                item(key = "warning_no_modloader") {
                    WarningItem(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.mods_manage_no_loader),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }

            val list = modsList ?: emptyList()
            items(list, key = { it.localMod.file.absolutePath }, contentType = { "mod" }) { mod ->
                ModItemLayout(
                    modifier = Modifier.fillMaxWidth(),
                    mod = mod,
                    onLoad = {
                        onLoad(mod)
                    },
                    onForceRefresh = {
                        onForceRefresh(mod)
                    },
                    onClick = {

                        if (selectedMods.contains(mod)) {
                            removeFromSelected(mod)
                        } else {
                            addToSelected(mod)
                        }
                    },
                    onEnable = {
                        onEnable(mod)
                    },
                    onDisable = {
                        onDisable(mod)
                    },
                    onSwapMoreInfo = onSwapMoreInfo,
                    onDelete = {
                        onDelete(mod)
                    },
                    hasIssue = mod.localMod.file.absolutePath in issuePaths,
                    selected = selectedMods.contains(mod)
                )
            }
        }

        if (modsList == null) {

            ScalingLabel(
                text = stringResource(R.string.mods_manage_no_mods)
            )
        } else if (modsList.isEmpty()) {

            ScalingLabel(
                text = stringResource(R.string.generic_no_matching_items)
            )
        }
    }
}

@Composable
private fun ModItemLayout(
    modifier: Modifier,
    mod: RemoteMod,
    selected: Boolean,
    onLoad: () -> Unit,
    onForceRefresh: () -> Unit,
    onClick: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onSwapMoreInfo: (String, Platform) -> Unit,
    onDelete: () -> Unit,
    hasIssue: Boolean
) {
    val context = LocalContext.current
    val local = mod.localMod
    val project = mod.projectInfo
    val active = local.file.isEnabled()
    val title = when {
        project != null -> mod.mcMod?.getMcmodTitle(project.title, context) ?: project.title
        local.notMod || local.name.isBlank() -> local.file.name
        else -> local.name
    }
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember(local.file) { mutableStateOf(false) }
    LaunchedEffect(mod) { onLoad() }

    Surface(
        modifier = modifier,
        shape = FieldShape,
        color = itemColor(),
        border = if (selected) BorderStroke(Size.stroke, colors.primary) else null
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f)
                        .toggleable(value = selected, role = Role.Checkbox, indication = null,
                            interactionSource = null, onValueChange = { onClick() })
                        .heightIn(min = Size.touch).padding(Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        ModIcon(Modifier.clip(FieldShape), mod = mod, iconSize = 40.dp)
                        if (selected) {
                            Surface(Modifier.align(Alignment.BottomEnd), shape = CircleShape,
                                color = colors.primary, contentColor = colors.onPrimary) {
                                Icon(painterResource(R.drawable.ic_check), null, Modifier.padding(2.dp).size(14.dp))
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Space.xxs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasIssue) {
                                Icon(
                                    modifier = Modifier.size(16.dp),
                                    painter = painterResource(R.drawable.ic_warning_outlined),
                                    contentDescription = null,
                                    tint = colors.error
                                )
                            }
                            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (active) colors.onSurface else colors.onSurfaceVariant)
                        }
                        if (title != local.file.name) {
                            Text(local.file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant)
                        }
                        val loaders = mod.remoteFile?.loaders
                        if (!loaders.isNullOrEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                loaders.forEach { Text(it.getDisplayName(), style = MaterialTheme.typography.labelSmall) }
                            }
                        } else if (local.loader != ModLoader.UNKNOWN) {
                            Text(local.loader.displayName, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.generic_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.generic_refresh)) },
                            enabled = !mod.isLoading && mod.isLoaded,
                            onClick = { menuOpen = false; onForceRefresh() }
                        )
                        if (project != null) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.mods_manage_info)) },
                                onClick = { menuOpen = false; onSwapMoreInfo(project.id, project.platform) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.generic_delete), color = colors.error) },
                            onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).toggleable(value = active, role = Role.Switch, indication = null,
                        interactionSource = null, onValueChange = { if (it) onEnable() else onDisable() })
                        .heightIn(min = Size.touch),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(checked = active, onCheckedChange = null)
                    Text(stringResource(if (active) R.string.generic_enabled else R.string.generic_disabled),
                        style = MaterialTheme.typography.labelMedium)
                }
                if (mod.isLoading) Text(stringResource(R.string.generic_loading), style = MaterialTheme.typography.labelSmall)
                if (project == null && !local.notMod) LocalModInfoTooltip(local)
            }
        }
    }
}

@Composable
private fun ModIcon(
    modifier: Modifier = Modifier,
    mod: RemoteMod,
    iconSize: Dp,
) {
    Box(modifier = modifier) {
        val colorMatrix = remember(mod, mod.localMod.file) { ColorMatrix() }
        colorMatrix.setToSaturation(
            if (mod.localMod.file.isDisabled()) 0f
            else 1f
        )

        val projectInfo = mod.projectInfo
        if (projectInfo == null) {
            if (mod.localMod.icon == null) {
                ModLoaderIcon(
                    modifier = Modifier.size(iconSize),
                    modloader = mod.localMod.loader,
                    defaultIcon = R.drawable.ic_unknown_pack,
                    colorFilter = ColorFilter.colorMatrix(colorMatrix),

                )
            } else {
                ByteArrayIcon(
                    modifier = Modifier.size(iconSize),
                    triggerRefresh = mod,
                    icon = mod.localMod.icon,
                    colorFilter = ColorFilter.colorMatrix(colorMatrix),
                )
            }
        } else {
            AddonIcon(
                iconUrl = projectInfo.iconUrl,
                size = iconSize,
                colorFilter = ColorFilter.colorMatrix(colorMatrix)
            )
        }
    }
}

@Composable
private fun WarningItem(
    modifier: Modifier = Modifier,
    itemColor: Color = itemColor(),
    itemContentColor: Color = onItemColor(),
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = itemColor,
        contentColor = itemContentColor,
    ) {
        Row(
            modifier = Modifier.padding(all = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(all = 8.dp),
                    painter = painterResource(R.drawable.ic_warning_filled),
                    contentDescription = stringResource(R.string.generic_warning),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LocalModInfoTooltip(
    mod: LocalMod
) {
    TooltipIconButton(
        modifier = Modifier.size(38.dp),
        tooltip = {
            RichTooltip(
                modifier = Modifier.padding(all = 3.dp),
                title = { Text(text = stringResource(R.string.mods_manage_info)) },
                shadowElevation = 3.dp
            ) {
                Column {

                    Text(text = stringResource(R.string.generic_file_size, formatFileSize(mod.fileSize)))

                    mod.version?.let { version ->
                        Text(text = stringResource(R.string.mods_manage_version, version))
                    }

                    Row {
                        Text(text = stringResource(R.string.mods_manage_authors))
                        FlowRow(
                            modifier = Modifier.weight(1f, fill = false),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            mod.authors.forEach { author ->
                                Text(text = author)
                            }
                        }
                    }

                    mod.description?.takeIf { it.isNotEmptyOrBlank() }?.let { description ->
                        Row {
                            Text(text = stringResource(R.string.mods_manage_description))
                            Text(
                                modifier = Modifier.weight(1f, fill = false),
                                text = description
                            )
                        }
                    }
                }
            }
        }
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_info_outlined),
            contentDescription = stringResource(R.string.mods_manage_info)
        )
    }
}
