package com.cmc.launcher.ui.control.mouse

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cmc.launcher.R
import com.cmc.launcher.bridge.CursorShape
import com.cmc.launcher.setting.unit.ParcelableSettingUnit
import com.cmc.launcher.ui.components.rememberDialogMaxHeight
import com.cmc.launcher.ui.screens.main.control_editor.InfoLayoutSliderItem
import com.cmc.launcher.ui.theme.cardColor
import com.cmc.launcher.ui.theme.onCardColor
import com.cmc.launcher.utils.file.ifExists

@Composable
fun MouseHotspotEditorDialog(
    hotspot: ParcelableSettingUnit<CursorHotspot>,
    cursorShape: CursorShape,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .heightIn(max = rememberDialogMaxHeight())
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(all = 3.dp)
                    .heightIn(max = (maxHeight - 6.dp).coerceAtMost(rememberDialogMaxHeight()))
                    .wrapContentHeight(),
                shadowElevation = 3.dp,
                shape = MaterialTheme.shapes.extraLarge,
                color = cardColor(false),
                contentColor = onCardColor(),
            ) {
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .padding(all = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MouseHotspotPreview(
                        xPercent = hotspot.state.xPercent,
                        yPercent = hotspot.state.yPercent,
                        cursorShape = cursorShape,
                        mouseSize = 68.dp
                    )

                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight(0.5f)
                            .padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        InfoLayoutSliderItem(
                            title = stringResource(R.string.settings_control_mouse_pointer_hotspot_x_percent),
                            value = hotspot.state.xPercent.toFloat(),
                            onValueChange = { value ->
                                hotspot.updateState(
                                    hotspot.state.copy(xPercent = value.toInt())
                                )
                            },
                            onValueChangeFinished = {
                                hotspot.save(hotspot.state)
                            },
                            decimalFormat = "#0",
                            suffix = "%",
                            valueRange = 0f..100f,
                            fineTuningStep = 1f
                        )

                        InfoLayoutSliderItem(
                            title = stringResource(R.string.settings_control_mouse_pointer_hotspot_y_percent),
                            value = hotspot.state.yPercent.toFloat(),
                            onValueChange = { value ->
                                hotspot.updateState(
                                    hotspot.state.copy(yPercent = value.toInt())
                                )
                            },
                            onValueChangeFinished = {
                                hotspot.save(hotspot.state)
                            },
                            decimalFormat = "#0",
                            suffix = "%",
                            valueRange = 0f..100f,
                            fineTuningStep = 1f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MouseHotspotPreview(
    xPercent: Int,
    yPercent: Int,
    cursorShape: CursorShape,
    modifier: Modifier = Modifier,
    mouseSize: Dp = 48.dp,
    dotSize: Dp = 8.dp
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {

        val infiniteTransition = rememberInfiniteTransition()
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Box(
            modifier = modifier.size(mouseSize)
        ) {

            MousePointer(
                cursorShape = cursorShape,
                mouseSize = mouseSize,
                mouseFile = getMouseFile(cursorShape).ifExists()
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha)
            ) {
                val radius = dotSize.toPx() / 2
                val x = size.width * xPercent / 100f
                val y = size.height * yPercent / 100f
                val center = Offset(x, y)

                drawCircle(
                    color = Color(0xFFC9350F),
                    radius = radius,
                    center = center
                )

                drawCircle(
                    color = Color.White,
                    radius = radius - 1.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}
