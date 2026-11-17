package com.movtery.layer_controller

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.JOYSTICK_MIN_SIZE_DP
import com.movtery.layer_controller.data.JOYSTICK_MIN_SIZE_PERCENTAGE
import com.movtery.layer_controller.data.MAX_SIZE_PERCENTAGE
import com.movtery.layer_controller.data.MIN_SIZE_DP
import com.movtery.layer_controller.data.MIN_SIZE_PERCENTAGE
import com.movtery.layer_controller.layout.JoystickWidgetRenderer
import com.movtery.layer_controller.layout.TextButton
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.observable.ObservableJoystickData
import com.movtery.layer_controller.observable.ObservableJoystickStyle
import com.movtery.layer_controller.observable.ObservableWidget
import com.movtery.layer_controller.utils.getWidgetPosition
import com.movtery.layer_controller.utils.snap.GuideLine
import com.movtery.layer_controller.utils.snap.LineDirection
import com.movtery.layer_controller.utils.snap.SnapMode
import com.movtery.layer_controller.utils.toPercentagePosition
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ControlEditorLayer(
    observedLayout: ObservableControlLayout,
    selectedWidget: ObservableWidget?,
    onButtonTap: (data: ObservableWidget, layer: ObservableControlLayer) -> Unit,
    onBackgroundClick: () -> Unit,
    floatingButtons: @Composable RowScope.() -> Unit,
    enableSnap: Boolean,
    snapInAllLayers: Boolean,
    snapMode: SnapMode,
    focusedLayer: ObservableControlLayer? = null,
    isDark: Boolean = isSystemInDarkTheme(),
    localSnapRange: Dp = 20.dp,
    snapThresholdValue: Dp = 4.dp
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val primaryColor = MaterialTheme.colorScheme.primary

        val layers by observedLayout.layers.collectAsStateWithLifecycle()
        val styles by observedLayout.styles.collectAsStateWithLifecycle()
        val joystickStyles by observedLayout.joystickStyles.collectAsStateWithLifecycle()

        val guideLines = remember { mutableStateMapOf<ObservableWidget, List<GuideLine>>() }

        val renderingLayers = when (focusedLayer) {
            null -> layers

                .filter { !it.editorHide }

                .reversed()

            else -> listOf(focusedLayer)
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {

            var resizingWidget by remember { mutableStateOf<ObservableWidget?>(null) }

            var dragTL by remember { mutableStateOf(Offset.Zero) }

            var dragBR by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onBackgroundClick
                    )
            )

            val density = LocalDensity.current
            val screenSize = remember(maxWidth, maxHeight) {
                with(density) {
                    IntSize(
                        width = maxWidth.roundToPx(),
                        height = maxHeight.roundToPx()
                    )
                }
            }

            val selectedWidgetBounds by remember(
                selectedWidget, resizingWidget, dragTL, dragBR, screenSize
            ) {
                derivedStateOf {
                    val widget = selectedWidget ?: return@derivedStateOf null
                    val widgetSize = widget.internalRenderSize
                    if (widgetSize == IntSize.Zero) return@derivedStateOf null

                    if (resizingWidget == widget) {
                        dragTL to dragBR
                    } else {
                        val position = getWidgetPosition(widget, widgetSize, screenSize)
                        position to Offset(position.x + widgetSize.width, position.y + widgetSize.height)
                    }
                }
            }

            ControlWidgetRenderer(
                screenSize = screenSize,
                isDark = isDark,
                renderingLayers = renderingLayers,
                styles = styles,
                joystickStyles = joystickStyles,
                enableSnap = enableSnap,
                snapInAllLayers = snapInAllLayers,
                snapMode = snapMode,
                localSnapRange = localSnapRange,
                snapThresholdValue = snapThresholdValue,
                onButtonTap = onButtonTap,
                drawLine = { data, line ->
                    guideLines[data] = line
                },
                onLineCancel = { data ->
                    guideLines.remove(data)
                }
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                guideLines.values.forEach { guidelines ->
                    guidelines.forEach { guideline ->
                        drawLine(
                            guideline = guideline,
                            color = primaryColor
                        )
                    }
                }

                selectedWidgetBounds?.let { (drawTL, drawBR) ->

                    val padding = 4.dp.toPx()
                    drawRect(
                        color = primaryColor,
                        topLeft = Offset(drawTL.x - padding, drawTL.y - padding),
                        size = Size(
                            (drawBR.x - drawTL.x) + padding * 2,
                            (drawBR.y - drawTL.y) + padding * 2
                        ),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            selectedWidget?.takeIf { widget ->

                widget.widgetSize.type != ButtonSize.Type.WrapContent
            }?.let { widget ->
                selectedWidgetBounds?.let { (drawTL, drawBR) ->

                    val isJoystick = widget is ObservableJoystickData
                    val effectiveMinDp = if (isJoystick) JOYSTICK_MIN_SIZE_DP else MIN_SIZE_DP
                    val minSizePx = with(density) { effectiveMinDp.dp.toPx() }
                    val effectiveMinPercentage = if (isJoystick) JOYSTICK_MIN_SIZE_PERCENTAGE else MIN_SIZE_PERCENTAGE
                    val minPercentageRatio = effectiveMinPercentage.toFloat() / MAX_SIZE_PERCENTAGE.toFloat()
                    val oldSize = widget.widgetSize

                    val (minWidth, maxWidth) = when (oldSize.type) {
                        ButtonSize.Type.Dp -> minSizePx to screenSize.width.toFloat()
                        ButtonSize.Type.Percentage -> {
                            val reference = if (oldSize.widthReference == ButtonSize.Reference.ScreenWidth) {
                                screenSize.width
                            } else {
                                screenSize.height
                            }
                            (reference * minPercentageRatio) to (reference * 1.0f)
                        }
                        else -> minSizePx to screenSize.width.toFloat()
                    }

                    val (minHeight, maxHeight) = when (oldSize.type) {
                        ButtonSize.Type.Dp -> minSizePx to screenSize.height.toFloat()
                        ButtonSize.Type.Percentage -> {
                            val reference = if (oldSize.heightReference == ButtonSize.Reference.ScreenWidth) {
                                screenSize.width
                            } else {
                                screenSize.height
                            }
                            (reference * minPercentageRatio) to (reference * 1.0f)
                        }
                        else -> minSizePx to screenSize.height.toFloat()
                    }

                    val updateSizeAndPos = { newTopLeft: Offset, newSize: IntSize ->
                        val newPosPercentage = newTopLeft.toPercentagePosition(newSize, screenSize)
                        widget.putRenderPosition(newPosPercentage)

                        val oldSize = widget.widgetSize
                        val newWidgetSize = when (oldSize.type) {
                            ButtonSize.Type.Dp -> {
                                oldSize.copy(
                                    widthDp = with(density) { newSize.width.toDp().value }.coerceAtLeast(effectiveMinDp),
                                    heightDp = with(density) { newSize.height.toDp().value }.coerceAtLeast(effectiveMinDp)
                                )
                            }

                            ButtonSize.Type.Percentage -> {
                                val widthRef = when (oldSize.widthReference) {
                                    ButtonSize.Reference.ScreenWidth -> screenSize.width
                                    ButtonSize.Reference.ScreenHeight -> screenSize.height
                                }
                                val heightRef = when (oldSize.heightReference) {
                                    ButtonSize.Reference.ScreenWidth -> screenSize.width
                                    ButtonSize.Reference.ScreenHeight -> screenSize.height
                                }
                                oldSize.copy(
                                    widthPercentage = (newSize.width.toFloat() / widthRef * MAX_SIZE_PERCENTAGE)
                                        .roundToInt()
                                        .coerceIn(effectiveMinPercentage, MAX_SIZE_PERCENTAGE),
                                    heightPercentage = (newSize.height.toFloat() / heightRef * MAX_SIZE_PERCENTAGE)
                                        .roundToInt()
                                        .coerceIn(effectiveMinPercentage, MAX_SIZE_PERCENTAGE)
                                )
                            }
                            else -> oldSize
                        }
                        widget.putWidgetSize(newWidgetSize)
                    }

                    @Composable
                    fun ResizeHandle(
                        isTopLeft: Boolean,
                        currentPos: Offset,
                        touchSize: Dp = 30.dp,
                        visualSize: Dp = 14.dp
                    ) {
                        var activeHandleCount by remember { mutableIntStateOf(0) }
                        val touchSizePx = with(density) { touchSize.toPx() }
                        val visualSizePx = with(density) { visualSize.toPx() }

                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (currentPos.x - touchSizePx / 2).roundToInt(),
                                        (currentPos.y - touchSizePx / 2).roundToInt()
                                    )
                                }
                                .size(touchSize)
                                .drawBehind {
                                    drawCircle(
                                        color = primaryColor,
                                        radius = visualSizePx / 2,
                                        center = center
                                    )
                                }
                                .pointerInput(widget, screenSize, minWidth, maxWidth, minHeight, maxHeight) {
                                    detectDragGestures(
                                        onDragStart = {
                                            if (activeHandleCount == 0) {
                                                val currentWidgetSize = widget.internalRenderSize
                                                val pos = getWidgetPosition(widget, currentWidgetSize, screenSize)
                                                dragTL = pos
                                                dragBR = Offset(pos.x + currentWidgetSize.width, pos.y + currentWidgetSize.height)
                                            }
                                            activeHandleCount++
                                            resizingWidget = widget
                                            widget.movingOffset = dragTL
                                            widget.isEditingPos = true
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (isTopLeft) {
                                                var newTL = dragTL + dragAmount
                                                if (widget is ObservableJoystickData) {
                                                    val oldSide = dragBR.x - dragTL.x
                                                    val distX = dragBR.x - newTL.x
                                                    val distY = dragBR.y - newTL.y
                                                    val deltaX = distX - oldSide
                                                    val deltaY = distY - oldSide
                                                    val side = (if (abs(deltaX) > abs(deltaY)) oldSide + deltaX else oldSide + deltaY)
                                                        .coerceIn(minWidth, minOf(maxWidth, maxHeight, dragBR.x, dragBR.y))
                                                    newTL = Offset(dragBR.x - side, dragBR.y - side)
                                                }
                                                val finalTL = Offset(
                                                    newTL.x.coerceIn(maxOf(0f, dragBR.x - maxWidth), dragBR.x - minWidth),
                                                    newTL.y.coerceIn(maxOf(0f, dragBR.y - maxHeight), dragBR.y - minHeight)
                                                )
                                                dragTL = finalTL
                                                widget.movingOffset = finalTL
                                                val finalSize = IntSize(
                                                    (dragBR.x - finalTL.x).roundToInt(),
                                                    (dragBR.y - finalTL.y).roundToInt()
                                                )
                                                updateSizeAndPos(finalTL, finalSize)
                                            } else {
                                                var newBR = dragBR + dragAmount
                                                if (widget is ObservableJoystickData) {
                                                    val oldSide = dragBR.x - dragTL.x
                                                    val distX = newBR.x - dragTL.x
                                                    val distY = newBR.y - dragTL.y
                                                    val deltaX = distX - oldSide
                                                    val deltaY = distY - oldSide
                                                    val side = (if (abs(deltaX) > abs(deltaY)) oldSide + deltaX else oldSide + deltaY)
                                                        .coerceIn(minWidth, minOf(maxWidth, maxHeight, screenSize.width - dragTL.x, screenSize.height - dragTL.y))
                                                    newBR = Offset(dragTL.x + side, dragTL.y + side)
                                                }
                                                val finalBR = Offset(
                                                    newBR.x.coerceIn(dragTL.x + minWidth, minOf(screenSize.width.toFloat(), dragTL.x + maxWidth)),
                                                    newBR.y.coerceIn(dragTL.y + minHeight, minOf(screenSize.height.toFloat(), dragTL.y + maxHeight))
                                                )
                                                dragBR = finalBR
                                                val finalSize = IntSize(
                                                    (finalBR.x - dragTL.x).roundToInt(),
                                                    (finalBR.y - dragTL.y).roundToInt()
                                                )
                                                updateSizeAndPos(dragTL, finalSize)
                                            }
                                        },
                                        onDragEnd = {
                                            activeHandleCount = (activeHandleCount - 1).coerceAtLeast(0)
                                            if (activeHandleCount == 0) {
                                                resizingWidget = null
                                                widget.isEditingPos = false
                                            }
                                        },
                                        onDragCancel = {
                                            activeHandleCount = (activeHandleCount - 1).coerceAtLeast(0)
                                            if (activeHandleCount == 0) {
                                                resizingWidget = null
                                                widget.isEditingPos = false
                                            }
                                        }
                                    )
                                }
                        )
                    }

                    ResizeHandle(isTopLeft = true, currentPos = drawTL)

                    ResizeHandle(isTopLeft = false, currentPos = drawBR)
                }
            }

            selectedWidget?.let {
                selectedWidgetBounds?.let { (drawTL, drawBR) ->
                    var barSize by remember { mutableStateOf(IntSize.Zero) }

                    val centerX = (drawTL.x + drawBR.x) / 2
                    val targetY = drawBR.y + with(density) { 8.dp.toPx() }

                    val xPos = (centerX - barSize.width / 2)
                        .coerceIn(0f, maxOf(0f, screenSize.width.toFloat() - barSize.width))
                    val yPos = targetY
                        .coerceAtMost(maxOf(0f, screenSize.height.toFloat() - barSize.height))

                    Row(
                        modifier = Modifier
                            .onSizeChanged { barSize = it }

                            .alpha(if (barSize != IntSize.Zero) 1f else 0f)
                            .offset {
                                IntOffset(xPos.roundToInt(), yPos.roundToInt())
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        content = floatingButtons
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawLine(
    guideline: GuideLine,
    color: Color,
    strokeWidth: Float = 2f
) {
    when (guideline.direction) {
        LineDirection.Vertical -> {
            drawLine(
                color = color,
                start = Offset(guideline.coordinate, 0f),
                end = Offset(guideline.coordinate, size.height),
                strokeWidth = strokeWidth
            )
        }
        LineDirection.Horizontal -> {
            drawLine(
                color = color,
                start = Offset(0f, guideline.coordinate),
                end = Offset(size.width, guideline.coordinate),
                strokeWidth = strokeWidth
            )
        }
    }
}

@Composable
private fun ControlWidgetRenderer(
    screenSize: IntSize,
    isDark: Boolean,
    renderingLayers: List<ObservableControlLayer>,
    styles: List<ObservableButtonStyle>,
    joystickStyles: List<ObservableJoystickStyle>,
    enableSnap: Boolean,
    snapInAllLayers: Boolean,
    snapMode: SnapMode,
    localSnapRange: Dp,
    snapThresholdValue: Dp,
    onButtonTap: (data: ObservableWidget, layer: ObservableControlLayer) -> Unit,
    drawLine: (ObservableWidget, List<GuideLine>) -> Unit,
    onLineCancel: (ObservableWidget) -> Unit
) {
    val allWidgetsMap = remember { mutableStateMapOf<ObservableControlLayer, List<ObservableWidget>>() }
    val snapInAllLayers1 by rememberUpdatedState(snapInAllLayers)

    @Composable
    fun RenderWidget(
        data: ObservableWidget,
        layer: ObservableControlLayer
    ) {
        TextButton(
            isEditMode = true,
            data = data,
            allStyles = styles,
            screenSize = screenSize,
            isDark = isDark,
            enableSnap = enableSnap,
            snapMode = snapMode,
            localSnapRange = localSnapRange,
            getOtherWidgets = {
                allWidgetsMap
                    .filter { (layer1, _) ->
                        snapInAllLayers1 || layer1 == layer
                    }
                    .values.flatten().filter { it != data }
            },
            snapThresholdValue = snapThresholdValue,
            drawLine = drawLine,
            onLineCancel = onLineCancel,
            onTapInEditMode = {
                onButtonTap(data, layer)
            }
        )
    }

    Layout(
        content = {

            renderingLayers.forEach { layer ->
                val normalButtons by layer.normalButtons.collectAsStateWithLifecycle()
                val textBoxes by layer.textBoxes.collectAsStateWithLifecycle()
                val joystickButtons by layer.joystickButtons.collectAsStateWithLifecycle()

                val widgetsInLayer = normalButtons + textBoxes + joystickButtons
                allWidgetsMap[layer] = widgetsInLayer

                textBoxes.forEach { data ->
                    RenderWidget(data, layer)
                }

                normalButtons.forEach { data ->
                    RenderWidget(data, layer)
                }

                joystickButtons.forEach { data ->
                    JoystickWidgetRenderer(
                        data = data,
                        joystickStyles = joystickStyles,
                        screenSize = screenSize,
                        isDark = isDark,
                        isEditMode = true,
                        enableSnap = enableSnap,
                        snapMode = snapMode,
                        localSnapRange = localSnapRange,
                        getOtherWidgets = {
                            allWidgetsMap
                                .filter { (layer1, _) ->
                                    snapInAllLayers1 || layer1 == layer
                                }
                                .values.flatten().filter { it != data }
                        },
                        snapThresholdValue = snapThresholdValue,
                        drawLine = drawLine,
                        onLineCancel = onLineCancel,
                        onTapInEditMode = {
                            onButtonTap(data, layer)
                        }
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints)
        }

        var index = 0
        fun ObservableWidget.putSize() {
            if (index < placeables.size) {
                val placeable = placeables[index]
                this.internalRenderSize = IntSize(placeable.width, placeable.height)
                index++
            }
        }

        renderingLayers.fastForEach { layer ->
            layer.textBoxes.value.fastForEach { it.putSize() }
            layer.normalButtons.value.fastForEach { it.putSize() }
            layer.joystickButtons.value.fastForEach { it.putSize() }
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            var placeableIndex = 0
            fun ObservableWidget.place() {
                if (placeableIndex < placeables.size) {
                    val placeable = placeables[placeableIndex]
                    val position = getWidgetPosition(
                        data = this,
                        widgetSize = IntSize(placeable.width, placeable.height),
                        screenSize = screenSize
                    )
                    placeable.place(position.x.toInt(), position.y.toInt())
                    placeableIndex++
                }
            }

            renderingLayers.fastForEach { layer ->
                layer.textBoxes.value.fastForEach { it.place() }
                layer.normalButtons.value.fastForEach { it.place() }
                layer.joystickButtons.value.fastForEach { it.place() }
            }
        }
    }
}
