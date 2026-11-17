package com.movtery.guide

import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

class GuideScope internal constructor(

    val anchors: List<Rect>,

    val containerSize: IntSize,
    private val sideState: State<GuideSide?>,

    val controller: GuideController
) {

    val bounds: Rect = anchors.boundsUnion()

    val side: GuideSide?
        get() = sideState.value
}
