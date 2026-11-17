package com.cmc.launcher.ui.screens.content.download.addon.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cmc.launcher.R
import com.cmc.launcher.game.download.addon.favorites.FavoriteProjectsRepository
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.download.addon.platform.PlatformProject
import com.cmc.launcher.game.download.addon.platform.PlatformVersion
import com.cmc.launcher.game.download.addon.platform.cacheKey
import com.cmc.launcher.game.download.addon.platform.getProject
import com.cmc.launcher.game.download.addon.platform.getVersionById
import com.cmc.launcher.game.download.addon.platform.getVersions
import com.cmc.launcher.game.download.addon.platform.isAllNull
import com.cmc.launcher.game.download.addon.utils.getMcmodTitle
import com.cmc.launcher.game.download.addon.utils.getTranslations
import com.cmc.launcher.game.version.mod.InstalledMod
import com.cmc.launcher.game.versioninfo.filterRelease
import com.cmc.launcher.ui.AndroidStringText
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.ui.base.BaseScreen
import com.cmc.launcher.ui.components.AnimatedDirection
import com.cmc.launcher.ui.components.BackgroundCard
import com.cmc.launcher.ui.components.CheckChip
import com.cmc.launcher.ui.components.ScalingLabel
import com.cmc.launcher.ui.components.ShimmerBox
import com.cmc.launcher.ui.components.SimpleTextInputField
import com.cmc.launcher.ui.components.animated
import com.cmc.launcher.ui.components.rememberAnimatedContext
import com.cmc.launcher.ui.screens.NestedNavKey
import com.cmc.launcher.ui.screens.NormalNavKey
import com.cmc.launcher.ui.screens.TitledNavKey
import com.cmc.launcher.ui.screens.content.download.addon.elements.AddonIcon
import com.cmc.launcher.ui.screens.content.download.addon.elements.AddonVersionItemLayout
import com.cmc.launcher.ui.screens.content.download.addon.elements.ClassesIdentifier
import com.cmc.launcher.ui.screens.content.download.addon.elements.DependencyEntry
import com.cmc.launcher.ui.screens.content.download.addon.elements.DownloadAddonState
import com.cmc.launcher.ui.screens.content.download.addon.elements.DownloadAddonVersionLoading
import com.cmc.launcher.ui.screens.content.download.addon.elements.FavoriteIdentifier
import com.cmc.launcher.ui.screens.content.download.addon.elements.ProjectUrlsContent
import com.cmc.launcher.ui.screens.content.download.addon.elements.ScreenshotItemLayout
import com.cmc.launcher.ui.screens.content.download.addon.elements.VersionInfoMap
import com.cmc.launcher.ui.screens.content.download.addon.elements.initAll
import com.cmc.launcher.ui.screens.content.download.addon.elements.mapWithVersions
import com.cmc.launcher.ui.theme.onCardColor
import com.cmc.launcher.viewmodel.EventViewModel
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jackhuang.hmcl.addon.AddonProjectInfo
import kotlin.time.Duration.Companion.milliseconds

private class DownloadScreenViewModel(
    private val platform: Platform,
    private val projectId: String,
    initialClasses: PlatformClasses
): ViewModel() {

    var classes by mutableStateOf(initialClasses)
        private set

    private var _versionsList by mutableStateOf<List<VersionInfoMap>>(emptyList())

    private var rawVersions: List<PlatformVersion> = emptyList()
    var versionsResult by mutableStateOf<DownloadAddonState<List<VersionInfoMap>>>(DownloadAddonState.Getting())
    var versionsLoading by mutableStateOf<DownloadAddonVersionLoading>(DownloadAddonVersionLoading.None)
        private set

    var showOnlyMCRelease by mutableStateOf(true)
    var searchMCVersion by mutableStateOf("")

    fun filterWith(
        showOnlyMCRelease: Boolean = this.showOnlyMCRelease,
        searchMCVersion: String = this.searchMCVersion
    ) {
        this.showOnlyMCRelease = showOnlyMCRelease
        this.searchMCVersion = searchMCVersion
        viewModelScope.launch {
            versionsLoading = DownloadAddonVersionLoading.None
            val infos = _versionsList.filterInfos()
            versionsResult = DownloadAddonState.Success(infos)
        }
    }

    private fun List<VersionInfoMap>.filterInfos(): List<VersionInfoMap> {
        return filter { info ->
            (!showOnlyMCRelease || filterRelease(info.gameVersion)) &&
                    (searchMCVersion.isEmpty() || info.gameVersion.contains(searchMCVersion, true))
        }
    }

    private fun remapVersions() {
        if (rawVersions.isEmpty()) return
        _versionsList = rawVersions.mapWithVersions(classes)
        versionsResult = DownloadAddonState.Success(_versionsList.filterInfos())
    }

    fun getVersions() {
        viewModelScope.launch {
            versionsResult = DownloadAddonState.Getting()

            failedDependencyProjects.clear()
            if (platform == Platform.CURSEFORGE) {
                versionsLoading = DownloadAddonVersionLoading.StartLoadPage
            }

            getVersions(
                projectID = projectId,
                platform = platform,
                pageCallback = { chunk, page ->
                    versionsLoading = DownloadAddonVersionLoading.LoadingPage(chunk, page)
                },
                onSuccess = { result ->
                    val versions: List<PlatformVersion> = result.initAll(projectId)
                    rawVersions = versions

                    _versionsList = versions.mapWithVersions(classes)
                    versionsResult = DownloadAddonState.Success(_versionsList.filterInfos())
                    versionsLoading = DownloadAddonVersionLoading.None

                    if (classes == PlatformClasses.MOD_PACK) return@getVersions
                    val dependencies = versions
                        .flatMap { it.platformDependencies() }
                        .distinctBy { it.cacheKey() }
                    if (dependencies.isEmpty()) return@getVersions

                    viewModelScope.launch {
                        val semaphore = Semaphore(8)
                        dependencies.map { dependency ->
                            async {
                                semaphore.withPermit {
                                    cacheDependencyProject(
                                        platform = dependency.platform,
                                        dependency = dependency
                                    )
                                }
                            }
                        }.awaitAll()
                    }
                },
                onError = {
                    versionsResult = it
                    versionsLoading = DownloadAddonVersionLoading.None
                }
            )
        }
    }

    var projectResult by mutableStateOf<DownloadAddonState<Pair<PlatformProject, AddonProjectInfo?>>>(DownloadAddonState.Getting())

    fun getProject() {
        viewModelScope.launch {
            projectResult = DownloadAddonState.Getting()
            getProject(
                projectID = projectId,
                platform = platform,
                onSuccess = { result ->

                    val accurateClasses = result.platformClasses(classes)
                    if (accurateClasses != classes) {
                        classes = accurateClasses
                        remapVersions()
                    }
                    val mcmod = classes.getTranslations()?.getInfoByCurseForgeId(result.platformSlug())
                    projectResult = DownloadAddonState.Success(Pair(result, mcmod))
                },
                onError = { state, _ ->
                    projectResult = state
                }
            )
        }
    }

    val cachedDependencyProject = mutableStateMapOf<String, PlatformProject>()

    val notFoundDependencyProjects = mutableStateListOf<String>()

    val failedDependencyProjects = mutableStateListOf<String>()

    private suspend fun cacheDependencyProject(
        platform: Platform,
        dependency: PlatformVersion.PlatformDependency
    ) {
        val key = dependency.cacheKey()
        if (notFoundDependencyProjects.contains(key) || cachedDependencyProject.containsKey(key)) return

        try {
            val projectId = dependency.projectId ?: run {

                getVersionById(
                    versionId = dependency.versionId
                        ?: error("The dependency does not provide a project id or a version id."),
                    platform = platform,
                    printLog = false
                ).platformProjectId()
            }
            getProject<PlatformProject>(
                projectID = projectId,
                platform = platform,
                onSuccess = { result ->
                    cachedDependencyProject[key] = result
                },
                onError = { _, e ->
                    if (e.isNotFound()) {
                        notFoundDependencyProjects.add(key)
                    } else {
                        if (!failedDependencyProjects.contains(key)) failedDependencyProjects.add(key)
                    }
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e.isNotFound()) {
                notFoundDependencyProjects.add(key)
            } else {
                if (!failedDependencyProjects.contains(key)) failedDependencyProjects.add(key)
            }
        }
    }

    init {

        getVersions()
        getProject()
    }

    override fun onCleared() {
        viewModelScope.cancel()
    }
}

private fun Throwable.isNotFound(): Boolean =
    this is ClientRequestException && response.status.value == 404

@Composable
private fun rememberDownloadAddonViewModel(
    key: NormalNavKey.DownloadAddon
): DownloadScreenViewModel {
    return viewModel(
        key = key.toString()
    ) {
        DownloadScreenViewModel(
            platform = key.platform,
            projectId = key.projectId,
            initialClasses = key.classes
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadAddonScreen(
    mainScreenKey: TitledNavKey?,
    parentScreenKey: TitledNavKey,
    parentCurrentKey: TitledNavKey?,
    currentKey: TitledNavKey?,
    key: NormalNavKey.DownloadAddon,
    eventViewModel: EventViewModel,
    onItemClicked: (PlatformClasses, PlatformVersion, iconUrl: String?, deps: List<DependencyEntry>) -> Unit,
    nestedNavKeyClass: Class<out TitledNavKey>? = null,
    versionsUIWeight: Float = 6.5f,
    projectUIWeight: Float = 3.5f,
    installedChecker: ((PlatformVersion) -> InstalledMod?)? = null,
) {
    val viewModel: DownloadScreenViewModel = rememberDownloadAddonViewModel(key)

    BaseScreen(
        levels1 = listOf(
            Pair(nestedNavKeyClass ?: NestedNavKey.Download::class.java, mainScreenKey)
        ),
        Triple(parentScreenKey, parentCurrentKey, false),
        Triple(key, currentKey, false),
    ) { isVisible ->
        var showProjectSheet by remember { mutableStateOf(false) }
        val projectSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        Column(modifier = Modifier.fillMaxSize()) {
            val headerCtx = rememberAnimatedContext(isVisible = isVisible)
            ProjectHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .animated(headerCtx),
                projectResult = viewModel.projectResult,
                classes = viewModel.classes,
                platform = key.platform,
                projectId = key.projectId,
                onOpenDetail = { showProjectSheet = true },
                onReload = { viewModel.getProject() }
            )

            val versionCtx = rememberAnimatedContext(isVisible = isVisible)
            Versions(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .animated(versionCtx),
                viewModel = viewModel,
                installedChecker = installedChecker,
                onReload = { viewModel.getVersions() },
                onItemClicked = { version ->
                    val deps = version.platformDependencies().mapNotNull { dep ->
                        val key = dep.cacheKey()
                        val project = viewModel.cachedDependencyProject[key]
                        when {
                            project != null -> DependencyEntry(dep, project)
                            viewModel.notFoundDependencyProjects.contains(key) ->
                                DependencyEntry(dep, null, notFound = true)
                            viewModel.failedDependencyProjects.contains(key) ->
                                DependencyEntry(dep, null)

                            else -> null
                        }
                    }
                    onItemClicked(viewModel.classes, version, key.iconUrl, deps)
                },
            )
        }

        if (showProjectSheet) {
            ModalBottomSheet(
                onDismissRequest = { showProjectSheet = false },
                sheetState = projectSheetState,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                ProjectInfo(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f),
                    projectResult = viewModel.projectResult,
                    platform = key.platform,
                    projectId = key.projectId,
                    classes = viewModel.classes,
                    onReload = { viewModel.getProject() },
                    openLink = { url ->
                        eventViewModel.sendEvent(EventViewModel.Event.OpenLink(url))
                    }
                )
            }
        }
    }
}

@Composable
private fun ProjectHeader(
    modifier: Modifier = Modifier,
    projectResult: DownloadAddonState<Pair<PlatformProject, AddonProjectInfo?>>,
    platform: Platform,
    projectId: String,
    classes: PlatformClasses,
    onOpenDetail: () -> Unit,
    onReload: () -> Unit = {}
) {
    val context = LocalContext.current

    BackgroundCard(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        when (projectResult) {
            is DownloadAddonState.Getting -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .clip(shape = RoundedCornerShape(10.dp))
                            .size(56.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
            is DownloadAddonState.Success -> {
                val (project, mcmod) = projectResult.result
                val iconUrl = remember { project.platformIconUrl() }
                val title = remember { project.platformTitle() }
                val summary = remember { project.platformSummary() }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AddonIcon(
                        modifier = Modifier.clip(shape = RoundedCornerShape(12.dp)),
                        size = 56.dp,
                        iconUrl = iconUrl
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            text = mcmod.getMcmodTitle(title, context),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ClassesIdentifier(
                                classes = classes,
                                iconSize = 14.dp,
                                textStyle = MaterialTheme.typography.labelSmall
                            )
                            val isFavorite = FavoriteProjectsRepository.isFavorite(platform, projectId)
                            FavoriteIdentifier(
                                isFavorite = isFavorite,
                                iconSize = 14.dp,
                                textStyle = MaterialTheme.typography.labelSmall,
                                onClick = {
                                    if (isFavorite) {
                                        FavoriteProjectsRepository.unfavorite(platform, projectId)
                                    } else {
                                        FavoriteProjectsRepository.favorite(project, classes)
                                    }
                                }
                            )
                        }
                    }

                    IconButton(onClick = onOpenDetail) {
                        Icon(
                            modifier = Modifier.size(22.dp),
                            painter = painterResource(R.drawable.ic_arrow_right_rounded),
                            contentDescription = "查看详情"
                        )
                    }
                }
            }
            is DownloadAddonState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 16.dp)
                ) {
                    ScalingLabel(
                        modifier = Modifier.align(Alignment.Center),
                        text = {
                            AndroidStringText(
                                text = androidText(
                                    R.string.download_addon_failed_to_get_project,
                                    projectResult.message
                                )
                            )
                        },
                        onClick = onReload
                    )
                }
            }
        }
    }
}

@Composable
private fun Versions(
    modifier: Modifier = Modifier,
    viewModel: DownloadScreenViewModel,
    installedChecker: ((PlatformVersion) -> InstalledMod?)? = null,
    onReload: () -> Unit = {},
    onItemClicked: (PlatformVersion) -> Unit = {}
) {
    when (val versions = viewModel.versionsResult) {
        is DownloadAddonState.Getting -> {
            Box(
                modifier.padding(all = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                    ) {
                        when (val state = viewModel.versionsLoading) {
                            is DownloadAddonVersionLoading.None -> {}
                            is DownloadAddonVersionLoading.StartLoadPage -> {
                                Text(
                                    text = stringResource(R.string.download_addon_loading_page_data),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                            is DownloadAddonVersionLoading.LoadingPage -> {
                                Text(
                                    text = stringResource(R.string.download_addon_loaded_chunk_page, state.chunk, state.page),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    LinearWavyProgressIndicator(
                        modifier = Modifier.width(168.dp),
                        wavelength = 32.dp
                    )
                }
            }
        }
        is DownloadAddonState.Success -> {
            Column(modifier = modifier) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CheckChip(
                        selected = viewModel.showOnlyMCRelease,
                        onClick = {
                            viewModel.filterWith(showOnlyMCRelease = viewModel.showOnlyMCRelease.not())
                        },
                        label = {
                            Text(text = stringResource(R.string.download_addon_show_only_mc_release))
                        },
                    )

                    SimpleTextInputField(
                        modifier = Modifier.weight(1f),
                        value = viewModel.searchMCVersion,
                        onValueChange = { viewModel.filterWith(searchMCVersion = it) },
                        singleLine = true,
                        textStyle = TextStyle(color = onCardColor()).copy(fontSize = 12.sp),
                        hint = {
                            Text(
                                text = stringResource(R.string.download_addon_search_mc_versions),
                                style = TextStyle(color = onCardColor()).copy(fontSize = 12.sp)
                            )
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )

                val scrollState = rememberLazyListState()

                LaunchedEffect(Unit) {
                    delay(100L.milliseconds)
                    runCatching {
                        val result = versions.result
                        val index = versions.result.indexOfFirst { it.isAdapt }
                        if (index >= 0 && index < result.size) {

                            scrollState.animateScrollToItem(index)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    state = scrollState
                ) {
                    items(
                        items = versions.result,
                        key = { "${it.gameVersion}_${it.loader?.getDisplayName()}" }
                    ) { info ->
                        AddonVersionItemLayout(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            infoMap = info,
                            installedChecker = installedChecker,
                            onItemClicked = onItemClicked
                        )
                    }
                }
            }
        }
        is DownloadAddonState.Error -> {
            Box(modifier.padding(all = 12.dp)) {
                ScalingLabel(
                    modifier = Modifier.align(Alignment.Center),
                    text = {
                        AndroidStringText(
                            text = androidText(
                                R.string.download_addon_failed_to_get_versions,
                                versions.message
                            )
                        )
                    },
                    onClick = onReload
                )
            }
        }
    }
}

@Composable
private fun ProjectInfo(
    modifier: Modifier = Modifier,
    projectResult: DownloadAddonState<Pair<PlatformProject, AddonProjectInfo?>>,
    platform: Platform,
    projectId: String,
    classes: PlatformClasses,
    onReload: () -> Unit = {},
    openLink: (url: String) -> Unit = {}
) {
    val context = LocalContext.current
    BackgroundCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(modifier = Modifier) {
            when (projectResult) {
                is DownloadAddonState.Getting -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(all = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ShimmerBox(
                                    modifier = Modifier
                                        .clip(shape = RoundedCornerShape(10.dp))
                                        .size(72.dp)
                                )
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {

                                    ShimmerBox(
                                        modifier = Modifier
                                            .fillMaxWidth(0.6f)
                                            .height(20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )

                                    ShimmerBox(
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .height(16.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                    }
                }
                is DownloadAddonState.Success -> {
                    val (project, mcmod) = projectResult.result

                    val platform = remember { project.platform() }
                    val iconUrl = remember { project.platformIconUrl() }
                    val title = remember { project.platformTitle() }
                    val summary = remember { project.platformSummary() }
                    val urls = remember(classes) { project.platformUrls(classes) }
                    val screenshots = remember { project.platformScreenshots() }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(all = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AddonIcon(
                                    modifier = Modifier.clip(shape = RoundedCornerShape(10.dp)),
                                    size = 72.dp,
                                    iconUrl = iconUrl
                                )

                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = mcmod.getMcmodTitle(title, context),
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        if (!urls.isAllNull()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.download_addon_links),
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    ProjectUrlsContent(
                                        platform = platform,
                                        urls = urls,
                                        mcmod = mcmod,
                                        openLink = openLink,
                                    )
                                }
                            }
                        }

                        items(screenshots) { screenshot ->
                            ScreenshotItemLayout(
                                modifier = Modifier.fillMaxWidth(),
                                screenshot = screenshot
                            )
                        }
                    }
                }
                is DownloadAddonState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(all = 12.dp)
                    ) {
                        ScalingLabel(
                            modifier = Modifier.align(Alignment.Center),
                            text = {
                                AndroidStringText(
                                    text = androidText(
                                        R.string.download_addon_failed_to_get_project,
                                        projectResult.message
                                    )
                                )
                            },
                            onClick = onReload
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.padding(all = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClassesIdentifier(
                    classes = classes,
                    iconSize = 16.dp,
                    textStyle = MaterialTheme.typography.labelMedium
                )

                val isFavorite = FavoriteProjectsRepository.isFavorite(platform, projectId)
                FavoriteIdentifier(
                    isFavorite = isFavorite,
                    iconSize = 16.dp,
                    textStyle = MaterialTheme.typography.labelMedium,
                    onClick = {
                        if (isFavorite) {
                            FavoriteProjectsRepository.unfavorite(platform, projectId)
                        } else {

                            (projectResult as? DownloadAddonState.Success)
                                ?.result?.first?.let { project ->
                                    FavoriteProjectsRepository.favorite(project, classes)
                                }
                        }
                    }
                )
            }
        }
    }
}
