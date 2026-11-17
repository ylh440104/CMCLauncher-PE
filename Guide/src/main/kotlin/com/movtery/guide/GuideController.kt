package com.movtery.guide

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object GuideHub {
    private var active: GuideController? = null

    fun tryActivate(controller: GuideController): Boolean {
        val current = active
        if (current === controller) return true

        if (current != null && current.state.value is GuideState.Active) return false
        active = controller
        return true
    }

    fun deactivate(controller: GuideController) {
        if (active === controller) active = null
    }

    fun reset() {
        active = null
    }
}

class GuideController internal constructor(
    internal val entries: List<GuideEntry>,
    internal val colors: GuideColors,
    internal val holeRadius: Dp,
    internal val holeBorderWidth: Dp,
    internal val shape: Shape,
    internal val backBehavior: GuideBack
) {
    private val _state = MutableStateFlow<GuideState>(GuideState.Idle)
    val state: StateFlow<GuideState> = _state.asStateFlow()

    fun start(): Boolean {
        if (entries.isEmpty()) return false
        if (!GuideHub.tryActivate(this)) return false
        _state.value = GuideState.Active(0, entries.first(), emptyList())
        return true
    }

    fun next() {
        val current = _state.value as? GuideState.Active ?: return
        val nextIndex = current.index + 1
        if (nextIndex < entries.size) {
            _state.value = GuideState.Active(nextIndex, entries[nextIndex], emptyList())
        } else {
            finish()
        }
    }

    fun finish() {
        if (_state.value is GuideState.Active) {
            GuideHub.deactivate(this)
            _state.value = GuideState.Finished
        }
    }

    internal fun updateAnchors(index: Int, rects: List<Rect>) {
        val current = _state.value as? GuideState.Active ?: return
        if (current.index == index && current.anchors != rects) {
            _state.value = current.copy(anchors = rects)
        }
    }
}
