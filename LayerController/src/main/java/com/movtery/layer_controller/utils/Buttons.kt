package com.movtery.layer_controller.utils

import androidx.annotation.FloatRange
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.toAndroidShape
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.movtery.layer_controller.observable.ObservableWidget
import com.movtery.layer_controller.utils.snap.GuideLine
import com.movtery.layer_controller.utils.snap.LineDirection
import com.movtery.layer_controller.utils.snap.SnapMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
internal fun Modifier.editMode(
    isEditMode: Boolean,
    data: ObservableWidget,
    screenSize: IntSize,
    enableSnap: Boolean,
    snapMode: SnapMode,
    localSnapRange: Dp,
    getOtherWidgets: () -> List<ObservableWidget>,
    snapThresholdValue: Dp,
    drawLine: (ObservableWidget, List<GuideLine>) -> Unit,
    onLineCancel: (ObservableWidget) -> Unit,
    onTapInEditMode: () -> Unit = {}
): Modifier {
    val screenSize1 by rememberUpdatedState(screenSize)
    val widgetSize by rememberUpdatedState(data.internalRenderSize)

    val enableSnap1 by rememberUpdatedState(enableSnap)
    val snapMode1 by rememberUpdatedState(snapMode)

    val getOtherWidgets1 by rememberUpdatedState(getOtherWidgets)
    val drawLine1 by rememberUpdatedState(drawLine)
    val onLineCancel1 by rememberUpdatedState(onLineCancel)

    val onTapInEditMode1 by rememberUpdatedState(onTapInEditMode)

    val density = LocalDensity.current
    val snapThreshold = with(density) { snapThresholdValue.toPx() }
    val localSnapRangePx = with(density) { localSnapRange.toPx() }

    return this.then(
        if (isEditMode) {
            Modifier
                .pointerInput(data, snapThreshold, localSnapRangePx) {
                    detectDragGestures(
                        onDragStart = {
                            data.isEditingPos = false
                            data.movingOffset = Offset.Zero
                            val currentOffset = getWidgetPosition(data, widgetSize, screenSize1)
                            data.movingOffset = currentOffset
                            data.isEditingPos = true
                            onLineCancel1(data)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val currentOffset = getWidgetPosition(data, widgetSize, screenSize1)

                            var newX = currentOffset.x + dragAmount.x
                            var newY = currentOffset.y + dragAmount.y

                            val maxX = screenSize1.width.toFloat() - widgetSize.width
                            val maxY = screenSize1.height.toFloat() - widgetSize.height

                            newX = newX.coerceIn(0f, maxX)
                            newY = newY.coerceIn(0f, maxY)

                            val newPosition = Offset(newX, newY).also { data.movingOffset = it }

                            val newPercentagePosition = newPosition.toPercentagePosition(
                                widgetSize = widgetSize,
                                screenSize = screenSize1
                            )

                            val finalPosition = if (enableSnap1) {
                                calculateSnapPosition(
                                    currentPosition = newPercentagePosition,
                                    widgetSize = widgetSize,
                                    screenSize = screenSize1,
                                    otherWidgets = getOtherWidgets1(),
                                    snapThreshold = snapThreshold,
                                    snapMode = snapMode1,
                                    localSnapRange = localSnapRangePx,
                                    drawLine = { lines ->
                                        drawLine1(data, lines)
                                    },
                                    onLineCancel = {
                                        onLineCancel1(data)
                                    }
                                )
                            } else {
                                onLineCancel(data)
                                newPercentagePosition
                            }

                            data.putRenderPosition(finalPosition)
                        },
                        onDragEnd = {
                            data.isEditingPos = false
                            data.movingOffset = Offset.Zero
                            onLineCancel1(data)
                        },
                        onDragCancel = {
                            data.isEditingPos = false
                            data.movingOffset = Offset.Zero
                            onLineCancel1(data)
                        }
                    )
                }
                .pointerInput(data) {
                    detectTapGestures(
                        onTap = { onTapInEditMode1() }
                    )
                }
        } else Modifier
    )
}

private fun calculateSnapPosition(
    currentPosition: ButtonPosition,
    widgetSize: IntSize,
    screenSize: IntSize,
    otherWidgets: List<ObservableWidget>,
    snapThreshold: Float,
    snapMode: SnapMode,
    localSnapRange: Float,
    drawLine: (List<GuideLine>) -> Unit,
    onLineCancel: () -> Unit,
): ButtonPosition {
    val currentOffset = getWidgetPosition(currentPosition, widgetSize, screenSize)

    val currentLeft = currentOffset.x
    val currentRight = currentOffset.x + widgetSize.width
    val currentTop = currentOffset.y
    val currentBottom = currentOffset.y + widgetSize.height

    val newXWithLines = mutableMapOf<Float, GuideLine>()
    val newYWithLines = mutableMapOf<Float, GuideLine>()

    for (otherData in otherWidgets) {
        val otherSize = otherData.internalRenderSize
        val otherPosition = getWidgetPosition(otherData, otherSize, screenSize)
        val otherLeft = otherPosition.x
        val otherRight = otherPosition.x + otherSize.width
        val otherTop = otherPosition.y
        val otherBottom = otherPosition.y + otherSize.height

        if (snapMode == SnapMode.Local) {
            val minDistance = calculateMinDistanceBetweenRects(
                currentLeft, currentTop, currentRight, currentBottom,
                otherLeft, otherTop, otherRight, otherBottom
            )

            if (minDistance > localSnapRange) {
                continue
            }
        }

        val rightToLeft = abs(currentRight - otherLeft)
        val leftToRight = abs(currentLeft - otherRight)

        val bottomToTop = abs(currentBottom - otherTop)
        val topToBottom = abs(currentTop - otherBottom)

        val leftToLeft = abs(currentLeft - otherLeft)
        val rightToRight = abs(currentRight - otherRight)
        val topToTop = abs(currentTop - otherTop)
        val bottomToBottom = abs(currentBottom - otherBottom)

        if (rightToLeft < snapThreshold) {
            newXWithLines[otherLeft - widgetSize.width] = GuideLine(LineDirection.Vertical, otherLeft)
        } else if (leftToRight < snapThreshold) {
            newXWithLines[otherRight] = GuideLine(LineDirection.Vertical, otherRight)
        }

        if (bottomToTop < snapThreshold) {
            newYWithLines[otherTop - widgetSize.height] = GuideLine(LineDirection.Horizontal, otherTop)
        } else if (topToBottom < snapThreshold) {
            newYWithLines[otherBottom] = GuideLine(LineDirection.Horizontal, otherBottom)
        }

        if (leftToLeft < snapThreshold) {
            newXWithLines[otherLeft] = GuideLine(LineDirection.Vertical, otherLeft)
        } else if (rightToRight < snapThreshold) {
            newXWithLines[otherRight - widgetSize.width] = GuideLine(LineDirection.Vertical, otherRight)
        }

        if (topToTop < snapThreshold) {
            newYWithLines[otherTop] = GuideLine(LineDirection.Horizontal, otherTop)
        } else if (bottomToBottom < snapThreshold) {
            newYWithLines[otherBottom - widgetSize.height] = GuideLine(LineDirection.Horizontal, otherBottom)
        }
    }

    val newX = newXWithLines.minByOrNull { abs(it.key - currentOffset.x) }
    val newY = newYWithLines.minByOrNull { abs(it.key - currentOffset.y) }

    if (newX == null && newY == null) {

        onLineCancel()
        return currentPosition
    } else {
        listOfNotNull(newX?.value, newY?.value).let { guideLines ->
            if (guideLines.isNotEmpty()) {
                drawLine(guideLines)
            } else {
                onLineCancel()
            }
        }
        return Offset(newX?.key ?: currentOffset.x, newY?.key ?: currentOffset.y)
            .toPercentagePosition(widgetSize, screenSize)
    }
}

@Composable
internal fun Modifier.buttonSize(
    data: ObservableWidget,
    screenSize: IntSize
): Modifier {
    val size = data.widgetSize

    return this.then(
        when (size.type) {
            ButtonSize.Type.Dp -> Modifier.size(
                width = size.widthDp.dp,
                height = size.heightDp.dp
            )

            ButtonSize.Type.Percentage -> {
                val screenWidth = screenSize.width.toFloat()
                val screenHeight = screenSize.height.toFloat()

                val widthReference = when (size.widthReference) {
                    ButtonSize.Reference.ScreenWidth -> screenWidth
                    ButtonSize.Reference.ScreenHeight -> screenHeight
                }
                val heightReference = when (size.heightReference) {
                    ButtonSize.Reference.ScreenWidth -> screenWidth
                    ButtonSize.Reference.ScreenHeight -> screenHeight
                }

                val density = LocalDensity.current
                with(density) {
                    val buttonWidth = (widthReference * (size.widthPercentage / 10000f)).toDp()
                    val buttonHeight = (heightReference * (size.heightPercentage / 10000f)).toDp()
                    Modifier.size(width = buttonWidth, height = buttonHeight)
                }
            }

            ButtonSize.Type.WrapContent -> Modifier.wrapContentSize()
        }
    )
}

@Composable
internal fun buttonContentColorAsState(
    style: ObservableButtonStyle,
    isDark: Boolean = isSystemInDarkTheme(),
    isPressed: Boolean
): State<Color> {
    val themeStyle = if (style.commonStyle || !isDark) {
        style.lightStyle
    } else {
        style.darkStyle
    }

    val targetColor = remember(themeStyle, isPressed, themeStyle.pressedContentColor, themeStyle.contentColor) {
        if (isPressed) themeStyle.pressedContentColor else themeStyle.contentColor
    }

    return if (style.animateSwap) {
        animateColorAsState(targetColor, label = "contentColorAnimation")
    } else {
        remember(targetColor) { mutableStateOf(targetColor) }
    }
}

@Composable
internal fun buttonFontSizeAsState(
    style: ObservableButtonStyle,
    isDark: Boolean = isSystemInDarkTheme(),
    isPressed: Boolean
): State<Float> {
    val themeStyle = if (style.commonStyle || !isDark) {
        style.lightStyle
    } else {
        style.darkStyle
    }
    val textStyle = LocalTextStyle.current

    val fontSize: Float = remember(themeStyle, textStyle, isPressed, themeStyle.fontSize, themeStyle.pressedFontSize) {
        val defaultFontSize = textStyle.fontSize.value
        val size: Int? = if (isPressed) {
            themeStyle.pressedFontSize
        } else {
            themeStyle.fontSize
        }
        size?.toFloat() ?: defaultFontSize
    }

    return if (style.animateSwap) {
        animateFloatAsState(fontSize, label = "fontSizeAnimation")
    } else {
        remember(fontSize) {
            mutableFloatStateOf(fontSize)
        }
    }
}

@Composable
internal fun Modifier.buttonStyle(
    style: ObservableButtonStyle,
    isDark: Boolean = isSystemInDarkTheme(),
    isPressed: Boolean
): Modifier {
    val themeStyle = if (style.commonStyle || !isDark) {
        style.lightStyle
    } else {
        style.darkStyle
    }

    val alpha = remember(themeStyle, isPressed, themeStyle.pressedAlpha, themeStyle.alpha) {
        if (isPressed) themeStyle.pressedAlpha else themeStyle.alpha
    }
    val backgroundColor = remember(themeStyle, isPressed, themeStyle.pressedBackgroundColor, themeStyle.backgroundColor) {
        if (isPressed) themeStyle.pressedBackgroundColor else themeStyle.backgroundColor
    }
    val borderWidth = remember(themeStyle, isPressed, themeStyle.pressedBorderWidth, themeStyle.borderWidth) {
        val value = if (isPressed) themeStyle.pressedBorderWidth else themeStyle.borderWidth
        if (value == 0) (-1).dp else value.dp
    }
    val borderColor = remember(themeStyle, isPressed, themeStyle.pressedBorderColor, themeStyle.borderColor) {
        if (isPressed) themeStyle.pressedBorderColor else themeStyle.borderColor
    }
    val borderRadius = remember(themeStyle, isPressed, themeStyle.pressedBorderRadius, themeStyle.borderRadius) {
        if (isPressed) themeStyle.pressedBorderRadius.toAndroidShape() else themeStyle.borderRadius.toAndroidShape()
    }

    return if (style.animateSwap) {
        this.animatedButtonModifier(alpha, backgroundColor, borderWidth, borderColor, borderRadius)
    } else {
        this.staticButtonModifier(alpha, backgroundColor, borderWidth, borderColor, borderRadius)
    }
}

@Composable
private fun Modifier.animatedButtonModifier(
    alpha: Float,
    backgroundColor: Color,
    borderWidth: Dp,
    borderColor: Color,
    borderRadius: RoundedCornerShape
): Modifier {
    val alphaA by animateFloatAsState(alpha, label = "alphaAnimation")
    val backgroundColorA by animateColorAsState(backgroundColor, label = "bgAnimation")
    val borderWidthA by animateDpAsState(borderWidth, label = "borderWidthAnimation")
    val borderColorA by animateColorAsState(borderColor, label = "borderColorAnimation")
    val borderRadiusA by animateShapeAsState(borderRadius, label = "borderRadiusAnimation")

    return this.then(
        Modifier
            .alpha(alphaA)
            .clip(borderRadiusA)
            .background(backgroundColorA)
            .border(
                width = borderWidthA,
                color = borderColorA,
                shape = borderRadiusA
            )
    )
}

private fun Modifier.staticButtonModifier(
    alpha: Float,
    backgroundColor: Color,
    borderWidth: Dp,
    borderColor: Color,
    borderRadius: RoundedCornerShape
) = this.then(
    Modifier
        .alpha(alpha)
        .clip(borderRadius)
        .background(backgroundColor)
        .border(
            width = borderWidth,
            color = borderColor,
            shape = borderRadius
        )
)

internal fun getWidgetPosition(
    data: ObservableWidget,
    widgetSize: IntSize,
    screenSize: IntSize
): Offset {
    if (data.isEditingPos) return data.movingOffset
    return getWidgetPosition(data.internalRenderPosition, widgetSize, screenSize)
}

internal fun getWidgetPosition(
    position: ButtonPosition,
    widgetSize: IntSize,
    screenSize: IntSize
): Offset {
    return widgetPosition(
        xPercentage = position.xPercentage(),
        yPercentage = position.yPercentage(),
        widgetSize = widgetSize,
        screenSize = screenSize
    )
}

fun widgetPosition(
    @FloatRange(from = 0.0, to = 1.0)
    xPercentage: Float,
    @FloatRange(from = 0.0, to = 1.0)
    yPercentage: Float,
    widgetSize: IntSize,
    screenSize: IntSize
): Offset {
    val newX = (screenSize.width - widgetSize.width) * xPercentage
    val newY = (screenSize.height - widgetSize.height) * yPercentage
    return Offset(newX, newY)
}

internal fun Offset.toPercentagePosition(
    widgetSize: IntSize,
    screenSize: IntSize
): ButtonPosition {
    val availableWidth = (screenSize.width - widgetSize.width).toFloat()
    val availableHeight = (screenSize.height - widgetSize.height).toFloat()

    val xPercent = if (availableWidth > 0) x / availableWidth else 0f
    val yPercent = if (availableHeight > 0) y / availableHeight else 0f

    val x = (xPercent * 10000).roundToInt().coerceIn(0, 10000)
    val y = (yPercent * 10000).roundToInt().coerceIn(0, 10000)
    return ButtonPosition(x, y)
}

internal fun calculateMinDistanceBetweenRects(
    rect1Left: Float, rect1Top: Float, rect1Right: Float, rect1Bottom: Float,
    rect2Left: Float, rect2Top: Float, rect2Right: Float, rect2Bottom: Float
): Float {

    if (rect1Right >= rect2Left && rect1Left <= rect2Right &&
        rect1Bottom >= rect2Top && rect1Top <= rect2Bottom) {
        return 0f
    }

    val horizontalDistance = if (rect1Right < rect2Left) {
        rect2Left - rect1Right
    } else if (rect1Left > rect2Right) {
        rect1Left - rect2Right
    } else {
        0f
    }

    val verticalDistance = if (rect1Bottom < rect2Top) {
        rect2Top - rect1Bottom
    } else if (rect1Top > rect2Bottom) {
        rect1Top - rect2Bottom
    } else {
        0f
    }

    return if (horizontalDistance > 0 && verticalDistance > 0) {

        sqrt(horizontalDistance * horizontalDistance + verticalDistance * verticalDistance)
    } else {

        max(horizontalDistance, verticalDistance)
    }
}
