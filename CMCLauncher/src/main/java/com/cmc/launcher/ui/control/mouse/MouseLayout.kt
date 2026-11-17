package com.cmc.launcher.ui.control.mouse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.cmc.launcher.R
import com.cmc.launcher.bridge.CursorShape
import com.cmc.launcher.bridge.ZLBridgeStates
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.setting.enums.MouseControlMode
import com.cmc.launcher.utils.device.PhysicalMouseChecker
import com.cmc.launcher.utils.file.child
import com.cmc.launcher.utils.file.ifExists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

val arrowPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("default_pointer.image")

val linkPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("link_pointer.image")

val iBeamPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("ibeam_pointer.image")

val crossHairPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("crosshair_pointer.image")

val resizeNSPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("resize_NS_pointer.image")

val resizeEWPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("resize_EW_pointer.image")

val resizeAllPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("resize_ALL_pointer.image")

val notAllowedPointerFile: File = PathManager.DIR_MOUSE_POINTER.child("not_allowed_pointer.image")

@Composable
fun VirtualPointerLayout(
    modifier: Modifier = Modifier,
    controlMode: MouseControlMode = AllSettings.mouseControlMode.state,
    enableMouseClick: Boolean = AllSettings.enableMouseClick.state,
    longPressTimeoutMillis: Long = AllSettings.mouseLongPressDelay.state.toLong(),
    requestPointerCapture: Boolean = !AllSettings.physicalMouseMode.state,
    hideMouseInClickMode: Boolean = AllSettings.hideMouse.state,
    lastMousePosition: Offset? = null,
    onTap: (Offset) -> Unit = {},
    onLongPress: () -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    onPointerMove: (Offset) -> Unit = {},
    onMouseScroll: (Offset) -> Unit = {},
    onMouseButton: (button: Int, pressed: Boolean) -> Unit = { _, _ -> },
    isMoveOnlyPointer: (PointerId) -> Boolean = { false },
    onOccupiedPointer: (PointerId) -> Unit = {},
    onReleasePointer: (PointerId) -> Unit = {},
    mouseSize: Dp = AllSettings.mouseSize.state.dp,
    cursorSensitivity: Int = AllSettings.cursorSensitivity.state,
    requestFocusKey: Any? = null
) {
    val speedFactor = cursorSensitivity / 100f

    val windowSize = LocalWindowInfo.current.containerSize
    val screenWidth: Float = windowSize.width.toFloat()
    val screenHeight: Float = windowSize.height.toFloat()

    val capturePointer = requestPointerCapture && !rememberTextInputActive()

    var showMousePointer by remember {
        mutableStateOf(requestPointerCapture)
    }
    fun updateMousePointer(show: Boolean) {
        showMousePointer = show
    }
    LaunchedEffect(hideMouseInClickMode) {
        updateMousePointer(
            show = when {

                PhysicalMouseChecker.physicalMouseConnected -> requestPointerCapture

                controlMode == MouseControlMode.CLICK -> !hideMouseInClickMode

                else -> controlMode == MouseControlMode.SLIDE
            }
        )
    }

    var pointerPosition by remember {
        val pos = lastMousePosition?.takeIf {

            !showMousePointer
        } ?: Offset(screenWidth / 2f, screenHeight / 2f)
        onPointerMove(pos)
        mutableStateOf(pos)
    }

    Box(modifier = modifier) {
        val cursorShape by ZLBridgeStates.cursorShape.collectAsStateWithLifecycle()

        if (showMousePointer) {
            MousePointer(
                modifier = Modifier.mouseFixedPosition(
                    mouseSize = mouseSize,
                    cursorShape = cursorShape,
                    pointerPosition = pointerPosition
                ),
                cursorShape = cursorShape,
                mouseSize = mouseSize,
                mouseFile = getMouseFile(cursorShape).ifExists(),
            )
        }

        TouchpadLayout(
            modifier = Modifier.fillMaxSize(),
            controlMode = controlMode,
            enableMouseClick = enableMouseClick,
            longPressTimeoutMillis = longPressTimeoutMillis,
            requestPointerCapture = capturePointer,
            pointerIcon = cursorShape.composeIcon,
            onTap = { fingerPos ->
                onTap(
                    if (controlMode == MouseControlMode.CLICK) {
                        updateMousePointer(!hideMouseInClickMode)

                        pointerPosition = fingerPos
                        fingerPos
                    } else {
                        pointerPosition
                    }
                )
            },
            onLongPress = onLongPress,
            onLongPressEnd = onLongPressEnd,
            onPointerMove = { offset, isMoveOnly ->
                pointerPosition = if (isMoveOnly || controlMode == MouseControlMode.SLIDE) {
                    updateMousePointer(true)
                    Offset(
                        x = (pointerPosition.x + offset.x * speedFactor).coerceIn(0f, screenWidth),
                        y = (pointerPosition.y + offset.y * speedFactor).coerceIn(0f, screenHeight)
                    )
                } else {
                    updateMousePointer(!hideMouseInClickMode)

                    offset
                }
                onPointerMove(pointerPosition)
            },
            onMouseMove = { offset ->
                if (capturePointer) {
                    updateMousePointer(true)
                    pointerPosition = Offset(
                        x = (pointerPosition.x + offset.x * speedFactor).coerceIn(0f, screenWidth),
                        y = (pointerPosition.y + offset.y * speedFactor).coerceIn(0f, screenHeight)
                    )
                    onPointerMove(pointerPosition)
                } else {

                    updateMousePointer(false)
                    pointerPosition = offset
                    onPointerMove(pointerPosition)
                }
            },
            onMouseScroll = onMouseScroll,
            onMouseButton = onMouseButton,
            isMoveOnlyPointer = isMoveOnlyPointer,
            onOccupiedPointer = onOccupiedPointer,
            onReleasePointer = onReleasePointer,
            inputChange = arrayOf<Any>(speedFactor, controlMode),
            requestFocusKey = requestFocusKey
        )
    }
}

@Composable
fun Modifier.mouseFixedPosition(
    mouseSize: Dp,
    cursorShape: CursorShape,
    pointerPosition: Offset
): Modifier {
    val sizePx = with(LocalDensity.current) { mouseSize.toPx() }
    val hotspotUnit = remember(
        cursorShape
    ) {
        when (cursorShape) {
            CursorShape.Arrow -> AllSettings.arrowMouseHotspot
            CursorShape.IBeam -> AllSettings.iBeamMouseHotspot
            CursorShape.Hand -> AllSettings.linkMouseHotspot
            CursorShape.CrossHair -> AllSettings.crossHairMouseHotspot
            CursorShape.ResizeNS -> AllSettings.resizeNSMouseHotspot
            CursorShape.ResizeEW -> AllSettings.resizeEWMouseHotspot
            CursorShape.ResizeAll -> AllSettings.resizeAllMouseHotspot
            CursorShape.NotAllowed -> AllSettings.notAllowedMouseHotspot
        }
    }
    val hotspot = hotspotUnit.state
    val x = pointerPosition.x - sizePx * (hotspot.xPercent.toFloat() / 100f)
    val y = pointerPosition.y - sizePx * (hotspot.yPercent.toFloat() / 100f)

    return this.absoluteOffset(
        x = with(LocalDensity.current) { x.toDp() },
        y = with(LocalDensity.current) { y.toDp() }
    )
}

@Composable
fun getMouseFile(
    cursorShape: CursorShape
): File {
    return remember(cursorShape) {
        when (cursorShape) {
            CursorShape.Arrow -> arrowPointerFile
            CursorShape.IBeam -> iBeamPointerFile
            CursorShape.Hand -> linkPointerFile
            CursorShape.CrossHair -> crossHairPointerFile
            CursorShape.ResizeNS -> resizeNSPointerFile
            CursorShape.ResizeEW -> resizeEWPointerFile
            CursorShape.ResizeAll -> resizeAllPointerFile
            CursorShape.NotAllowed -> notAllowedPointerFile
        }
    }
}

@Composable
fun MousePointer(
    modifier: Modifier = Modifier,
    mouseSize: Dp = AllSettings.mouseSize.state.dp,
    cursorShape: CursorShape = CursorShape.Arrow,
    mouseFile: File?,
    centerIcon: Boolean = false,
    triggerRefresh: Any? = null,
    crossfade: Boolean = false
) {
    val context = LocalContext.current

    val fileExists by produceState(initialValue = false, triggerRefresh, mouseFile) {
        value = withContext(Dispatchers.IO) { mouseFile?.exists() == true }
    }
    val defaultRes = when (cursorShape) {
        CursorShape.Arrow -> R.drawable.img_mouse_pointer_arrow
        CursorShape.IBeam -> R.drawable.img_mouse_pointer_ibeam
        CursorShape.Hand -> R.drawable.img_mouse_pointer_link
        CursorShape.CrossHair -> R.drawable.img_mouse_pointer_crosshair
        CursorShape.ResizeNS -> R.drawable.img_mouse_pointer_resize_ns
        CursorShape.ResizeEW -> R.drawable.img_mouse_pointer_resize_ew
        CursorShape.ResizeAll -> R.drawable.img_mouse_pointer_resize_move
        CursorShape.NotAllowed -> R.drawable.img_mouse_pointer_not_allowed
    }

    val model = remember(fileExists, triggerRefresh, cursorShape, crossfade) {
        ImageRequest.Builder(context)
            .data(if (fileExists && mouseFile != null) mouseFile else defaultRes)
            .crossfade(crossfade)
            .build()
    }

    val imageAlignment = if (centerIcon) Alignment.Center else Alignment.TopStart

    AsyncImage(
        model = model,
        contentDescription = null,
        alignment = imageAlignment,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(mouseSize)
    )
}
