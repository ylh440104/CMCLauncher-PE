package com.cmc.launcher.ui.screens.content.home.version

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.cmc.launcher.ui.theme.cmc.Cmc
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.cardgrid.model.CardInteraction
import com.movtery.cardgrid.model.CardSize
import com.movtery.cardgrid.model.CardSizeClass
import com.movtery.cardgrid.model.CardState
import com.cmc.launcher.R
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.ui.components.LittleTextLabel
import com.cmc.launcher.ui.screens.content.elements.VersionIconImage
import com.cmc.launcher.ui.screens.content.home.CardIconShape

val LocalHomeCardLauncher = staticCompositionLocalOf<(Version) -> Unit> { {} }

val LocalHomeCardVersionSettings = staticCompositionLocalOf<(Version) -> Unit> { {} }

private const val DESCRIPTION_START_SPAN = 9

private val DetailTopGap = 4.dp

private val DetailItemGap = 2.dp

@Composable
fun CardState.VersionCardContent(cardId: String) {
    val states by VersionCardManager.cards.collectAsStateWithLifecycle()
    val card = remember(states, cardId) {
        states.firstOrNull { it.record.cardId == cardId }
    }
    val version = (card?.status as? VersionCardStatus.Available)?.version
    val onOpenSettings = LocalHomeCardVersionSettings.current

    val currentVersion by rememberUpdatedState(version)
    val currentInteraction by rememberUpdatedState(interaction)

    val showDetails = sizeClass.height >= CardSizeClass.MEDIUM
    val iconSize = iconSizeFor(sizeClass)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .homeCardTap {
                if (currentInteraction == CardInteraction.Idle) {
                    currentVersion?.let(onOpenSettings)
                }
            }
            .padding(12.dp)
    ) {
        when {
            sizeClass.width >= CardSizeClass.MEDIUM &&
                    sizeClass.height >= CardSizeClass.LARGE -> TallContent(
                card = card,
                version = version,
                iconSize = enlargedIconSize(sizeClass.width),
                spanHeight = spanHeight,
                showDetails = showDetails
            ) {
                if (version != null) TextLaunchButton(version)
            }
            sizeClass.width <= CardSizeClass.SMALL &&
                    sizeClass.height > CardSizeClass.SMALL -> TallContent(
                card = card,
                version = version,
                iconSize = enlargedIconSize(sizeClass.height),
                spanHeight = spanHeight,
                showDetails = showDetails
            ) {
                if (version != null) {
                    CompactLaunchButton(
                        version = version,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                    )
                }
            }
            else -> RowContent(
                card = card,
                version = version,
                iconSize = iconSize,
                showDetails = showDetails,
                textButton = sizeClass.width >= CardSizeClass.LARGE,
                spacing = if (sizeClass.width <= CardSizeClass.SMALL) 6.dp else 12.dp
            )
        }
    }
}

private fun iconSizeFor(sizeClass: CardSize): Dp {
    val cramped = sizeClass.height == CardSizeClass.COMPACT
    return when (sizeClass.width) {
        CardSizeClass.EXTRA_LARGE,
        CardSizeClass.LARGE -> if (cramped) 32.dp else 44.dp
        CardSizeClass.MEDIUM -> if (cramped) 28.dp else 36.dp
        else -> if (cramped) 24.dp else 28.dp
    }
}

private fun enlargedIconSize(sizeClass: CardSizeClass): Dp = when (sizeClass) {
    CardSizeClass.MEDIUM -> 48.dp
    CardSizeClass.LARGE -> 56.dp
    else -> 64.dp
}

@Composable
private fun lineHeightDp(style: TextStyle): Dp = with(LocalDensity.current) {
    style.lineHeight.takeOrElse { style.fontSize }.toDp()
}

private fun Modifier.homeCardTap(onTap: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.isConsumed) return@awaitEachGesture
        val startPosition = down.position
        val slopPx = viewConfiguration.touchSlop
        val longPressMillis = viewConfiguration.longPressTimeoutMillis
        val tapped = withTimeoutOrNull(longPressMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                if ((change.position - startPosition).getDistance() > slopPx) {
                    return@withTimeoutOrNull false
                }
                if (!change.pressed) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") false
        } ?: false
        if (tapped) onTap()
    }
}

@Composable
private fun TallContent(
    card: VersionCardState?,
    version: Version?,
    iconSize: Dp,
    spanHeight: Int,
    showDetails: Boolean,
    button: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
        ) {

            val blank = maxHeight - iconSize - DetailTopGap
            val summaryLine = lineHeightDp(MaterialTheme.typography.labelMedium)
            val summaryValid = version?.isSummaryValid() == true

            val blankText = version
                ?.takeIf {
                    spanHeight >= DESCRIPTION_START_SPAN &&
                            blank >= summaryLine &&
                            (summaryValid || it.getVersionInfo() != null)
                }
                ?.getVersionSummary()

            val blankMaxLines = (blank.value / summaryLine.value).toInt()

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VersionIconImage(
                        modifier = Modifier
                            .size(iconSize)
                            .clip(CardIconShape),
                        version = version
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(DetailItemGap)
                    ) {
                        CardNameText(card = card, version = version)

                        if (showDetails && version != null && (summaryValid || blankText == null)) {
                            InfoRow(version = version)
                        }
                        when (card?.status) {
                            is VersionCardStatus.Deleted -> StatusPlaceholder(
                                text = stringResource(R.string.home_version_card_deleted)
                            )
                            is VersionCardStatus.Inaccessible -> StatusPlaceholder(
                                text = stringResource(R.string.home_version_card_inaccessible)
                            )
                            else -> Unit
                        }
                    }
                }
                if (blankText != null) {

                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = DetailTopGap),
                        maxLines = blankMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        text = blankText,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            button()
        }
    }
}

@Composable
private fun InfoRow(version: Version) {
    val versionInfo = version.getVersionInfo()
    Row(
        modifier = Modifier
            .alpha(0.7f)
            .basicMarquee(iterations = Int.MAX_VALUE),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = versionInfo?.minecraftVersion ?: "",
            style = MaterialTheme.typography.labelSmall
        )
        versionInfo?.loaderInfos?.forEach { loaderInfo ->
            Text(
                text = loaderInfo.loader.displayName,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = loaderInfo.version,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun CardNameText(
    modifier: Modifier = Modifier,
    card: VersionCardState?,
    version: Version?
) {
    card?.record?.versionName?.let { name ->
        Text(
            modifier = modifier
                .basicMarquee(iterations = Int.MAX_VALUE)
                .then(if (version == null) Modifier.alpha(0.55f) else Modifier),
            maxLines = 1,
            text = name,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun RowContent(
    card: VersionCardState?,
    version: Version?,
    iconSize: Dp,
    showDetails: Boolean,
    textButton: Boolean,
    spacing: Dp
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VersionIconImage(
            modifier = Modifier
                .size(iconSize)
                .clip(CardIconShape),
            version = version
        )
        CardTexts(
            modifier = Modifier.weight(1f),
            card = card,
            version = version,
            showDetails = showDetails
        )
        if (version != null) {
            if (textButton) {
                TextLaunchButton(version)
            } else {
                IconLaunchButton(version)
            }
        }
    }
}

@Composable
private fun CardTexts(
    modifier: Modifier = Modifier,
    card: VersionCardState?,
    version: Version?,
    showDetails: Boolean
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DetailItemGap)
    ) {
        CardNameText(card = card, version = version)

        when (val status = card?.status) {
            is VersionCardStatus.Available -> if (showDetails) {

                val versionInfo = status.version.getVersionInfo()
                FlowRow(
                    modifier = Modifier.alpha(0.7f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(DetailItemGap)
                ) {
                    Text(
                        text = versionInfo?.minecraftVersion ?: "",
                        style = MaterialTheme.typography.labelSmall
                    )
                    versionInfo?.loaderInfos?.forEach { loaderInfo ->
                        Text(
                            text = loaderInfo.loader.displayName,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = loaderInfo.version,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            is VersionCardStatus.Deleted -> StatusPlaceholder(
                text = stringResource(R.string.home_version_card_deleted)
            )
            is VersionCardStatus.Inaccessible -> StatusPlaceholder(
                text = stringResource(R.string.home_version_card_inaccessible)
            )
            null, VersionCardStatus.Loading -> Unit
        }
    }
}

@Composable
private fun TextLaunchButton(version: Version, modifier: Modifier = Modifier) {
    val onLaunch = LocalHomeCardLauncher.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Cmc.Accent, Cmc.Blue)))
            .clickable { onLaunch(version) }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = Modifier.size(15.dp),
                painter = painterResource(R.drawable.ic_play_arrow_filled),
                contentDescription = null,
                tint = Cmc.Ink
            )
            Text(
                modifier = Modifier.padding(start = 5.dp),
                text = stringResource(R.string.main_launch_game),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Cmc.Ink
            )
        }
    }
}

@Composable
private fun CompactLaunchButton(version: Version, modifier: Modifier = Modifier) {
    val onLaunch = LocalHomeCardLauncher.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Cmc.Accent, Cmc.Blue)))
            .clickable { onLaunch(version) },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = Modifier.size(13.dp),
                painter = painterResource(R.drawable.ic_play_arrow_filled),
                contentDescription = null,
                tint = Cmc.Ink
            )
            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = stringResource(R.string.main_launch_game),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Cmc.Ink
            )
        }
    }
}

@Composable
private fun IconLaunchButton(version: Version, modifier: Modifier = Modifier) {
    val onLaunch = LocalHomeCardLauncher.current
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Cmc.Accent, Cmc.Blue)))
            .clickable { onLaunch(version) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(17.dp),
            painter = painterResource(R.drawable.ic_play_arrow_filled),
            contentDescription = stringResource(R.string.main_launch_game),
            tint = Cmc.Ink
        )
    }
}

@Composable
private fun StatusPlaceholder(text: String) {
    LittleTextLabel(
        text = text,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        textStyle = MaterialTheme.typography.labelSmall
    )
}
