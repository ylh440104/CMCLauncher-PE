package com.cmc.launcher.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cmc.launcher.ui.theme.Alpha
import com.cmc.launcher.ui.theme.CardShape
import com.cmc.launcher.ui.theme.PanelShape
import com.cmc.launcher.ui.theme.Radius
import com.cmc.launcher.ui.theme.Size
import com.cmc.launcher.ui.theme.Space

@Composable
fun hairlineColor(): Color = MaterialTheme.colorScheme.outline.copy(alpha = Alpha.faint)

@Composable
fun surfaceColor(elevated: Boolean): Color =
    if (elevated) MaterialTheme.colorScheme.surfaceContainerHigh
    else MaterialTheme.colorScheme.surfaceContainer

@Composable
fun CmcPanel(
    modifier: Modifier = Modifier,
    shape: Shape = PanelShape,
    tonal: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (tonal) surfaceColor(true) else MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(Size.stroke, hairlineColor(), shape)
            .padding(Space.cardInner),
        content = content
    )
}

@Composable
fun CmcCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaceColor(true))
            .border(Size.stroke, hairlineColor(), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Space.cardInner),
        content = content
    )
}

@Composable
fun CmcSection(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Space.sectionGap, bottom = Space.sm),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (caption != null) {
                Text(
                    modifier = Modifier.padding(top = Space.xxs),
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun CmcDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Size.stroke)
            .background(hairlineColor())
    )
}

@Composable
fun CmcRow(
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    title: String,
    summary: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    summaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(Size.iconSm),
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(Space.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (summary != null) {
                Text(
                    modifier = Modifier.padding(top = Space.xxs),
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = summaryColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                content = trailing
            )
        }
    }
}

@Composable
fun CmcTag(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary.copy(alpha = Alpha.soft),
    content: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.tiny))
            .background(container)
            .padding(horizontal = Space.sm, vertical = Space.xxs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1
        )
    }
}

@Composable
fun CmcSwatch(
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(Size.swatch)
            .clip(RoundedCornerShape(Radius.pill))
            .background(color)
            .border(
                width = if (selected) Size.strokeBold else Size.stroke,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else hairlineColor(),
                shape = RoundedCornerShape(Radius.pill)
            )
            .clickable(onClick = onClick)
    )
}

@Composable
fun CmcDot(color: Color, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(Radius.pill))
            .background(color)
    )
}