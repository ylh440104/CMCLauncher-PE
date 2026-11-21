package com.cmc.launcher.ui.screens.content.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cmc.launcher.R
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.setting.enums.MirrorSourceType
import com.cmc.launcher.ui.base.BaseScreen
import com.cmc.launcher.ui.components.animated
import com.cmc.launcher.ui.components.rememberAnimatedContext
import com.cmc.launcher.ui.kit.CmcButton
import com.cmc.launcher.ui.kit.CmcCard
import com.cmc.launcher.ui.kit.CmcDivider
import com.cmc.launcher.ui.kit.CmcPanel
import com.cmc.launcher.ui.kit.CmcProgressBar
import com.cmc.launcher.ui.kit.CmcRadioRow
import com.cmc.launcher.ui.kit.CmcRow
import com.cmc.launcher.ui.kit.CmcSection
import com.cmc.launcher.ui.kit.CmcSliderRow
import com.cmc.launcher.ui.kit.CmcSwatch
import com.cmc.launcher.ui.kit.CmcTag
import com.cmc.launcher.ui.screens.NestedNavKey
import com.cmc.launcher.ui.screens.NormalNavKey
import com.cmc.launcher.ui.screens.TitledNavKey
import com.cmc.launcher.ui.theme.Alpha
import com.cmc.launcher.ui.theme.ColorThemeType
import com.cmc.launcher.ui.theme.Radius
import com.cmc.launcher.ui.theme.Size
import com.cmc.launcher.ui.theme.Space
import com.cmc.launcher.viewmodel.LocalBackgroundViewModel
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.launch

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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .animated(ctx),
            state = listState,
            contentPadding = PaddingValues(
                start = Space.screenH,
                end = Space.screenH,
                top = Space.sm,
                bottom = Space.xxxl
            ),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item { MirrorSection() }
            item { PaletteSection() }
            item { BackgroundSection() }
            item { BlurSection() }
            item { FooterSection() }
        }
    }
}

@Composable
private fun MirrorSection() {
    val current = AllSettings.gameDownloadSource.state
    CmcSection(title = "下载线路", caption = "游戏本体与依赖的获取渠道")
    CmcPanel {
        MirrorItem(
            title = "自动选择",
            caption = "根据当前网络挑选延迟最低的线路",
            iconRes = R.drawable.ic_autorenew,
            selected = current == MirrorSourceType.AUTO
        ) {
            AllSettings.gameDownloadSource.save(MirrorSourceType.AUTO)
        }
        CmcDivider(modifier = Modifier.padding(horizontal = Space.xs))
        MirrorItem(
            title = "镜像加速",
            caption = "国内镜像分发，下载速度更快",
            iconRes = R.drawable.ic_rocket_launch_filled,
            selected = current == MirrorSourceType.MIRROR
        ) {
            AllSettings.gameDownloadSource.save(MirrorSourceType.MIRROR)
        }
        CmcDivider(modifier = Modifier.padding(horizontal = Space.xs))
        MirrorItem(
            title = "官方源",
            caption = "直连原始分发服务器",
            iconRes = R.drawable.ic_public,
            selected = current == MirrorSourceType.OFFICIAL
        ) {
            AllSettings.gameDownloadSource.save(MirrorSourceType.OFFICIAL)
        }
    }
}

@Composable
private fun MirrorItem(
    title: String,
    caption: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    CmcCard(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.medium),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = Modifier.size(Size.iconSm),
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Space.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    modifier = Modifier.padding(top = Space.xxs),
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Space.sm))
            if (selected) {
                CmcTag(text = "使用中")
            } else {
                Text(
                    text = "切换",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PaletteSection() {
    val current = AllSettings.launcherColorTheme.state
    val customArgb = AllSettings.launcherCustomColor.state
    val customColor = Color(customArgb)
    val customStyle = AllSettings.launcherCustomPaletteStyle.state

    CmcSection(
        title = "配色",
        caption = "主题色板与自定义种子色",
        trailing = {
            CmcTag(
                text = current.displayName(),
                container = MaterialTheme.colorScheme.primary.copy(alpha = Alpha.soft)
            )
        }
    )

    CmcPanel {
        Text(
            text = "内置色板",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(Space.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PaletteSwatch(ColorThemeType.DYNAMIC, current, Color(0xFF6C7C94))
            PaletteSwatch(ColorThemeType.EMBERMIRE, current, Color(0xFFD4652F))
            PaletteSwatch(ColorThemeType.VELVET_ROSE, current, Color(0xFFC2537A))
            PaletteSwatch(ColorThemeType.MISTWAVE, current, Color(0xFF4F8DA8))
            PaletteSwatch(ColorThemeType.GLACIER, current, Color(0xFF5B9BD5))
        }
        Spacer(Modifier.size(Space.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PaletteSwatch(ColorThemeType.VERDANTFIELD, current, Color(0xFF4E8A5C))
            PaletteSwatch(ColorThemeType.URBAN_ASH, current, Color(0xFF6E7076))
            PaletteSwatch(ColorThemeType.VERDANT_DAWN, current, Color(0xFF69A97B))
            PaletteSwatch(ColorThemeType.CUSTOM, current, customColor)
            Spacer(Modifier.width(Size.swatch))
        }
    }

    if (current == ColorThemeType.CUSTOM) {
        CmcPanel(modifier = Modifier.padding(top = Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "种子色",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                CmcSwatch(color = customColor, selected = true, onClick = {})
            }
            Spacer(Modifier.size(Space.xs))
            RgbChannel(
                label = "红",
                value = (customArgb shr 16) and 0xFF
            ) { v ->
                AllSettings.launcherCustomColor.updateState(
                    (0xFF shl 24) or (v shl 16) or (customArgb and 0x0000FFFF)
                )
            }
            RgbChannel(
                label = "绿",
                value = (customArgb shr 8) and 0xFF
            ) { v ->
                AllSettings.launcherCustomColor.updateState(
                    (0xFF shl 24) or (customArgb and 0x00FF0000) or (v shl 8) or (customArgb and 0xFF)
                )
            }
            RgbChannel(
                label = "蓝",
                value = customArgb and 0xFF
            ) { v ->
                AllSettings.launcherCustomColor.updateState(
                    (0xFF shl 24) or (customArgb and 0x00FFFF00) or v
                )
            }
            CmcDivider(modifier = Modifier.padding(vertical = Space.sm))
            Text(
                text = "色调风格",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(Space.xs))
            PaletteStyle.entries.forEach { style ->
                CmcRadioRow(
                    text = style.displayName(),
                    selected = customStyle == style,
                    onClick = {
                        AllSettings.launcherCustomPaletteStyle.updateState(style)
                        AllSettings.launcherCustomPaletteStyle.save(style)
                    }
                )
            }
        }
    }
}

@Composable
private fun PaletteSwatch(
    type: ColorThemeType,
    current: ColorThemeType,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CmcSwatch(
            color = color,
            selected = current == type,
            onClick = {
                AllSettings.launcherColorTheme.updateState(type)
                AllSettings.launcherColorTheme.save(type)
            }
        )
        Text(
            modifier = Modifier.padding(top = Space.xs),
            text = type.shortName(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (current == type) FontWeight.SemiBold else FontWeight.Normal,
            color = if (current == type) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RgbChannel(label: String, value: Int, onValue: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.width(20.dp),
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.xs),
            value = value / 255f,
            onValueChange = { onValue((it * 255f).toInt().coerceIn(0, 255)) },
            onValueChangeFinished = {
                AllSettings.launcherCustomColor.save(AllSettings.launcherCustomColor.state)
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )
        Text(
            modifier = Modifier.width(30.dp),
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BackgroundSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backgroundViewModel = LocalBackgroundViewModel.current
    val backgroundValid = backgroundViewModel?.isValid == true
    val isVideo = backgroundViewModel?.isVideo == true
    var isBusy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && backgroundViewModel != null) {
            isBusy = true
            scope.launch {
                runCatching { backgroundViewModel.import(context, uri) }
                isBusy = false
            }
        }
    }

    CmcSection(title = "背景", caption = "支持图片、动图与视频")
    CmcPanel {
        CmcRow(
            icon = painterResource(R.drawable.ic_image_outlined),
            title = "自定义背景",
            summary = when {
                isBusy -> "正在导入…"
                backgroundValid && isVideo -> "当前使用视频背景"
                backgroundValid -> "当前使用图片背景"
                else -> "未设置，使用默认背景"
            },
            trailing = {
                if (backgroundValid) {
                    CmcTag(
                        text = if (isVideo) "视频" else "图片",
                        container = MaterialTheme.colorScheme.secondary.copy(alpha = Alpha.soft)
                    )
                }
            }
        )
        Spacer(Modifier.size(Space.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            CmcButton(
                modifier = Modifier.weight(1f),
                text = if (backgroundValid) "更换背景" else "选择文件",
                enabled = !isBusy,
                onClick = {
                    picker.launch(
                        arrayOf("image/*", "video/*", "application/octet-stream")
                    )
                }
            )
            if (backgroundValid) {
                CmcButton(
                    modifier = Modifier.weight(1f),
                    text = "移除",
                    filled = false,
                    accent = MaterialTheme.colorScheme.error,
                    onClick = { scope.launch { backgroundViewModel?.delete() } }
                )
            }
        }
    }
}

@Composable
private fun BlurSection() {
    val blur = AllSettings.backgroundBlur.state
    val opacity = AllSettings.launcherBackgroundOpacity.state

    CmcSection(title = "外观", caption = "毛玻璃强度与背景透明度")
    CmcPanel {
        CmcSliderRow(
            title = "模糊强度",
            summary = "调高后玻璃背后的内容更朦胧",
            value = blur.toFloat(),
            onValueChange = { AllSettings.backgroundBlur.updateState(it.toInt()) },
            valueRange = 0f..40f,
            valueLabel = blur.toString(),
            steps = 39,
            onValueChangeFinished = {
                AllSettings.backgroundBlur.save(AllSettings.backgroundBlur.state)
            }
        )
        CmcDivider(modifier = Modifier.padding(horizontal = Space.xs))
        CmcSliderRow(
            title = "背景不透明度",
            summary = "调低可让文字与卡片更突出",
            value = opacity.toFloat(),
            onValueChange = { AllSettings.launcherBackgroundOpacity.updateState(it.toInt()) },
            valueRange = 20f..100f,
            valueLabel = "$opacity%",
            onValueChangeFinished = {
                AllSettings.launcherBackgroundOpacity.save(
                    AllSettings.launcherBackgroundOpacity.state
                )
            }
        )
        Spacer(Modifier.size(Space.xs))
        CmcProgressBar(progress = opacity / 100f)
    }
}

@Composable
private fun FooterSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.xl, bottom = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "设置即时生效",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.strong)
        )
        Spacer(Modifier.size(Space.xxs))
        Box(modifier = Modifier.width(40.dp).size(width = 40.dp, height = 2.dp))
    }
}

private fun ColorThemeType.displayName(): String = when (this) {
    ColorThemeType.DYNAMIC -> "跟随系统"
    ColorThemeType.EMBERMIRE -> "余烬"
    ColorThemeType.VELVET_ROSE -> "丝绒玫瑰"
    ColorThemeType.MISTWAVE -> "雾波"
    ColorThemeType.GLACIER -> "冰川"
    ColorThemeType.VERDANTFIELD -> "苍野"
    ColorThemeType.URBAN_ASH -> "都市灰烬"
    ColorThemeType.VERDANT_DAWN -> "青翠晨曦"
    ColorThemeType.CUSTOM -> "自定义"
}

private fun ColorThemeType.shortName(): String = when (this) {
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

private fun PaletteStyle.displayName(): String = when (this) {
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