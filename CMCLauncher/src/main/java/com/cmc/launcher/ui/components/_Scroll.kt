package com.cmc.launcher.ui.components

import android.view.ViewConfiguration
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.fadeEdge(
    state: ScrollState,
    length: Dp = 12.dp,
    direction: EdgeDirection = EdgeDirection.Vertical,
    position: EdgeSide = EdgeSide.Both,
    style: FadeStyle = createDefaultFadeStyle(direction)
): Modifier {
    val fadePx = with(LocalDensity.current) { length.toPx() }

    return this

        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            val startFade = if (state.canScrollBackward) {
                (state.value.toFloat() / fadePx).coerceIn(0f, 1f) * fadePx
            } else 0f

            val endFade = if (state.canScrollForward) {
                ((state.maxValue - state.value).toFloat() / fadePx).coerceIn(0f, 1f) * fadePx
            } else 0f

            drawContent()
            drawFadeEdges(startFade, endFade, direction, position, style)
        }
}

@Composable
fun Modifier.fadeEdge(
    state: LazyListState,
    length: Dp = 12.dp,
    direction: EdgeDirection = EdgeDirection.Vertical,
    position: EdgeSide = EdgeSide.Both,
    style: FadeStyle = createDefaultFadeStyle(direction)
): Modifier {
    val fadePx = with(LocalDensity.current) { length.toPx() }

    return this

        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {

            val layoutInfo = state.layoutInfo

            val firstItem = layoutInfo.visibleItemsInfo.firstOrNull()
            val topDistancePx = when {
                firstItem == null -> 0f
                state.firstVisibleItemIndex == 0 -> -firstItem.offset.toFloat()
                else -> fadePx
            }
            val startFade = if (state.canScrollBackward) {
                (topDistancePx / fadePx).coerceIn(0f, 1f) * fadePx
            } else 0f

            val lastItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val bottomDistancePx = when {
                lastItem == null -> 0f
                state.canScrollForward.not() -> 0f
                state.firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size == layoutInfo.totalItemsCount &&
                        lastItem.offset + lastItem.size > viewportHeight ->
                    (lastItem.offset + lastItem.size - viewportHeight).toFloat()
                else -> fadePx
            }
            val endFade = if (state.canScrollForward) {
                (bottomDistancePx / fadePx).coerceIn(0f, 1f) * fadePx
            } else 0f

            drawContent()
            drawFadeEdges(startFade, endFade, direction, position, style)
        }
}

private fun DrawScope.drawFadeEdges(
    startFade: Float,
    endFade: Float,
    direction: EdgeDirection,
    position: EdgeSide,
    style: FadeStyle
) {
    val totalSize = if (direction == EdgeDirection.Vertical) size.height else size.width

    if (position.includeStart && startFade > 0f) {
        drawFadeEdge(
            insetStart = 0f,
            insetEnd = totalSize - startFade,
            direction = direction,
            reverse = false,
            style = style
        )
    }

    if (position.includeEnd && endFade > 0f) {
        drawFadeEdge(
            insetStart = totalSize - endFade,
            insetEnd = 0f,
            direction = direction,
            reverse = true,
            style = style
        )
    }
}

private fun DrawScope.drawFadeEdge(
    insetStart: Float,
    insetEnd: Float,
    direction: EdgeDirection,
    reverse: Boolean,
    style: FadeStyle
) {
    val isVertical = direction == EdgeDirection.Vertical
    inset(
        left = if (!isVertical) insetStart else 0f,
        top = if (isVertical) insetStart else 0f,
        right = if (!isVertical) insetEnd else 0f,
        bottom = if (isVertical) insetEnd else 0f
    ) {
        rotate(if (reverse) 180f else 0f) {
            drawRect(brush = style.brush, blendMode = style.blendMode)
        }
    }
}

@Immutable
enum class EdgeDirection {
    Vertical, Horizontal
}

@Immutable
enum class EdgeSide(val includeStart: Boolean, val includeEnd: Boolean) {

    Start(true, false),

    End(false, true),

    Both(true, true)
}

@Immutable
data class FadeStyle(
    val brush: Brush,
    val blendMode: BlendMode
)

fun createDefaultFadeStyle(
    direction: EdgeDirection
): FadeStyle {

    val colorStops = arrayOf(
        0f to Color.Black,
        1f to Color.Transparent
    )

    return FadeStyle(
        brush = when (direction) {
            EdgeDirection.Vertical -> Brush.verticalGradient(*colorStops)
            EdgeDirection.Horizontal -> Brush.horizontalGradient(*colorStops)
        },
        blendMode = BlendMode.DstOut
    )
}

@Composable
fun Modifier.verticalScrollWithBar(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false
): Modifier = this.nonInteractiveScrollbar(
    state = state.scrollIndicatorState!!,
    orientation = Orientation.Vertical
).verticalScroll(
    state = state,
    enabled = enabled,
    flingBehavior = flingBehavior,
    reverseScrolling = reverseScrolling,
)

@Composable
fun Modifier.verticalScrollWithBar(
    state: ScrollState,
    overscrollEffect: OverscrollEffect?,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false
): Modifier = this.nonInteractiveScrollbar(
    state = state.scrollIndicatorState!!,
    orientation = Orientation.Vertical
).verticalScroll(
    state = state,
    overscrollEffect = overscrollEffect,
    enabled = enabled,
    flingBehavior = flingBehavior,
    reverseScrolling = reverseScrolling,
)

@Composable
fun Modifier.lazyScrollWithBar(
    state: LazyListState,
    orientation: Orientation = Orientation.Vertical
): Modifier = this.nonInteractiveScrollbar(
    state = state.scrollIndicatorState!!,
    orientation = orientation
)

@Composable
fun Modifier.topBarWheelFallback(state: TopAppBarState): Modifier {

    val scrollFactor = ViewConfiguration.get(LocalView.current.context).scaledVerticalScrollFactor
    return pointerInput(state, scrollFactor) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.type != PointerEventType.Scroll) continue
                if (event.changes.any { it.isConsumed }) continue

                val deltaY = event.changes.fold(0f) { acc, change -> acc + change.scrollDelta.y } * scrollFactor
                val target = (state.heightOffset - deltaY).coerceIn(state.heightOffsetLimit, 0f)
                if (target != state.heightOffset) {
                    state.heightOffset = target
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}
