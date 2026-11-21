package com.cmc.launcher.ui.screens.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import com.cmc.launcher.R
import com.cmc.launcher.game.account.Account
import com.cmc.launcher.game.account.AccountsManager
import com.cmc.launcher.game.account.getAccountTypeName
import com.cmc.launcher.game.account.wardrobe.toArmModel
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.installed.VersionsManager
import com.cmc.launcher.ui.base.BaseScreen
import com.cmc.launcher.ui.components.PetModelView
import com.cmc.launcher.ui.screens.NestedNavKey
import com.cmc.launcher.ui.screens.NormalNavKey
import com.cmc.launcher.ui.screens.content.elements.PlayerFace
import com.cmc.launcher.ui.screens.content.elements.VersionIconImage
import com.cmc.launcher.ui.screens.content.home.HomeGrid
import com.cmc.launcher.ui.screens.content.home.server.LocalServerCardQuickPlay
import com.cmc.launcher.ui.screens.content.home.version.LocalHomeCardLauncher
import com.cmc.launcher.ui.screens.content.home.version.LocalHomeCardVersionSettings
import com.cmc.launcher.ui.theme.Alpha
import com.cmc.launcher.ui.theme.CardShape
import com.cmc.launcher.ui.theme.Radius
import com.cmc.launcher.ui.theme.Space
import com.cmc.launcher.ui.theme.cardColor
import com.cmc.launcher.ui.theme.onCardColor
import com.cmc.launcher.viewmodel.ScreenBackStackViewModel
import com.movtery.cardgrid.state.rememberCardGridState
import com.movtery.petlib.PetAnimation
import com.movtery.petlib.PetTapReaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun LauncherScreen(
    backStackViewModel: ScreenBackStackViewModel,
    navigateToVersions: (Version) -> Unit,
    onLaunchGame: (Version?) -> Unit,
    onQuickPlayServer: (Version?, String) -> Unit,
    onOpenLink: (String) -> Unit
) {
    BaseScreen(
        screenKey = NormalNavKey.LauncherMain,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) { isVisible ->
        val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
        val version by VersionsManager.currentVersion.collectAsStateWithLifecycle()
        val isRefreshing by VersionsManager.isRefreshing.collectAsStateWithLifecycle()

        val toAccounts: () -> Unit = {
            backStackViewModel.mainScreen.navigateTo(
                screenKey = NormalNavKey.AccountManager(FirstLoginMenu.NONE)
            )
        }
        val toVersions: () -> Unit = {
            backStackViewModel.mainScreen.removeAndNavigateTo(
                remove = NestedNavKey.VersionSettings::class,
                screenKey = NormalNavKey.VersionsManager
            )
        }
        val toDownload: () -> Unit = { backStackViewModel.navigateToDownload() }

        CompositionLocalProvider(
            LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) = onOpenLink(uri)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Space.screenH)
            ) {
                Spacer(Modifier.height(Space.sm))

                LaunchPanel(
                    account = account,
                    version = version,
                    isRefreshing = isRefreshing,
                    onAccounts = toAccounts,
                    onVersions = toVersions,
                    onVersionSettings = { selected -> navigateToVersions(selected) },
                    onAddVersion = toDownload,
                    onLaunch = { onLaunchGame(version) }
                )

                Spacer(Modifier.height(Space.sm))

                HomeContent(
                    isVisible = isVisible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onLaunchGame = { onLaunchGame(it) },
                    onQuickPlayServer = onQuickPlayServer,
                    onOpenVersionSettings = navigateToVersions
                )
            }
        }
    }
}

@Composable
private fun LaunchPanel(
    account: Account?,
    version: Version?,
    isRefreshing: Boolean,
    onAccounts: () -> Unit,
    onVersions: () -> Unit,
    onVersionSettings: (Version) -> Unit,
    onAddVersion: () -> Unit,
    onLaunch: () -> Unit
) {
    var versionMenu by remember { mutableStateOf(false) }
    val versions by VersionsManager.versions.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = cardColor(),
        contentColor = onCardColor(),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alpha.strong)
        )
    ) {
        Column(Modifier.padding(Space.cardInner)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (account != null) {
                    PlayerFace(account = account, avatarSize = 40.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(Radius.medium))
                            .clickable(onClick = onAccounts),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_person_outlined),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(Space.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account?.username ?: "未登录账号",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (account != null) getAccountTypeName(account) else "点此登录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Text(
                    text = "切换",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.small))
                        .clickable(onClick = onAccounts)
                        .padding(horizontal = Space.sm, vertical = Space.xs)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = Space.sm),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alpha.strong)
            )

            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.medium))
                        .clickable(enabled = versions.isNotEmpty()) { versionMenu = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VersionIconImage(
                        version = version,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.width(Space.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isRefreshing -> "正在扫描版本…"
                                version == null -> "未安装游戏版本"
                                else -> version.getVersionName()
                            },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                version == null -> "点击右侧按钮去安装"
                                version.isValid() -> version.getVersionSummary()
                                else -> "版本文件不完整"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (version != null && version.isValid()) {
                        IconButton(onClick = { onVersionSettings(version) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings_filled),
                                contentDescription = stringResource(R.string.versions_manage_settings),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onVersions) {
                        Icon(
                            painter = painterResource(R.drawable.ic_dashboard_outlined),
                            contentDescription = stringResource(R.string.page_title_version_manage),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DropdownMenu(
                    expanded = versionMenu,
                    onDismissRequest = { versionMenu = false },
                    shape = RoundedCornerShape(Radius.medium)
                ) {
                    versions.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    VersionIconImage(
                                        version = item,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(Space.sm))
                                    Text(
                                        text = item.getVersionName(),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            onClick = {
                                versionMenu = false
                                if (item != version) VersionsManager.saveVersion(item)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.md))

            if (version == null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable(onClick = onAddVersion),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(Radius.pill)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            text = "安装游戏版本",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable(enabled = version.isValid(), onClick = onLaunch),
                    color = if (version.isValid()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (version.isValid()) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(Radius.pill)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_arrow_filled),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            text = stringResource(R.string.main_launch_game),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    onLaunchGame: (Version) -> Unit,
    onQuickPlayServer: (Version?, String) -> Unit,
    onOpenVersionSettings: (Version) -> Unit
) {
    val gridState = rememberCardGridState()
    CompositionLocalProvider(
        LocalHomeCardLauncher provides onLaunchGame,
        LocalHomeCardVersionSettings provides onOpenVersionSettings,
        LocalServerCardQuickPlay provides onQuickPlayServer
    ) {
        HomeGrid(
            state = gridState,
            isVisible = isVisible,
            modifier = modifier.fillMaxSize()
        )
    }
}
