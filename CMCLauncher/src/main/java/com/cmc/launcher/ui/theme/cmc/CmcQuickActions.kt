package com.cmc.launcher.ui.theme.cmc

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cmc.launcher.ui.theme.CardShape
import com.cmc.launcher.ui.theme.Space
import com.cmc.launcher.ui.theme.cardColor

data class CmcAction(
    val label: String,
    val caption: String = "",
    val accent: Color = Color.Unspecified,
    @DrawableRes val iconRes: Int? = null,
    val onClick: () -> Unit
) {
    constructor(label: String, caption: String, accent: Color, onClick: () -> Unit) :
        this(label, caption, accent, null, onClick)
}

@Composable
fun CmcQuickActionRow(actions: List<CmcAction>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = cardColor(),
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions.forEachIndexed { index, action ->
                if (index > 0) VerticalDivider(modifier = Modifier.height(24.dp), color = scheme.outlineVariant.copy(alpha = 0.55f))
                CmcQuickActionCard(action, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun CmcQuickActionCard(action: CmcAction, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = action.onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.xs, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.xs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        action.iconRes?.let {
            Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(20.dp),
                tint = action.accent.takeOrElse { scheme.onSurfaceVariant })
        }
        Text(action.label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface,
            maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        if (action.caption.isNotBlank()) {
            Text(action.caption, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CmcStatStrip(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        items.forEach { (label, value) ->
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
