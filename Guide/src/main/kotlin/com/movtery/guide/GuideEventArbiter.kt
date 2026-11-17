package com.movtery.guide

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal enum class TapAction {

    Advance,

    GateArm,

    None
}

private enum class TapRegion { Anchor, Content, Scrim }

internal class GuideEventArbiter internal constructor(
    private val guides: List<GuideController>,
    private val registry: GuideRegistry
) {

    private fun activeState(controller: GuideController): GuideState.Active? =
        controller.state.value as? GuideState.Active

    fun passThroughAt(position: Offset): Boolean {
        val state = guides.firstNotNullOfOrNull { activeState(it) } ?: return true
        if (state.entry.isIntro) return false
        if (state.entry.nodeClick != NodeClickMode.PassThrough) return false
        return registry.rectsFor(state.entry.key).any { it.contains(position) }
    }

    fun gateActive(controller: GuideController, state: GuideState.Active): Boolean {
        val next = controller.entries.getOrNull(state.index + 1) ?: return false
        return state.entry.waitForNextNode && !next.isIntro
    }

    fun nextAnchorReady(controller: GuideController, state: GuideState.Active): Boolean {
        val next = controller.entries.getOrNull(state.index + 1) ?: return false
        if (next.isIntro) return false
        return registry.nodesFor(next.key).isNotEmpty()
    }

    fun resolveTap(
        controller: GuideController,
        contentRect: Rect?,
        position: Offset
    ): TapAction {
        val state = activeState(controller) ?: return TapAction.None
        if (state.entry.isIntro) return TapAction.Advance
        return when (regionAt(state, contentRect, position)) {
            TapRegion.Anchor -> tapAction(controller, state)
            TapRegion.Content -> TapAction.None
            TapRegion.Scrim -> if (state.entry.advanceOnScrimClick) {
                tapAction(controller, state)
            } else {
                TapAction.None
            }
        }
    }

    private fun tapAction(
        controller: GuideController,
        state: GuideState.Active
    ): TapAction = if (gateActive(controller, state)) TapAction.GateArm else TapAction.Advance

    private fun regionAt(
        state: GuideState.Active,
        contentRect: Rect?,
        position: Offset
    ): TapRegion = when {
        registry.rectsFor(state.entry.key).any { it.contains(position) } -> TapRegion.Anchor
        contentRect?.contains(position) == true -> TapRegion.Content
        else -> TapRegion.Scrim
    }
}
