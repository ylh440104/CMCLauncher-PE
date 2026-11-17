package com.cmc.launcher.ui.screens.content.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.cmc.launcher.setting.enums.ActionMenuSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val PickUpScale = 1.08f

interface ActionMenuDragHandler {
    fun onDragStart(position: Offset)
    fun onDrag(position: Offset)
    fun onDragEnd()
    fun onDragCancel()

    fun addExclusion(exclusion: ActionMenuDragExclusion)

    fun removeExclusion(exclusion: ActionMenuDragExclusion)
}

class ActionMenuDragExclusion {
    internal var bounds: Rect? = null
}

val LocalActionMenuDrag = staticCompositionLocalOf<ActionMenuDragHandler?> { null }

@Composable
fun Modifier.actionMenuDragAnchor(): Modifier {
    val handler = LocalActionMenuDrag.current ?: return this
    var anchorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return this
        .onGloballyPositioned { anchorCoordinates = it }
        .pointerInput(handler) {
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    anchorCoordinates?.let { coordinates ->
                        handler.onDragStart(coordinates.positionInRoot() + position)
                    }
                },
                onDragEnd = { handler.onDragEnd() },
                onDragCancel = { handler.onDragCancel() },
                onDrag = { change, _ ->
                    anchorCoordinates?.let { coordinates ->
                        handler.onDrag(coordinates.positionInRoot() + change.position)
                    }
                }
            )
        }
}

@Composable
fun Modifier.actionMenuDragExclusion(): Modifier {
    val handler = LocalActionMenuDrag.current ?: return this
    val exclusion = remember { ActionMenuDragExclusion() }
    DisposableEffect(handler) {
        handler.addExclusion(exclusion)
        onDispose {
            handler.removeExclusion(exclusion)
        }
    }
    return this.onGloballyPositioned { coordinates ->
        exclusion.bounds = if (coordinates.isAttached) coordinates.boundsInRoot() else null
    }
}

@Composable
fun rememberActionMenuDragState(
    onCommit: (ActionMenuSide) -> Unit
): ActionMenuDragState {
    val scope = rememberCoroutineScope()
    val settleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Offset>()
    val scaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val previewSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val currentOnCommit by rememberUpdatedState(onCommit)
    return remember {
        ActionMenuDragState(
            scope = scope,
            settleSpec = settleSpec,
            scaleSpec = scaleSpec,
            previewSpec = previewSpec,
            onCommit = { currentOnCommit(it) }
        )
    }
}

@Stable
class ActionMenuDragState(
    private val scope: CoroutineScope,
    private val settleSpec: AnimationSpec<Offset>,
    private val scaleSpec: AnimationSpec<Float>,
    private val previewSpec: AnimationSpec<Float>,
    private val onCommit: (ActionMenuSide) -> Unit
) : ActionMenuDragHandler {

    var floating by mutableStateOf(false)
        private set

    var previewSide by mutableStateOf<ActionMenuSide?>(null)
        private set

    var scale by mutableFloatStateOf(1f)
        private set

    var cardPosition by mutableStateOf(Offset.Zero)
        private set

    val settleOffset = Animatable(Offset.Zero, Offset.VectorConverter)

    val previewShift = Animatable(0f, Float.VectorConverter)

    private var settleJob: Job? = null
    private var scaleJob: Job? = null
    private var fingerAtGrab = Offset.Zero
    private var cardPositionAtGrab = Offset.Zero

    private val exclusions = mutableListOf<ActionMenuDragExclusion>()

    var dockedSide = ActionMenuSide.END

    var isRtl = false

    var parentOrigin = Offset.Zero

    var parentWidthPx = 0f

    var outerPaddingPx = 0f

    var menuSpanPx = 0f

    fun landingOf(side: ActionMenuSide): Offset {
        val physicallyLeft = if (isRtl) side == ActionMenuSide.END else side == ActionMenuSide.START
        val x = if (physicallyLeft) outerPaddingPx else parentWidthPx - menuSpanPx
        return Offset(x, outerPaddingPx)
    }

    override fun addExclusion(exclusion: ActionMenuDragExclusion) {
        exclusions.add(exclusion)
    }

    override fun removeExclusion(exclusion: ActionMenuDragExclusion) {
        exclusions.remove(exclusion)
    }

    override fun onDragStart(position: Offset) {

        if (exclusions.any { it.bounds?.contains(position) == true }) return

        settleJob?.cancel()
        fingerAtGrab = position - parentOrigin

        cardPositionAtGrab = landingOf(dockedSide) + settleOffset.value
        cardPosition = cardPositionAtGrab
        previewSide = dockedSide
        floating = true
        animatePreviewTo(0f)
        animateScaleTo(PickUpScale)
    }

    override fun onDrag(position: Offset) {
        if (!floating) return
        val finger = position - parentOrigin
        cardPosition = cardPositionAtGrab + (finger - fingerAtGrab)

        val physicalLeft = finger.x < parentWidthPx / 2f
        val side = if (isRtl) {
            if (physicalLeft) ActionMenuSide.END else ActionMenuSide.START
        } else {
            if (physicalLeft) ActionMenuSide.START else ActionMenuSide.END
        }
        if (side != previewSide) {
            previewSide = side
            animatePreviewTo(previewShiftTargetOf(side))
        }
    }

    override fun onDragEnd() {
        if (!floating) return
        val target = previewSide ?: dockedSide
        settle(target)
    }

    override fun onDragCancel() {
        if (!floating) return
        settle(dockedSide)
    }

    private fun settle(target: ActionMenuSide) {
        settleJob?.cancel()
        val releasePosition = cardPosition
        settleJob = scope.launch {
            if (target != dockedSide) {
                onCommit(target)
                val shiftTarget = previewShiftTargetOf(target)
                previewShift.snapTo(previewShift.value - shiftTarget)
            }
            launch {
                previewShift.animateTo(0f, previewSpec)
            }
            settleOffset.snapTo(releasePosition - landingOf(target))
            previewSide = null
            floating = false
            launch { animateScaleTo(1f) }
            settleOffset.animateTo(Offset.Zero, settleSpec)
        }
    }

    private fun animateScaleTo(target: Float) {
        scaleJob?.cancel()
        scaleJob = scope.launch {
            animate(
                initialValue = scale,
                targetValue = target,
                animationSpec = scaleSpec
            ) { value, _ -> scale = value }
        }
    }

    private fun previewShiftTargetOf(side: ActionMenuSide): Float = when {
        side == dockedSide -> 0f
        dockedSide == ActionMenuSide.END -> menuSpanPx
        else -> -menuSpanPx
    }

    private fun animatePreviewTo(target: Float) {
        scope.launch {
            previewShift.animateTo(target, previewSpec)
        }
    }
}
