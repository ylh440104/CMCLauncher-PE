package com.cmc.launcher.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

@Composable
fun FloatingBall(
    position: Offset,
    onPositionChanged: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    onSavePos: () -> Unit = {},
    onClick: () -> Unit = {},
    alpha: Float = 1f,
    color: Color = Color.Black.copy(alpha = 0.25f),
    contentColor: Color = Color.White.copy(alpha = 0.95f),
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit
) {
    var ballSize by remember { mutableStateOf(IntSize.Zero) }
    val currentPosition by rememberUpdatedState(position)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnSavePos by rememberUpdatedState(onSavePos)

    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }

    var isInitialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { isInitialized = true }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(
        modifier = Modifier
            .alpha(alpha)
            .fillMaxSize()
            .onSizeChanged { size ->
                if (currentPosition != Offset.Zero) {
                    val maxX = (size.width - ballSize.width).toFloat().coerceAtLeast(0f)
                    val maxY = (size.height - ballSize.height).toFloat().coerceAtLeast(0f)

                    val newX = currentPosition.x.coerceIn(0f, maxX)
                    val newY = currentPosition.y.coerceIn(0f, maxY)

                    if (newX != currentPosition.x || newY != currentPosition.y) {
                        onPositionChanged(Offset(newX, newY))
                    }
                }
            }
    ) {
        val parentWidth by rememberUpdatedState(constraints.maxWidth)
        val parentHeight by rememberUpdatedState(constraints.maxHeight)

        Surface(
            modifier = Modifier
                .onSizeChanged { size ->
                    ballSize = size
                    if (isInitialized || currentPosition != Offset.Zero) return@onSizeChanged
                    val x = ((parentWidth - ballSize.width) / 2f)
                    val positionX = x.coerceIn(0f, (parentWidth - ballSize.width).toFloat())
                    val positionY = 0f.coerceIn(0f, (parentHeight - ballSize.height).toFloat())
                    onPositionChanged(Offset(positionX, positionY))
                }
                .offset {
                    val renderPosition = if (dragging) dragPosition else currentPosition
                    val maxX = (parentWidth - ballSize.width).coerceAtLeast(0)
                    val maxY = (parentHeight - ballSize.height).coerceAtLeast(0)
                    IntOffset(
                        renderPosition.x.roundToInt().coerceIn(0, maxX),
                        renderPosition.y.roundToInt().coerceIn(0, maxY)
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        val startPosition = down.position
                        var isDragging = false

                        val clampedX = currentPosition.x
                            .coerceIn(0f, (parentWidth - ballSize.width).toFloat().coerceAtLeast(0f))
                        val clampedY = currentPosition.y
                            .coerceIn(0f, (parentHeight - ballSize.height).toFloat().coerceAtLeast(0f))
                        if (clampedX != currentPosition.x || clampedY != currentPosition.y) {
                            onPositionChanged(Offset(clampedX, clampedY))
                        }

                        val completed = drag(down.id) { change ->
                            val delta = change.positionChange()
                            val distanceFromStart = (change.position - startPosition).getDistance()

                            if (!isDragging && distanceFromStart > viewConfiguration.touchSlop) {

                                isDragging = true
                                dragging = true
                                dragPosition = Offset(clampedX, clampedY)
                            }

                            if (isDragging) {
                                val deltaX = if (isRtl) -delta.x else delta.x
                                val newX = dragPosition.x + deltaX
                                val newY = dragPosition.y + delta.y
                                dragPosition = Offset(
                                    newX.coerceIn(0f, (parentWidth - ballSize.width).toFloat()),
                                    newY.coerceIn(0f, (parentHeight - ballSize.height).toFloat())
                                )
                            }
                            change.consume()
                        }

                        val upConsumed = if (completed) {

                            awaitPointerEvent(PointerEventPass.Final)
                                .changes.firstOrNull { it.id == down.id }?.isConsumed ?: true
                        } else {
                            true
                        }
                        if (isDragging) {

                            onPositionChanged(dragPosition)
                            if (!upConsumed) {
                                currentOnSavePos()
                            }
                            dragging = false
                        } else if (!upConsumed) {

                            currentOnClick()
                        }
                    }
                }.then(modifier),
            color = color,
            contentColor = contentColor,
            shape = shape
        ) {
            content()
        }
    }
}
