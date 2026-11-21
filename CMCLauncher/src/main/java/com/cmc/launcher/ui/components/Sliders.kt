package com.cmc.launcher.ui.components

import androidx.annotation.IntRange
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat

@Composable
private fun defaultSliderColors(): SliderColors = SliderDefaults.colors()

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
    val label = remember(value, toInt, suffix, valueText) {
        val core = valueText?.invoke(value)
            ?: if (toInt) value.toInt().toString() else formatter.format(value)
        if (suffix != null) "$core$suffix" else core
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            appendContent()
            Spacer(Modifier.weight(1f))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 2.dp)
            )
        }
        Slider(
            modifier = Modifier.fillMaxWidth(),
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = defaultSliderColors()
        )
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
    colors: SliderColors = defaultSliderColors()
) {
    Slider(
        modifier = modifier.fillMaxWidth(),
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        colors = colors
    )
}
