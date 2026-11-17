package com.cmc.launcher.ui.screens.content.download.addon.elements

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.cmc.launcher.R
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.download.addon.platform.PlatformDisplayLabel
import com.cmc.launcher.game.download.addon.platform.PlatformProject
import com.cmc.launcher.game.download.addon.platform.PlatformVersion
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.installed.VersionsManager
import com.cmc.launcher.game.version.mod.InstalledMod
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.ui.AndroidStringText
import com.cmc.launcher.ui.components.IconTextButton
import com.cmc.launcher.ui.components.LittleTextLabel
import com.cmc.launcher.ui.components.ShimmerBox
import com.cmc.launcher.ui.components.rememberMaxHeight
import com.cmc.launcher.ui.screens.content.elements.backgroundGlass
import com.cmc.launcher.ui.theme.cardColor
import com.cmc.launcher.ui.theme.onCardColor
import com.cmc.launcher.utils.animation.getAnimateTween
import com.cmc.launcher.utils.formatNumberByLocale
import com.cmc.launcher.utils.getTimeAgo
import com.cmc.launcher.utils.isChinese
import com.cmc.launcher.utils.string.isNotEmptyOrBlank
import org.jackhuang.hmcl.addon.AddonProjectInfo
import org.jackhuang.hmcl.util.versioning.GameVersionNumber

sealed interface DownloadAddonState<T> {
    class Getting<T> : DownloadAddonState<T>
    data class Success<T>(val result: T) : DownloadAddonState<T>
    data class Error<T>(val message: AndroidStringText) : DownloadAddonState<T>
}

sealed interface DownloadAddonVersionLoading {
    data object None: DownloadAddonVersionLoading

    data object StartLoadPage: DownloadAddonVersionLoading

    data class LoadingPage(val chunk: Int, val page: Int): DownloadAddonVersionLoading
}

class VersionInfoMap(
    val gameVersion: String,
    val loader: PlatformDisplayLabel?,
    val versions: List<PlatformVersion>,
    val isAdapt: Boolean
)

suspend fun <E: PlatformVersion> List<E>.initAllGeneric(
    currentProjectId: String,
    also: suspend (E) -> Unit = {}
): List<E> {
    return mapNotNull { version ->
        if (!version.initFile(currentProjectId)) return@mapNotNull null
        version.also {
            also(it)
        }
    }.sortedByDescending {

        it.platformDatePublished()
    }
}

suspend fun List<PlatformVersion>.initAll(
    currentProjectId: String,
    also: suspend (PlatformVersion) -> Unit = {}
): List<PlatformVersion> {
    return initAllGeneric<PlatformVersion>(currentProjectId, also)
}

fun List<PlatformVersion>.mapWithVersions(classes: PlatformClasses): List<VersionInfoMap> {
    val grouped = mutableMapOf<Pair<String, PlatformDisplayLabel?>, MutableList<PlatformVersion>>()

    forEach { version ->
        val labels = version.platformLoaders().ifEmpty { listOf(null) }
        version.platformGameVersion().forEach { gameVer ->
            labels.forEach { loaderLabel ->
                grouped.getOrPut(gameVer to loaderLabel) { mutableListOf() } += version
            }
        }
    }

    val currentVersion = VersionsManager.currentVersion.value

    return grouped.map { (key, versions) ->
        VersionInfoMap(
            gameVersion = key.first,
            loader = key.second,
            versions = versions,
            isAdapt = when (classes) {
                PlatformClasses.MOD_PACK -> false
                else -> isVersionAdapt(currentVersion, key.first, key.second)
            }
        )
    }.sortedByVersionAndLoader()
}

private fun List<VersionInfoMap>.sortedByVersionAndLoader(): List<VersionInfoMap> {
    return sortedWith { a, b ->

        val versionCompare = -GameVersionNumber.compare(a.gameVersion, b.gameVersion)
        if (versionCompare != 0) {
            versionCompare
        } else {
            when {
                a.loader == null && b.loader == null -> 0
                a.loader == null -> 1
                b.loader == null -> -1
                else -> a.loader.getDisplayName().compareTo(b.loader.getDisplayName())
            }
        }
    }
}

private fun isVersionAdapt(
    currentVersion: Version?,
    gameVersion: String,
    loader: PlatformDisplayLabel?
): Boolean {
    return if (currentVersion == null) {
        false
    } else {
        if (currentVersion.getVersionInfo()?.minecraftVersion != gameVersion) {
            false
        } else {

            val loaderInfos = currentVersion.getVersionInfo()?.loaderInfos.orEmpty()
            when {
                loader == null -> true
                loaderInfos.isEmpty() -> false
                else -> loaderInfos.any { it.loader.displayName.equals(loader.getDisplayName(), true) }
            }
        }
    }
}

@Composable
fun AddonVersionItemLayout(
    modifier: Modifier = Modifier,
    infoMap: VersionInfoMap,
    defaultExpanded: Boolean = infoMap.isAdapt,
    maxListHeight: Dp = rememberMaxHeight(),
    shape: Shape = MaterialTheme.shapes.large,
    influencedByBackground: Boolean = true,
    color: Color = cardColor(influencedByBackground),
    contentColor: Color = onCardColor(),
    blur: Int = AllSettings.backgroundBlur.state,
    installedChecker: ((PlatformVersion) -> InstalledMod?)? = null,
    onItemClicked: (PlatformVersion) -> Unit = {}
) {
    var expanded by remember(infoMap) { mutableStateOf(defaultExpanded) }

    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .backgroundGlass(blur, color, influencedByBackground)
        ) {
            AddonVersionHeadLayout(
                modifier = Modifier.fillMaxWidth(),
                infoMap = infoMap,
                isAdapt = infoMap.isAdapt,
                hasInstalled = installedChecker != null && infoMap.versions.any { installedChecker(it) != null },
                expanded = expanded,
                onClick = { expanded = !expanded }
            )

            if (infoMap.versions.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (expanded)  {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxListHeight)
                                .padding(vertical = 4.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(infoMap.versions) { version ->
                                AddonVersionListItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(all = 4.dp),
                                    version = version,
                                    installed = installedChecker?.invoke(version),
                                    onClick = {
                                        onItemClicked(version)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddonVersionHeadLayout(
    modifier: Modifier = Modifier,
    infoMap: VersionInfoMap,
    isAdapt: Boolean,
    hasInstalled: Boolean,
    expanded: Boolean,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(vertical = 8.dp),
                text = infoMap.gameVersion,
                style = MaterialTheme.typography.titleSmall
            )
            infoMap.loader?.let { loader ->
                LittleTextLabel(
                    text = loader.getDisplayName(),
                    shape = MaterialTheme.shapes.small
                )
            }
        }
        if (isAdapt) {
            Icon(
                modifier = Modifier.size(18.dp),
                painter = painterResource(R.drawable.ic_star_filled),
                contentDescription = null
            )
        }
        if (hasInstalled) {
            InstalledModBadge(
                modifier = Modifier.padding(start = 4.dp),
                size = 16.dp
            )
        }
        if (!infoMap.versions.isEmpty()) {
            Row(
                modifier = Modifier.padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) -180f else 0f,
                    animationSpec = getAnimateTween()
                )
                Icon(
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(rotation),
                    painter = painterResource(R.drawable.ic_arrow_drop_down_rounded),
                    contentDescription = stringResource(if (expanded) R.string.generic_expand else R.string.generic_collapse)
                )
            }
        }
    }
}

@Composable
private fun AddonVersionListItem(
    modifier: Modifier = Modifier,
    version: PlatformVersion,
    installed: InstalledMod? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.medium)
            .background(
                color = if (installed != null) {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                } else {
                    Color.Transparent
                },
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {

        val releaseType = remember { version.platformReleaseType() }
        val displayName = remember { version.platformDisplayName() }
        val downloadCount = remember { version.platformDownloadCount() }
        val date = remember { version.platformDatePublished() }

        Box(
            modifier = Modifier
                .padding(start = 12.dp, end = 8.dp)
                .size(34.dp)
                .clip(shape = CircleShape)
                .background(releaseType.color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = releaseType.name.take(1),
                style = MaterialTheme.typography.labelLarge,
                color = releaseType.color
            )
        }

        Column(
            modifier = Modifier.padding(all = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelLarge
            )

            val context = LocalContext.current

            Row(
                modifier = Modifier.alpha(0.7f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(R.drawable.ic_download_2_outlined),
                        contentDescription = null
                    )
                    Text(
                        text = formatNumberByLocale(context, downloadCount),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(R.drawable.ic_autorenew),
                        contentDescription = null
                    )
                    Text(
                        text = getTimeAgo(
                            context = LocalContext.current,
                            pastInstant = date
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(R.drawable.ic_package_2_outlined),
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(releaseType.textRes),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectUrlsContent(
    platform: Platform,
    urls: PlatformProject.Urls,
    mcmod: AddonProjectInfo?,
    openLink: (String) -> Unit,
) {
    urls.projectUrl?.takeIf { it.isNotEmptyOrBlank() }?.let { url ->
        IconTextButton(
            onClick = { openLink(url) },
            iconSize = 18.dp,
            painter = when (platform) {
                Platform.CURSEFORGE -> painterResource(R.drawable.img_platform_curseforge)
                Platform.MODRINTH -> painterResource(R.drawable.img_platform_modrinth)
            },
            text = stringResource(R.string.download_addon_project_link)
        )
    }
    urls.sourceUrl?.takeIf { it.isNotEmptyOrBlank() }?.let { url ->
        IconTextButton(
            onClick = { openLink(url) },
            iconSize = 18.dp,
            painter = painterResource(R.drawable.ic_code),
            text = stringResource(R.string.download_addon_source_link)
        )
    }
    urls.issuesUrl?.takeIf { it.isNotEmptyOrBlank() }?.let { url ->
        IconTextButton(
            onClick = { openLink(url) },
            iconSize = 18.dp,
            painter = painterResource(R.drawable.ic_chat_info),
            text = stringResource(R.string.download_addon_issues_link)
        )
    }
    urls.wikiUrl?.takeIf { it.isNotEmptyOrBlank() }?.let { url ->
        IconTextButton(
            onClick = { openLink(url) },
            iconSize = 18.dp,
            painter = painterResource(R.drawable.ic_import_contacts_outlined),
            text = stringResource(R.string.download_addon_wiki_link)
        )
    }
    val context = LocalContext.current
    mcmod?.takeIf {
        isChinese(context)
    }?.mcmodUrl()?.takeIf {
        it.isNotEmptyOrBlank()
    }?.let { url ->
        IconTextButton(
            onClick = { openLink(url) },
            iconSize = 18.dp,
            painter = painterResource(R.drawable.ic_link),
            text = "MC 百科"
        )
    }
}

@Composable
fun ScreenshotItemLayout(
    screenshot: PlatformProject.Screenshot,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
) {
    val context = LocalContext.current

    val imageRequest = remember(screenshot) {
        screenshot.imageUrl.takeIf { it.isNotBlank() }?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .build()
        }
    }

    val painter = rememberAsyncImagePainter(
        model = imageRequest,
        placeholder = null,
        error = painterResource(R.drawable.ic_unknown_icon)
    )

    val state by painter.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            AsyncImagePainter.State.Empty -> {

            }
            is AsyncImagePainter.State.Error, is AsyncImagePainter.State.Loading -> {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(shape)
                )
            }
            is AsyncImagePainter.State.Success -> {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height)
                        .clip(shape)
                )
            }
        }

        if (screenshot.title != null && screenshot.title == screenshot.description) {

            Text(
                text = screenshot.title,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        } else {
            screenshot.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center
                )
            }
            screenshot.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
