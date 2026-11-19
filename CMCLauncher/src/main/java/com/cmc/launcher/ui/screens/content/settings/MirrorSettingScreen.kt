package com.cmc.launcher.ui.screens.content.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cmc.launcher.R
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.setting.enums.MirrorSourceType
import com.cmc.launcher.ui.base.BaseScreen
import com.cmc.launcher.ui.components.animated
import com.cmc.launcher.ui.components.rememberAnimatedContext
import com.cmc.launcher.ui.screens.NestedNavKey
import com.cmc.launcher.ui.screens.NormalNavKey
import com.cmc.launcher.ui.screens.TitledNavKey
import com.cmc.launcher.viewmodel.LocalBackgroundViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.cmc.launcher.ui.theme.ColorThemeType

private data class MirrorOption(
    val type: MirrorSourceType,
    val title: String,
    val caption: String,
    val icon: Int
)

private val mirrorOptions = listOf(
    MirrorOption(
        type = MirrorSourceType.AUTO,
        title = "自动选择",
        caption = "按网络环境自动挑选最快的线路",
        icon = R.drawable.ic_autorenew
    ),
    MirrorOption(
        type = MirrorSourceType.MIRROR,
        title = "镜像优先",
        caption = "BMCLAPI 加速，国内下载更快",
        icon = R.drawable.ic_rocket_launch_filled
    ),
    MirrorOption(
        type = MirrorSourceType.OFFICIAL,
        title = "官方源",
        caption = "直连 Mojang 官方服务器",
        icon = R.drawable.ic_public
    )
)

@Composable
fun MirrorSettingScreen(
    key: NestedNavKey.Settings,
    settingsScreenKey: TitledNavKey?,
    mainScreenKey: TitledNavKey?
) {
    BaseScreen(
        Triple(key, mainScreenKey, false),
        Triple(NormalNavKey.Settings.Mirror, settingsScreenKey, false)
    ) { isVisible ->
        val ctx = rememberAnimatedContext(isVisible = isVisible)
        val listState = rememberLazyListState()
        val current = AllSettings.gameDownloadSource.state

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .animated(ctx),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 6.dp)) {
                    Text(
                        text = "下载镜像",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "选择游戏与依赖的下载线路",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(mirrorOptions) { option ->
                MirrorCard(
                    option = option,
                    selected = current == option.type,
                    onSelect = { AllSettings.gameDownloadSource.save(option.type) }
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "说明",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            modifier = Modifier.padding(top = 4.dp),
                            text = "镜像优先会先尝试国内加速节点，失败时自动回落到官方源；官方源适合海外网络环境。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 2.dp)) {
                    Text(
                        text = "配色",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "主题色板与自定义色调",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                ThemeColorCard()
            }
            item {
                Column(modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 2.dp)) {
                    Text(
                        text = "外观",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "毛玻璃与液态玻璃质感",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                modifier = Modifier.size(22.dp),
                                painter = painterResource(R.drawable.ic_blur_circular_filled),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    text = "模糊强度",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "玻璃背后的虚化程度",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${AllSettings.backgroundBlur.state}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            value = AllSettings.backgroundBlur.state.toFloat(),
                            onValueChange = { AllSettings.backgroundBlur.updateState(it.toInt()) },
                            onValueChangeFinished = {
                                AllSettings.backgroundBlur.save(AllSettings.backgroundBlur.state)
                            },
                            valueRange = 0f..40f,
                            steps = 39
                        )
                    }
                }
            }

            item {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val backgroundViewModel = LocalBackgroundViewModel.current
                val backgroundValid = backgroundViewModel?.isValid == true
                val isVideo = backgroundViewModel?.isVideo == true

                val picker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null && backgroundViewModel != null) {
                        scope.launch {
                            runCatching { backgroundViewModel.import(context, uri) }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                modifier = Modifier.size(22.dp),
                                painter = painterResource(R.drawable.ic_image_outlined),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    text = "自定义背景",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (backgroundValid) {
                                        if (isVideo) "当前：视频背景" else "当前：图片背景"
                                    } else "未设置，使用默认背景",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                onClick = {
                                    picker.launch(
                                        arrayOf(
                                            "image/*",
                                            "video/*",
                                            "application/octet-stream"
                                        )
                                    )
                                }
                            ) {
                                Text(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    text = "选择图片/视频",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (backgroundValid) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    onClick = {
                                        backgroundViewModel?.let { vm ->
                                            scope.launch { vm.delete() }
                                        }
                                    }
                                ) {
                                    Text(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        text = "移除背景",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                modifier = Modifier.size(22.dp),
                                painter = painterResource(R.drawable.ic_blur_circular_filled),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    text = "背景不透明度",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "调低可让雨滴更清晰",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${AllSettings.launcherBackgroundOpacity.state}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            value = AllSettings.launcherBackgroundOpacity.state.toFloat(),
                            onValueChange = {
                                AllSettings.launcherBackgroundOpacity.updateState(it.toInt())
                            },
                            onValueChangeFinished = {
                                AllSettings.launcherBackgroundOpacity.save(
                                    AllSettings.launcherBackgroundOpacity.state
                                )
                            },
                            valueRange = 20f..100f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MirrorCard(
    option: MirrorOption,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                modifier = Modifier.size(22.dp),
                painter = painterResource(option.icon),
                contentDescription = null,
                tint = content
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = content
                )
                Text(
                    text = option.caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.75f)
                )
            }
            RadioButton(
                selected = selected,
                onClick = onSelect
            )
        }
    }
}

@Composable
private fun ThemeColorCard() {
    val current = AllSettings.launcherColorTheme.state
    val customColor = Color(AllSettings.launcherCustomColor.state)
    val customStyle = AllSettings.launcherCustomPaletteStyle.state

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    painter = painterResource(R.drawable.ic_format_paint_outlined),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = "主题色板",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = current.label(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    ColorThemeType.DYNAMIC,
                    ColorThemeType.EMBERMIRE,
                    ColorThemeType.VELVET_ROSE,
                    ColorThemeType.MISTWAVE
                ).forEach { type ->
                    ColorSwatch(
                        title = type.shortLabel(),
                        color = type.previewColor(),
                        selected = current == type,
                        modifier = Modifier.weight(1f)
                    ) {
                        AllSettings.launcherColorTheme.updateState(type)
                        AllSettings.launcherColorTheme.save(type)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    ColorThemeType.GLACIER,
                    ColorThemeType.VERDANTFIELD,
                    ColorThemeType.URBAN_ASH,
                    ColorThemeType.CUSTOM
                ).forEach { type ->
                    ColorSwatch(
                        title = type.shortLabel(),
                        color = if (type == ColorThemeType.CUSTOM) customColor else type.previewColor(),
                        selected = current == type,
                        modifier = Modifier.weight(1f)
                    ) {
                        AllSettings.launcherColorTheme.updateState(type)
                        AllSettings.launcherColorTheme.save(type)
                    }
                }
            }

            if (current == ColorThemeType.CUSTOM) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "种子色",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(customColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }

                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    value = ((AllSettings.launcherCustomColor.state shr 16) and 0xFF) / 255f,
                    onValueChange = { v ->
                        val base = AllSettings.launcherCustomColor.state
                        val r = (v * 255f).toInt().coerceIn(0, 255)
                        val c = (0xFF shl 24) or (r shl 16) or (base and 0x00FFFF)
                        AllSettings.launcherCustomColor.updateState(c)
                    },
                    onValueChangeFinished = {
                        AllSettings.launcherCustomColor.save(AllSettings.launcherCustomColor.state)
                    },
                    valueRange = 0f..1f
                )
                Slider(
                    modifier = Modifier.fillMaxWidth(),
                    value = ((AllSettings.launcherCustomColor.state shr 8) and 0xFF) / 255f,
                    onValueChange = { v ->
                        val base = AllSettings.launcherCustomColor.state
                        val g = (v * 255f).toInt().coerceIn(0, 255)
                        val c = (0xFF shl 24) or (base and 0x00FF0000) or (g shl 8) or (base and 0x000000FF)
                        AllSettings.launcherCustomColor.updateState(c)
                    },
                    onValueChangeFinished = {
                        AllSettings.launcherCustomColor.save(AllSettings.launcherCustomColor.state)
                    },
                    valueRange = 0f..1f
                )
                Slider(
                    modifier = Modifier.fillMaxWidth(),
                    value = (AllSettings.launcherCustomColor.state and 0xFF) / 255f,
                    onValueChange = { v ->
                        val base = AllSettings.launcherCustomColor.state
                        val b = (v * 255f).toInt().coerceIn(0, 255)
                        val c = (0xFF shl 24) or (base and 0x00FFFF00) or b
                        AllSettings.launcherCustomColor.updateState(c)
                    },
                    onValueChangeFinished = {
                        AllSettings.launcherCustomColor.save(AllSettings.launcherCustomColor.state)
                    },
                    valueRange = 0f..1f
                )

                Text(
                    text = "色调风格",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    PaletteStyle.entries.forEach { style ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AllSettings.launcherCustomPaletteStyle.updateState(style)
                                    AllSettings.launcherCustomPaletteStyle.save(style)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = customStyle == style,
                                onClick = {
                                    AllSettings.launcherCustomPaletteStyle.updateState(style)
                                    AllSettings.launcherCustomPaletteStyle.save(style)
                                }
                            )
                            Text(
                                text = style.styleLabel(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    title: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = CircleShape
                )
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private fun ColorThemeType.label(): String = when (this) {
    ColorThemeType.DYNAMIC -> "跟随系统取色"
    ColorThemeType.EMBERMIRE -> "余烬"
    ColorThemeType.VELVET_ROSE -> "丝绒玫瑰"
    ColorThemeType.MISTWAVE -> "雾波"
    ColorThemeType.GLACIER -> "冰川"
    ColorThemeType.VERDANTFIELD -> "苍野"
    ColorThemeType.URBAN_ASH -> "都市灰烬"
    ColorThemeType.VERDANT_DAWN -> "青翠晨曦"
    ColorThemeType.CUSTOM -> "自定义"
}

private fun ColorThemeType.shortLabel(): String = when (this) {
    ColorThemeType.DYNAMIC -> "系统"
    ColorThemeType.EMBERMIRE -> "余烬"
    ColorThemeType.VELVET_ROSE -> "丝绒"
    ColorThemeType.MISTWAVE -> "雾波"
    ColorThemeType.GLACIER -> "冰川"
    ColorThemeType.VERDANTFIELD -> "苍野"
    ColorThemeType.URBAN_ASH -> "灰烬"
    ColorThemeType.VERDANT_DAWN -> "晨曦"
    ColorThemeType.CUSTOM -> "自定义"
}

private fun ColorThemeType.previewColor(): Color = when (this) {
    ColorThemeType.DYNAMIC -> Color(0xFF7A8CA8)
    ColorThemeType.EMBERMIRE -> Color(0xFFD4652F)
    ColorThemeType.VELVET_ROSE -> Color(0xFFC2537A)
    ColorThemeType.MISTWAVE -> Color(0xFF4F8DA8)
    ColorThemeType.GLACIER -> Color(0xFF5B9BD5)
    ColorThemeType.VERDANTFIELD -> Color(0xFF4E8A5C)
    ColorThemeType.URBAN_ASH -> Color(0xFF6E7076)
    ColorThemeType.VERDANT_DAWN -> Color(0xFF69A97B)
    ColorThemeType.CUSTOM -> Color(0xFF3F51B5)
}

private fun PaletteStyle.styleLabel(): String = when (this) {
    PaletteStyle.TonalSpot -> "柔和"
    PaletteStyle.Neutral -> "中性"
    PaletteStyle.Vibrant -> "鲜艳"
    PaletteStyle.Expressive -> "表现力"
    PaletteStyle.Content -> "内容"
    PaletteStyle.Fidelity -> "忠实"
    PaletteStyle.Monochrome -> "单色"
    PaletteStyle.Rainbow -> "虹彩"
    PaletteStyle.FruitSalad -> "果缤纷"
    else -> name
}
