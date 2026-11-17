package com.cmc.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cmc.launcher.R
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.ui.screens.content.settings.layouts.CardPosition
import com.cmc.launcher.ui.screens.content.settings.layouts.rememberSettingsCardShape

@Composable
fun WarningCard(
    title: String,
    text: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable (innerModifier: Modifier) -> Unit) = @Composable { innerModifier ->
        Icon(
            modifier = innerModifier,
            painter = painterResource(R.drawable.ic_warning_filled),
            contentDescription = title
        )
    },
    position: CardPosition = CardPosition.Single,
    outerShapeSize: Dp = 12.dp,
    innerShapeSize: Dp = 4.dp,
    influencedByBackground: Boolean = true,
    blur: Int = AllSettings.backgroundBlur.state,
    containerColor: Color = influencedByBackgroundColor(
        color = MaterialTheme.colorScheme.secondaryContainer,
        enabled = influencedByBackground
    ),
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    val cardShape = rememberSettingsCardShape(
        position = position,
        outerShape = outerShapeSize,
        innerShape = innerShapeSize
    )

    BackgroundCard(
        modifier = modifier,
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        blur = blur,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .padding(vertical = 2.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                content = text
            )
        }
    }
}
