package com.cmc.launcher.ui.screens.main

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import com.cmc.launcher.BuildKeys
import com.cmc.launcher.R
import com.cmc.launcher.coroutine.TaskSystem
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.installed.VersionsManager
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.ui.AndroidStringText
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.ui.components.BackgroundCard
import com.cmc.launcher.ui.components.CardTitleLayout
import com.cmc.launcher.ui.screens.BackStackNavKey
import com.cmc.launcher.ui.screens.NavStateHost
import com.cmc.launcher.ui.screens.NestedNavDisplay
import com.cmc.launcher.ui.screens.NestedNavKey
import com.cmc.launcher.ui.screens.NormalNavKey
import com.cmc.launcher.ui.screens.TitledNavKey
import com.cmc.launcher.ui.screens.content.AccountManageScreen
import com.cmc.launcher.ui.screens.content.DownloadScreen
import com.cmc.launcher.ui.screens.content.FileSelectorScreen
import com.cmc.launcher.ui.screens.content.LauncherScreen
import com.cmc.launcher.ui.screens.content.LogViewScreen
import com.cmc.launcher.ui.screens.content.MultiplayerScreen
import com.cmc.launcher.ui.screens.content.SettingsScreen
import com.cmc.launcher.ui.screens.content.VersionExportScreen
import com.cmc.launcher.ui.screens.content.VersionSettingsScreen
import com.cmc.launcher.ui.screens.content.VersionsManageScreen
import com.cmc.launcher.ui.screens.content.WebViewScreen
import com.cmc.launcher.ui.screens.content.addoninfo.AddonInfoScreen
import com.cmc.launcher.ui.screens.content.navigateToDownload
import com.cmc.launcher.ui.screens.navigateTo
import com.cmc.launcher.ui.screens.onBack
import com.cmc.launcher.ui.screens.rememberTransitionSpec
import com.cmc.launcher.ui.theme.Alpha
import com.cmc.launcher.ui.theme.Space
import com.cmc.launcher.ui.theme.backgroundColor
import com.cmc.launcher.ui.theme.cardColor
import com.cmc.launcher.ui.theme.onBackgroundColor
import com.cmc.launcher.ui.theme.onCardColor
import com.cmc.launcher.utils.file.formatFileSize
import com.cmc.launcher.viewmodel.ErrorViewModel
import com.cmc.launcher.viewmodel.EventViewModel
import com.cmc.launcher.viewmodel.LocalBackgroundViewModel
import com.cmc.launcher.viewmodel.ModifyVersionViewModel
import com.cmc.launcher.viewmodel.ModpackImportViewModel
import com.cmc.launcher.viewmodel.ScreenBackStackViewModel
import com.cmc.launcher.viewmodel.sendKeepScreen
import com.cmc.launcher.viewmodel.sendToast
import com.cmc.launcher.setting.AllSettings

private data class NavDestination(
    val title: String,
    val icon: Int,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
fun MainScreen(
    screenBackStackModel: ScreenBackStackViewModel,
    eventViewModel: EventViewModel,
    modpackImportViewModel: ModpackImportViewModel,
    modifyVersionViewModel: ModifyVersionViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    var taskRunning by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        TaskSystem.tasksFlow.collect { tasks ->
            val noTasks = tasks.isEmpty()
            eventViewModel.sendKeepScreen(!noTasks)
            taskRunning = noTasks
        }
    }

    val isTaskMenuExpanded = AllSettings.launcherTaskMenuExpanded.state

    val toMainScreen: () -> Unit = {
        screenBackStackModel.mainScreen.clearWith(NormalNavKey.LauncherMain)
    }

    val mainScreenKey = screenBackStackModel.mainScreen.currentKey
    val inLauncherScreen = mainScreenKey == null || mainScreenKey is NormalNavKey.LauncherMain
    val inMultiplayerScreen = mainScreenKey is NormalNavKey.Multiplayer
    val inDownloadScreen = mainScreenKey is NestedNavKey.Download
    val inSettingsScreen = mainScreenKey is NestedNavKey.Settings

    val isBackgroundValid = LocalBackgroundViewModel.current?.isValid == true
    val launcherBackgroundOpacity = AllSettings.launcherBackgroundOpacity.state.toFloat() / 100f
    val surface = if (isBackgroundValid) {
        backgroundColor().copy(alpha = launcherBackgroundOpacity)
    } else {
        MaterialTheme.colorScheme.background
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = surface,
        contentColor = onBackgroundColor()
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = if (inLauncherScreen) androidText(BuildKeys.LAUNCHER_NAME) else (mainScreenKey as? TitledNavKey)?.title,
                subtitle = (mainScreenKey as? BackStackNavKey<*>)?.currentKey?.title,
                showBack = !inLauncherScreen,
                onBack = {
                    LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher?.onBackPressed()
                        ?: screenBackStackModel.mainScreen.backStack.removeFirstOrNull()
                },
                onHome = toMainScreen,
                onFileManager = {
                    eventViewModel.sendEvent(
                        EventViewModel.Event.OpenFileManager(
                            rootPath = PathManager.DIR_FILES_EXTERNAL.absolutePath
                        )
                    )
                },
                onTasks = {
                    AllSettings.launcherTaskMenuExpanded.save(!isTaskMenuExpanded)
                },
                taskRunning = taskRunning
            )

            Box(Modifier.fillMaxWidth().weight(1f)) {
                NavStateHost(rootKey = screenBackStackModel.mainScreen) {
                    NavigationUI(
                        modifier = Modifier.fillMaxSize(),
                        screenBackStackModel = screenBackStackModel,
                        toMainScreen = toMainScreen,
                        eventViewModel = eventViewModel,
                        modpackImportViewModel = modpackImportViewModel,
                        modifyVersionViewModel = modifyVersionViewModel,
                        submitError = submitError
                    )
                }

                if (isTaskMenuExpanded) {
                    TaskPanel(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Space.screenH),
                        onCollapse = {
                            AllSettings.launcherTaskMenuExpanded.save(false)
                        }
                    )
                }
            }

            BottomNav(
                destinations = listOf(
                    NavDestination(
                        title = "主页",
                        icon = R.drawable.ic_home_filled,
                        selected = inLauncherScreen,
                        onClick = toMainScreen
                    ),
                    NavDestination(
                        title = "版本",
                        icon = R.drawable.ic_dashboard_outlined,
                        selected = mainScreenKey is NormalNavKey.VersionsManager,
                        onClick = {
                            screenBackStackModel.mainScreen.removeAndNavigateTo(
                                removes = screenBackStackModel.clearBeforeNavKeys,
                                screenKey = NormalNavKey.VersionsManager
                            )
                        }
                    ),
                    NavDestination(
                        title = "下载",
                        icon = R.drawable.ic_download_2_outlined,
                        selected = inDownloadScreen,
                        onClick = { screenBackStackModel.navigateToDownload() }
                    ),
                    NavDestination(
                        title = "联机",
                        icon = R.drawable.ic_group_filled,
                        selected = inMultiplayerScreen,
                        onClick = {
                            screenBackStackModel.mainScreen.removeAndNavigateTo(
                                removes = screenBackStackModel.clearBeforeNavKeys,
                                screenKey = NormalNavKey.Multiplayer
                            )
                        }
                    ),
                    NavDestination(
                        title = "设置",
                        icon = R.drawable.ic_settings_filled,
                        selected = inSettingsScreen,
                        onClick = {
                            screenBackStackModel.mainScreen.removeAndNavigateTo(
                                removes = screenBackStackModel.clearBeforeNavKeys,
                                screenKey = screenBackStackModel.settingsScreen
                            )
                        }
                    )
                )
            )
        }
    }
}

@Composable
private fun TopBar(
    title: AndroidStringText?,
    subtitle: AndroidStringText?,
    showBack: Boolean,
    taskRunning: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onFileManager: () -> Unit,
    onTasks: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.generic_back),
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onHome) {
                Icon(
                    painter = painterResource(R.drawable.ic_home_filled),
                    contentDescription = stringResource(R.string.generic_main_menu),
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Spacer(Modifier.width(Space.xs))
        }

        Column(Modifier.weight(1f)) {
            if (title == null) {
                Text(
                    text = BuildKeys.LAUNCHER_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                AndroidStringText(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        AndroidStringText(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (!taskRunning) {
            IconButton(onClick = onTasks) {
                Icon(
                    painter = painterResource(R.drawable.ic_assignment_filled),
                    contentDescription = stringResource(R.string.main_task_menu),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.generic_more),
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                shape = RoundedCornerShape(com.cmc.launcher.ui.theme.Radius.medium)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.fm_activity_name)) },
                    onClick = {
                        menuExpanded = false
                        onFileManager()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.main_task_menu)) },
                    onClick = {
                        menuExpanded = false
                        onTasks()
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomNav(destinations: List<NavDestination>) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { destination ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(com.cmc.launcher.ui.theme.Radius.medium))
                        .clickable(onClick = destination.onClick)
                        .padding(vertical = Space.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.xxs, Alignment.CenterVertically)
                ) {
                    Icon(
                        painter = painterResource(destination.icon),
                        contentDescription = destination.title,
                        modifier = Modifier.size(21.dp),
                        tint = if (destination.selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = destination.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (destination.selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationUI(
    modifier: Modifier = Modifier,
    screenBackStackModel: ScreenBackStackViewModel,
    toMainScreen: () -> Unit,
    eventViewModel: EventViewModel,
    modpackImportViewModel: ModpackImportViewModel,
    modifyVersionViewModel: ModifyVersionViewModel,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    val backStack = screenBackStackModel.mainScreen.backStack
    val currentKey = backStack.lastOrNull()

    LaunchedEffect(currentKey) {
        screenBackStackModel.mainScreen.currentKey = currentKey
    }

    if (backStack.isNotEmpty()) {

        val navigateToVersions: (Version) -> Unit = { version ->
            screenBackStackModel.mainScreen.navigateTo(
                screenKey = NestedNavKey.VersionSettings(version),
                useClassEquality = true
            )
        }

        val navigateToExport: (Version) -> Unit = { version ->
            screenBackStackModel.mainScreen.removeAndNavigateTo(
                remove = NestedNavKey.VersionSettings::class,
                screenKey = NestedNavKey.VersionExport(version),
                useClassEquality = true
            )
        }

        NestedNavDisplay(
            ownerKey = screenBackStackModel.mainScreen,
            backStack = backStack,
            modifier = modifier,
            onBack = {
                onBack(backStack)
            },
            transitionSpec = rememberTransitionSpec(),
            popTransitionSpec = rememberTransitionSpec(),
            entryProvider = entryProvider {
                entry<NormalNavKey.LauncherMain> {
                    LauncherScreen(
                        backStackViewModel = screenBackStackModel,
                        navigateToVersions = navigateToVersions,
                        onLaunchGame = { version ->
                            eventViewModel.sendEvent(
                                EventViewModel.Event.Launch.Game(version)
                            )
                        },
                        onQuickPlayServer = { version, address ->
                            val target = (version ?: VersionsManager.currentVersion.value)?.takeIf { it.isValid() }
                            if (target != null) {
                                eventViewModel.sendEvent(
                                    EventViewModel.Event.Launch.PlayServer(target, address)
                                )
                            } else {
                                eventViewModel.sendToast(androidText(R.string.game_launch_no_version))
                                screenBackStackModel.mainScreen.navigateTo(NormalNavKey.VersionsManager)
                            }
                        },
                        onOpenLink = {
                            eventViewModel.sendEvent(EventViewModel.Event.OpenLink(it))
                        },
                    )
                }
                entry<NestedNavKey.Settings> { key ->
                    SettingsScreen(
                        key = key,
                        backStackViewModel = screenBackStackModel,
                        eventViewModel = eventViewModel,
                        submitError = submitError
                    )
                }
                entry<NormalNavKey.AccountManager> { key ->
                    AccountManageScreen(
                        key = key,
                        backStackViewModel = screenBackStackModel,
                        backToMainScreen = toMainScreen,
                        openLink = { url ->
                            eventViewModel.sendEvent(EventViewModel.Event.OpenLink(url))
                        },
                        eventViewModel = eventViewModel,
                        submitError = submitError
                    )
                }
                entry<NormalNavKey.WebScreen> { key ->
                    WebViewScreen(
                        key = key,
                        backStackViewModel = screenBackStackModel,
                        eventViewModel = eventViewModel
                    )
                }
                entry<NormalNavKey.VersionsManager> {
                    VersionsManageScreen(
                        backScreenViewModel = screenBackStackModel,
                        navigateToVersions = navigateToVersions,
                        navigateToExport = navigateToExport,
                        eventViewModel = eventViewModel,
                        submitError = submitError
                    )
                }
                entry<NormalNavKey.FileSelector> { key ->
                    FileSelectorScreen(
                        key = key,
                        backScreenViewModel = screenBackStackModel
                    ) {
                        backStack.removeLastOrNull()
                    }
                }
                entry<NestedNavKey.VersionSettings> { key ->
                    VersionSettingsScreen(
                        key = key,
                        modifyViewModel = modifyVersionViewModel,
                        backScreenViewModel = screenBackStackModel,
                        backToMainScreen = toMainScreen,
                        onExportModpack = {
                            navigateToExport(key.version)
                        },
                        eventViewModel = eventViewModel,
                        submitError = submitError
                    )
                }
                entry<NestedNavKey.VersionExport> { key ->
                    VersionExportScreen(
                        key = key,
                        backScreenViewModel = screenBackStackModel,
                        eventViewModel = eventViewModel,
                        backToMainScreen = toMainScreen
                    )
                }
                entry<NestedNavKey.Download> { key ->
                    DownloadScreen(
                        key = key,
                        backScreenViewModel = screenBackStackModel,
                        eventViewModel = eventViewModel,
                        modpackImportViewModel = modpackImportViewModel,
                        submitError = submitError
                    )
                }
                entry<NestedNavKey.AddonInfo> { key ->
                    AddonInfoScreen(
                        key = key,
                        mainScreenKey = screenBackStackModel.mainScreen.currentKey,
                        addonInfoScreenKey = key.currentKey,
                        eventViewModel = eventViewModel,
                        submitError = submitError,
                    )
                }
                entry<NormalNavKey.Multiplayer> {
                    MultiplayerScreen(
                        backScreenViewModel = screenBackStackModel,
                        eventViewModel = eventViewModel
                    )
                }
                entry<NormalNavKey.LogView> { key ->
                    LogViewScreen(
                        key = key,
                        backStackViewModel = screenBackStackModel,
                    )
                }
            }
        )
    } else {
        Box(modifier)
    }
}
@Composable
private fun TaskPanel(
    modifier: Modifier = Modifier,
    onCollapse: () -> Unit
) {
    val tasks by TaskSystem.tasksFlow.collectAsStateWithLifecycle()
    BackgroundCard(
        modifier = modifier,
        influencedByBackground = false,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = cardColor(false),
            contentColor = onCardColor()
        )
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.main_task_menu),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "收起",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onCollapse)
                )
            }
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alpha.soft)
            )
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                items(tasks.size) { index ->
                    val task = tasks[index]
                    val progress by task.progress.collectAsStateWithLifecycle()
                    val message by task.message.collectAsStateWithLifecycle()
                    val rate by task.rateBytesPerSec.collectAsStateWithLifecycle()
                    TaskRow(
                        progress = progress,
                        message = message,
                        rate = rate,
                        onCancel = { TaskSystem.cancelTask(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    progress: Float,
    message: AndroidStringText?,
    rate: Long?,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            message?.let {
                AndroidStringText(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.generic_cancel),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(Space.xs))
        if (progress < 0f) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.xxs)
        ) {
            if (progress >= 0f) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            rate?.let {
                Text(
                    text = "${formatFileSize(it)}/s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
