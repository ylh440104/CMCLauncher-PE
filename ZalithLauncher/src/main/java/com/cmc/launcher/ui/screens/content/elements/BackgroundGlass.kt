package com.cmc.launcher.ui.screens.content.elements

import android.graphics.Matrix
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.graphics.withSave
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.setting.enums.BackgroundBlur
import com.cmc.launcher.viewmodel.BackgroundViewModel
import com.cmc.launcher.viewmodel.LocalBackgroundViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

/**
 * 背景毛玻璃效果：在元素内容下方对齐绘制启动器背景的预模糊结果
 * @param enabled 是否应用模糊效果
 */
@Composable
fun Modifier.backgroundGlass(
    blur: Int,
    color: Color,
    enabled: Boolean = true,
): Modifier {
    if (AllSettings.backgroundBlurType.state == BackgroundBlur.Background) return this
    if (!enabled) return this
    val background = LocalBackgroundViewModel.current?.takeIf { it.isValid } ?: return this
    if (blur <= 0 || AllSettings.launcherBackgroundOpacity.state >= 100) return this
    return this then GlassElement(blur, color, background)
}

/**
 * 启动器背景捕获：由 Background 挂载，发布背景内容与屏幕区域供毛玻璃效果采样
 * @param recordContent 是否将内容录制到共享层（前景模糊模式）
 * @param blurRadiusPx 前景模式 GPU 预模糊半径（像素，仅 Android 12+ 生效）
 * @param whiteOverlayAlpha 白色提亮层透明度（0 表示不绘制）
 */
internal fun Modifier.backgroundCapture(
    store: BackgroundViewModel,
    recordContent: Boolean,
    blurRadiusPx: Float,
    whiteOverlayAlpha: Float,
): Modifier = this then BackgroundCaptureElement(store, recordContent, blurRadiusPx, whiteOverlayAlpha)
