package com.cmc.launcher.ui.screens.content.download.addon.favorites

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cmc.launcher.R
import com.cmc.launcher.game.download.addon.favorites.FavoriteEntry
import com.cmc.launcher.game.download.addon.favorites.FavoriteProjectsRepository
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.download.addon.platform.PlatformDisplayLabel
import com.cmc.launcher.game.download.addon.platform.curseforge.models.curseForgeModLoaderFilters
import com.cmc.launcher.game.download.addon.platform.modrinth.models.modrinthModLoaderFilters
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.ui.base.BaseScreen
import com.cmc.launcher.ui.components.AnimatedDirection
import com.cmc.launcher.ui.components.EdgeDirection
import com.cmc.launcher.ui.components.OwnOutlinedTextField
import com.cmc.launcher.ui.components.TextRailItem
import com.cmc.launcher.ui.components.animated
import com.cmc.launcher.ui.components.fadeEdge
import com.cmc.launcher.ui.components.rememberAnimatedContext
import com.cmc.launcher.ui.components.topBarWheelFallback
import com.cmc.launcher.ui.screens.NestedNavKey
import com.cmc.launcher.ui.screens.TitledNavKey
import com.cmc.launcher.ui.screens.content.download.addon.elements.FilterListLayout
import com.cmc.launcher.ui.screens.content.download.addon.elements.FilterSelectionMode
import com.cmc.launcher.ui.screens.content.elements.backgroundGlass
import com.cmc.launcher.ui.theme.cardColor
import com.cmc.launcher.ui.theme.onCardColor
import com.cmc.launcher.viewmodel.backgroundVisible
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class FavoriteSortBy {

    FOLLOW_TIME,

    TITLE
}

private val favoriteModLoaderFilters: List<PlatformDisplayLabel> =
    (modrinthModLoaderFilters + curseForgeModLoaderFilters).distinctBy { it.getDisplayName() }

private class FavoritesScreenViewModel : ViewModel() {

    var category by mutableStateOf<PlatformClasses?>(null)
        private set
    var searchName by mutableStateOf("")
    var platformFilter by mutableStateOf<Platform?>(null)
    var sortBy by mutableStateOf(FavoriteSortBy.FOLLOW_TIME)
    var modloaderFilter by mutableStateOf<List<PlatformDisplayLabel>>(emptyList())

    val items: List<FavoriteEntry> by derivedStateOf {
        val keyword = searchName.trim()
        val modloaderNames = modloaderFilter.map { it.getDisplayName() }.toSet()

        FavoriteProjectsRepository.projects.values
            .asSequence()
            .filter { entry -> category == null || entry.project.classes == category }
            .filter { entry ->
                keyword.isEmpty() || entry.displayTitle().contains(keyword, ignoreCase = true)
            }
            .filter { entry -> platformFilter == null || entry.platform == platformFilter }
            .filter { entry ->

                modloaderNames.isEmpty() ||
                        entry.remote?.platformModLoaders()
                            ?.any { it.getDisplayName() in modloaderNames } == true
            }
            .sortedWith(
                when (sortBy) {
                    FavoriteSortBy.FOLLOW_TIME -> compareByDescending { it.project.followTime }
                    FavoriteSortBy.TITLE -> compareBy { it.displayTitle().lowercase() }
                }
            )
            .toList()
    }

    fun onCategoryChange(value: PlatformClasses?) {
        category = value

        if (value != PlatformClasses.MOD && value != PlatformClasses.MOD_PACK) {
            modloaderFilter = emptyList()
        }
    }

    fun reload() {
        viewModelScope.launch {

            FavoriteProjectsRepository.reload()
            FavoriteProjectsRepository.refreshRemote()
        }
    }
}

private fun FavoriteEntry.displayTitle(): String {
    return remote?.platformTitle() ?: project.title
}

@Composable
private fun rememberFavoritesScreenViewModel(): FavoritesScreenViewModel = viewModel {
    FavoritesScreenViewModel()
}

@Composable
fun FavoritesScreen(
    mainScreenKey: TitledNavKey?,
    parentScreenKey: TitledNavKey,
    parentCurrentKey: TitledNavKey?,
    screenKey: TitledNavKey,
    currentKey: TitledNavKey?,
    swapToDownload: (Platform, PlatformClasses, projectId: String, iconUrl: String?) -> Unit
) {
    val viewModel = rememberFavoritesScreenViewModel()

    BaseScreen(
        levels1 = listOf(
            Pair(NestedNavKey.Download::class.java, mainScreenKey)
        ),
        Triple(parentScreenKey, parentCurrentKey, false),
        Triple(screenKey, currentKey, false)
    ) { isVisible ->

        LaunchedEffect(Unit) {
            viewModel.reload()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            val ctxX = rememberAnimatedContext(isVisible = isVisible)
            FavoritesFilter(
                modifier = Modifier
                    .fillMaxWidth()
                    .animated(ctxX, from = AnimatedDirection.Top),
                viewModel = viewModel
            )

            val ctx = rememberAnimatedContext(isVisible = isVisible)
            FavoritesContent(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .animated(ctx),
                viewModel = viewModel,
                swapToDownload = swapToDownload
            )
        }
    }
}

@Composable
private fun FavoritesContent(
    modifier: Modifier = Modifier,
    viewModel: FavoritesScreenViewModel,
    swapToDownload: (Platform, PlatformClasses, projectId: String, iconUrl: String?) -> Unit
) {
    val repositoryLoaded = FavoriteProjectsRepository.initialized
    val repositoryEmpty = FavoriteProjectsRepository.projects.isEmpty()

    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = false,
        onRefresh = viewModel::reload,
    ) {
        if (!repositoryLoaded) {

        } else if (repositoryEmpty) {

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    modifier = Modifier.size(68.dp),
                    painter = painterResource(R.drawable.ic_box),
                    contentDescription = null
                )
                Text(
                    text = stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else {

            val density = LocalDensity.current
            val topAppBarState = rememberTopAppBarState()
            val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)
            val headerTopPaddingPx = with(density) { 12.dp.toPx() }
            var headerHeightPx by remember { mutableIntStateOf(0) }

            val listState = rememberLazyListState()
            val listScrolledFraction = remember(listState, headerTopPaddingPx) {
                derivedStateOf {
                    if (listState.firstVisibleItemIndex > 0) 1f
                    else {
                        val rampPx = headerTopPaddingPx + (listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0)
                        (listState.firstVisibleItemScrollOffset / rampPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    }
                }
            }
            val barShownFraction = remember(topAppBarState) {
                derivedStateOf { 1f - topAppBarState.collapsedFraction }
            }

            val withShadow = !backgroundVisible()

            val barShape = MaterialTheme.shapes.large
            val barShadowModifier = Modifier.graphicsLayer {
                if (withShadow) {
                    shadowElevation = 5.dp.toPx() * listScrolledFraction.value * barShownFraction.value
                }
                shape = barShape
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .topBarWheelFallback(topAppBarState)
                    .clipToBounds()
                    .topFade {
                        (headerHeightPx + headerTopPaddingPx + 80f) *
                                listScrolledFraction.value *
                                barShownFraction.value
                    },
                state = listState,
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = 12.dp,
                    top = with(density) {
                        (headerHeightPx + topAppBarState.heightOffset).coerceAtLeast(0f).toDp()
                    }
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val entries = viewModel.items
                if (entries.isEmpty()) {

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.favorites_empty_filtered),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                items(entries, key = { "${it.platform}:${it.project.projectId}" }) { entry ->
                    FavoriteProjectLayout(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        entry = entry,
                        onClick = {
                            swapToDownload(
                                entry.platform,
                                entry.project.classes,
                                entry.project.projectId,
                                entry.remote?.platformIconUrl() ?: entry.project.iconUrl
                            )
                        },
                        onUnfavorite = {
                            FavoriteProjectsRepository.unfavorite(entry.platform, entry.project.projectId)
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .onSizeChanged {
                        headerHeightPx = it.height
                        topAppBarState.heightOffsetLimit = -it.height.toFloat()
                    }
                    .offset { IntOffset(x = 0, y = topAppBarState.heightOffset.roundToInt()) }
            ) {
                val blur = AllSettings.backgroundBlur.state
                val barColor = cardColor()
                Surface(
                    modifier = Modifier
                        .padding(all = 12.dp)
                        .then(barShadowModifier),
                    color = barColor,
                    contentColor = onCardColor(),
                    shape = MaterialTheme.shapes.large
                ) {
                    val scrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .backgroundGlass(blur, barColor)
                            .fadeEdge(
                                state = scrollState,
                                length = 32.dp,
                                direction = EdgeDirection.Horizontal
                            )
                            .horizontalScroll(state = scrollState)
                            .padding(all = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FavoriteCategoryItem(
                            text = stringResource(R.string.generic_all),
                            selected = viewModel.category == null,
                            onClick = { viewModel.onCategoryChange(null) }
                        )
                        PlatformClasses.entries.forEach { classes ->
                            FavoriteCategoryItem(
                                text = stringResource(classes.categoryText()),
                                selected = viewModel.category == classes,
                                onClick = { viewModel.onCategoryChange(classes) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun PlatformClasses.categoryText(): Int = when (this) {
    PlatformClasses.MOD -> R.string.download_category_mod
    PlatformClasses.MOD_PACK -> R.string.download_category_modpack
    PlatformClasses.RESOURCE_PACK -> R.string.download_category_resource_pack
    PlatformClasses.SAVES -> R.string.download_category_saves
    PlatformClasses.SHADERS -> R.string.download_category_shaders
}

@Composable
private fun FavoriteCategoryItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextRailItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium
            )
        },
        onClick = onClick,
        selected = selected,
        shape = MaterialTheme.shapes.large
    )
}

@Composable
private fun FavoritesFilter(
    modifier: Modifier = Modifier,
    viewModel: FavoritesScreenViewModel
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {
            OwnOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = viewModel.searchName,
                onValueChange = { viewModel.searchName = it },
                singleLine = true,
                label = {
                    Text(text = stringResource(R.string.download_addon_filter_search_name))
                },
                trailingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = stringResource(R.string.generic_search)
                    )
                },
                shape = MaterialTheme.shapes.large
            )
        }

        item {
            FilterListLayout(
                modifier = Modifier.fillMaxWidth(),
                items = listOf<Platform?>(null) + Platform.entries,
                selectionMode = FilterSelectionMode.Single,
                selectedItems = listOf(viewModel.platformFilter),
                onSelectionChange = { new ->
                    val value = new.firstOrNull()
                    if (value != viewModel.platformFilter) {
                        viewModel.platformFilter = value
                    }
                },
                getItemLabel = { item ->
                    item?.displayName ?: stringResource(R.string.generic_all)
                },
                title = stringResource(R.string.download_addon_filter_search_platform),
                cancelable = false
            )
        }

        item {
            FilterListLayout(
                modifier = Modifier.fillMaxWidth(),
                items = FavoriteSortBy.entries,
                selectionMode = FilterSelectionMode.Single,
                selectedItems = listOf(viewModel.sortBy),
                onSelectionChange = { new ->
                    new.firstOrNull()?.takeIf { it != viewModel.sortBy }?.let { value ->
                        viewModel.sortBy = value
                    }
                },
                getItemLabel = { item ->
                    stringResource(
                        when (item) {
                            FavoriteSortBy.FOLLOW_TIME -> R.string.favorites_sort_follow_time
                            FavoriteSortBy.TITLE -> R.string.favorites_sort_title
                        }
                    )
                },
                title = stringResource(R.string.sort_by),
                cancelable = false
            )
        }

        val enableModLoader = viewModel.category == PlatformClasses.MOD ||
                viewModel.category == PlatformClasses.MOD_PACK
        if (enableModLoader) {
            item {
                FilterListLayout(
                    modifier = Modifier.fillMaxWidth(),
                    items = favoriteModLoaderFilters,
                    selectionMode = FilterSelectionMode.Multiple,
                    selectedItems = viewModel.modloaderFilter,
                    onSelectionChange = { new ->
                        viewModel.modloaderFilter = new
                    },
                    getItemLabel = { item ->
                        item.getDisplayName()
                    },
                    title = stringResource(R.string.download_addon_filter_modloader)
                )
            }
        }
    }
}

private fun Modifier.topFade(heightPx: () -> Float): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val height = heightPx()
        if (height <= 0f) return@drawWithContent
        inset(
            left = 0f,
            top = 0f,
            right = 0f,
            bottom = (size.height - height).coerceAtLeast(0f)
        ) {
            drawRect(
                brush = TopFadeBrush,
                blendMode = BlendMode.DstOut
            )
        }
    }

private val TopFadeBrush = Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent)
