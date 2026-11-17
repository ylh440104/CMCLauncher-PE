package com.movtery.cardgrid.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.movtery.cardgrid.state.CardGridState

private val AutoScrollEdge = 56.dp

private const val AutoScrollMaxSpeed = 900f

@Composable
fun CardGridAutoScroll(
    state: CardGridState,
    scrollState: ScrollState
) {
    val density = LocalDensity.current
    LaunchedEffect(state.hasSession) {
        if (!state.hasSession) return@LaunchedEffect
        var lastFrameNanos = 0L
        val edgePx = with(density) { AutoScrollEdge.toPx() }
        val maxSpeedPx = with(density) { AutoScrollMaxSpeed.dp.toPx() }
        while (true) {
            val frameNanos = withFrameNanos { it }
            if (lastFrameNanos != 0L) {
                val dt = (frameNanos - lastFrameNanos) / 1_000_000_000f
                val pointer = state.pointerPosition
                if (pointer != null) {
                    val viewportY = state.areaOffsetInRoot.y + pointer.y - state.viewportTopPx
                    val bottomDistance = state.viewportHeightPx - viewportY
                    val dyScroll = when {
                        viewportY in 0f..edgePx ->
                            -maxSpeedPx * (1f - viewportY / edgePx) * dt
                        bottomDistance in 0f..edgePx ->
                            maxSpeedPx * (1f - bottomDistance / edgePx) * dt
                        else -> 0f
                    }
                    if (dyScroll != 0f) {
                        scrollState.dispatchRawDelta(dyScroll)
                        state.onAutoScroll()
                    }
                }
            }
            lastFrameNanos = frameNanos
        }
    }
}
