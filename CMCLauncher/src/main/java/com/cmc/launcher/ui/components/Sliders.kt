package com.cmc.launcher.ui.components

import androidx.annotation.IntRange
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cmc.launcher.R
import com.cmc.launcher.ui.screens.content.elements.DisabledAlpha
import com.cmc.launcher.utils.math.addBigDecimal
import com.cmc.launcher.utils.math.subtractBigDecimal
import java.text.DecimalFormat

@Composable
fun SimpleTextSlider(
    modifier: Modifier = Modifier,
    shorter: Boolean = false,
    value: Float,
    decimalFormat: String = "#0.00",
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    toInt: Boolean = false,
    suffix: String? = null,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    onTextClick: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    fineTuningControl: Boolean = false,
    fineTuningStep: Float = 0.5f,
    valueText: ((Float) -> String)? = null,
    appendContent: @Composable () -> Unit = {}
) {
    val formatter = remember(decimalFormat) { DecimalFormat(decimalFormat) }
    fun getTextString(value: Float) = valueText?.invoke(value) ?: "${if (toInt) {
        value.toInt()
    } else {
        formatter.format(value)
    }}"

    fun changeValue(newValue: Float, finished: Boolean) {
        onValueChange(newValue)
        if (finished) onValueChangeFinished?.invoke()
    }

    LaunchedEffect(Unit) {

        if (value !in valueRange) {
            val newValue = value.coerceIn(valueRange)

            changeValue(newValue, true)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (shorter) {
            IndicatorSlider(
                value = value,
                enabled = enabled,
                onValueChange = { changeValue(it, false) },
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f)
            )
        } else {
            Slider(
                value = value,
                enabled = enabled,
                onValueChange = { changeValue(it, false) },
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f)
            )
        }
        Surface(
            modifier = Modifier
                .alpha(alpha = if (enabled) 1f else DisabledAlpha)
                .padding(start = 12.dp)
                .align(Alignment.CenterVertically),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Row(
                modifier = Modifier.padding(PaddingValues(horizontal = 8.dp, vertical = 4.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .then(
                            if (onTextClick != null) {
                                Modifier.clickable(enabled = enabled, onClick = onTextClick)
                            } else Modifier
                        )
                ) {
                    Text(
                        text = getTextString(value),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    suffix?.let { text ->
                        Text(text = text)
                    }
                }
                if (fineTuningControl) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        enabled = enabled,
                        modifier = Modifier.size(26.dp),
                        onClick = {
                            val newValue = value.subtractBigDecimal(fineTuningStep)
                            if (newValue <= valueRange.start) {
                                changeValue(valueRange.start, true)
                            } else {
                                changeValue(newValue, true)
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_left_rounded),
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        enabled = enabled,
                        modifier = Modifier.size(26.dp),
                        onClick = {
                            val newValue = value.addBigDecimal(fineTuningStep)
                            if (newValue >= valueRange.endInclusive) {
                                changeValue(valueRange.endInclusive, true)
                            } else {
                                changeValue(newValue, true)
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_right_rounded),
                            contentDescription = null
                        )
                    }
                }
            }
        }
        appendContent()
    }
}

@Composable
fun IndicatorSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    @IntRange(from = 0) steps: Int = 0,
    colors: SliderColors = SliderDefaults.colors()
) {

    val sliderTopCut = with(LocalDensity.current) { 8.dp.toPx().toInt() }

    val sliderBottomCut = with(LocalDensity.current) { 6.dp.toPx().toInt() }
    Layout(
        modifier = modifier,
        content = {
            val state = rememberSliderState(
                value = value,
                steps = steps,
                trackRange = valueRange,
            )
            Slider(
                state = state,
                onValueChange = { value ->
                    onValueChange(value)
                    state.value = value
                },
                enabled = enabled,
                onValueChangeFinished = onValueChangeFinished,
                interactionSource = interactionSource,
                colors = colors,
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = interactionSource,
                        colors = colors,
                        enabled = enabled,
                        thumbSize = DpSize(4.0.dp, 18.5.dp)
                    )
                }
            )
        }
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints)
        val newHeight = (placeable.height - sliderTopCut - sliderBottomCut).coerceAtLeast(0)
        layout(placeable.width, newHeight) {
            placeable.place(0, -sliderTopCut)
        }
    }
}
