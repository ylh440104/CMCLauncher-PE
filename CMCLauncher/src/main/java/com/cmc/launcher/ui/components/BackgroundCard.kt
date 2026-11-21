package com.cmc.launcher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.ui.screens.content.elements.backgroundGlass
import com.cmc.launcher.ui.theme.CardShape
import com.cmc.launcher.ui.theme.cardColor
import com.cmc.launcher.ui.theme.cardTitleColor
import com.cmc.launcher.ui.theme.onCardColor

@Composable
private fun flatCardElevation() = CardDefaults.cardElevation(
    defaultElevation = 0.dp, pressedElevation = 0.dp, focusedElevation = 0.dp,
    hoveredElevation = 0.dp, draggedElevation = 0.dp, disabledElevation = 0.dp
)

@Composable
private fun cardOutline() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

@Composable
fun BackgroundCard(
    modifier: Modifier = Modifier,
    influencedByBackground: Boolean = true,
    shape: Shape = CardShape,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = cardColor(influencedByBackground), contentColor = onCardColor()
    ),
    elevation: CardElevation = flatCardElevation(),
    blur: Int = AllSettings.backgroundBlur.state,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border ?: cardOutline()) {
        Column(modifier = Modifier.backgroundGlass(blur, colors.containerColor, influencedByBackground), content = content)
    }
}

@Composable
fun BackgroundCard(
    modifier: Modifier = Modifier,
    influencedByBackground: Boolean = true,
    shape: Shape = CardShape,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = cardColor(influencedByBackground), contentColor = onCardColor(),
        disabledContainerColor = cardColor(influencedByBackground)
    ),
    elevation: CardElevation = flatCardElevation(),
    blur: Int = AllSettings.backgroundBlur.state,
    border: BorderStroke? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable @UiComposable ColumnScope.() -> Unit
) {
    Card(
        onClick = onClick, modifier = modifier, enabled = enabled, shape = shape,
        colors = colors, elevation = elevation, border = border ?: cardOutline()
    ) {
        val tint = if (enabled) colors.containerColor else colors.disabledContainerColor
        Column(modifier = Modifier.backgroundGlass(blur, tint, influencedByBackground), content = content)
    }
}

@Composable
fun CardTitleLayout(
    modifier: Modifier = Modifier,
    influencedByBackground: Boolean = true,
    alpha: Float = 0.5f,
    color: Color = influencedByBackgroundColor(cardTitleColor(alpha), enabled = influencedByBackground),
    contentColor: Color = onCardColor(),
    blur: Int = AllSettings.backgroundBlur.state,
    content: @Composable @UiComposable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        Surface(modifier = Modifier.fillMaxWidth(), color = color, contentColor = contentColor) {
            Column(modifier = Modifier.backgroundGlass(blur, color, influencedByBackground), content = content)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}
