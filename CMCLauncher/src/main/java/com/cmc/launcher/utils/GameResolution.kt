package com.cmc.launcher.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.setting.enums.ResolutionRule
import kotlin.math.roundToInt

private const val CUSTOM_RESOLUTION_MIN_SCALE = 0.2f
private const val CUSTOM_RESOLUTION_MAX_SCALE = 3f

fun customResolutionRange(screenSide: Int): IntRange {
    val min = getDisplayFriendlyRes(
        (screenSide * CUSTOM_RESOLUTION_MIN_SCALE).roundToInt().coerceAtLeast(2),
        1f
    ).coerceAtLeast(2)
    val max = (screenSide * CUSTOM_RESOLUTION_MAX_SCALE).roundToInt()
    return min..max.coerceAtLeast(min)
}

data class GameDisplayLayout(
    val renderSize: IntSize,
    val displaySize: IntSize,
    val offset: IntOffset
) {

    fun mapToGame(position: Offset): Offset {
        val scaleX = renderSize.width / displaySize.width.toFloat()
        val scaleY = renderSize.height / displaySize.height.toFloat()
        return Offset(
            (position.x - offset.x) * scaleX,
            (position.y - offset.y) * scaleY
        )
    }
}

fun computeGameRenderSize(
    screenSize: IntSize,
    rule: ResolutionRule,
    percentage: Int,
    customWidth: Int,
    customHeight: Int
): IntSize {
    return if (rule == ResolutionRule.CUSTOM) {
        fun fixCustomSide(value: Int, screenSide: Int): Int {
            val sanitized = value.takeIf { it > 0 } ?: screenSide
            return getDisplayFriendlyRes(sanitized.coerceIn(customResolutionRange(screenSide)), 1f)
        }
        IntSize(
            width = fixCustomSide(customWidth, screenSize.width),
            height = fixCustomSide(customHeight, screenSize.height)
        )
    } else {
        val scale = percentage / 100f
        IntSize(
            width = getDisplayFriendlyRes(screenSize.width, scale),
            height = getDisplayFriendlyRes(screenSize.height, scale)
        )
    }
}

fun computeGameRenderSize(screenSize: IntSize): IntSize = computeGameRenderSize(
    screenSize = screenSize,
    rule = AllSettings.resolutionRule.getValue(),
    percentage = AllSettings.resolutionRatio.getValue(),
    customWidth = AllSettings.customResolutionWidth.getValue(),
    customHeight = AllSettings.customResolutionHeight.getValue()
)

@Composable
fun rememberGameRenderSize(screenSize: IntSize): IntSize {
    val rule = AllSettings.resolutionRule.state
    val percentage = AllSettings.resolutionRatio.state
    val customWidth = AllSettings.customResolutionWidth.state
    val customHeight = AllSettings.customResolutionHeight.state
    return remember(screenSize, rule, percentage, customWidth, customHeight) {
        computeGameRenderSize(screenSize, rule, percentage, customWidth, customHeight)
    }
}

fun currentGameDisplayLayout(screenSize: IntSize): GameDisplayLayout {
    val renderSize = computeGameRenderSize(screenSize)
    return if (AllSettings.resolutionRule.state == ResolutionRule.CUSTOM) {
        computeGameDisplayLayout(screenSize, renderSize)
    } else {
        GameDisplayLayout(
            renderSize = renderSize,
            displaySize = screenSize,
            offset = IntOffset.Zero
        )
    }
}

fun computeGameDisplayLayout(screenSize: IntSize, renderSize: IntSize): GameDisplayLayout {
    val scale = minOf(
        screenSize.width / renderSize.width.toFloat(),
        screenSize.height / renderSize.height.toFloat()
    )
    val displaySize = IntSize(
        width = (renderSize.width * scale).roundToInt().coerceIn(1, screenSize.width),
        height = (renderSize.height * scale).roundToInt().coerceIn(1, screenSize.height)
    )
    return GameDisplayLayout(
        renderSize = renderSize,
        displaySize = displaySize,
        offset = IntOffset(
            x = (screenSize.width - displaySize.width) / 2,
            y = (screenSize.height - displaySize.height) / 2
        )
    )
}

fun getRealScreenSize(context: Context): IntSize {
    val metrics = context.resources.displayMetrics
    return IntSize(metrics.widthPixels, metrics.heightPixels)
}

fun ensureCustomResolutionInitialized(context: Context) {
    if (AllSettings.customResolutionWidth.getValue() > 0 &&
        AllSettings.customResolutionHeight.getValue() > 0
    ) return

    val screenSize = getRealScreenSize(context)
    AllSettings.customResolutionWidth.save(screenSize.width)
    AllSettings.customResolutionHeight.save(screenSize.height)
}
