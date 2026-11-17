package com.movtery.guide

import androidx.compose.runtime.Composable

enum class NodeClickMode {

    Intercept,

    PassThrough
}

enum class NextTip {

    TapBlank,

    Finish
}

class GuideEntry internal constructor(
    val key: GuideKey,
    val nodeClick: NodeClickMode,

    val waitForNextNode: Boolean = false,
    val advanceOnScrimClick: Boolean,
    val placement: GuidePlacement,
    val showNextTip: Boolean,
    val content: @Composable (GuideScope) -> Unit,
    internal val isIntro: Boolean = false
)
