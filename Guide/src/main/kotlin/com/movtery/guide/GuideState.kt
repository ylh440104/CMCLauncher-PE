package com.movtery.guide

import androidx.compose.ui.geometry.Rect

sealed interface GuideState {

    data object Idle : GuideState

    data class Active(
        val index: Int,
        val entry: GuideEntry,

        val anchors: List<Rect>
    ) : GuideState {

        val isReady: Boolean
            get() = entry.isIntro || anchors.isNotEmpty()
    }

    data object Finished : GuideState
}

enum class GuideBack {

    Block,

    Finish
}
