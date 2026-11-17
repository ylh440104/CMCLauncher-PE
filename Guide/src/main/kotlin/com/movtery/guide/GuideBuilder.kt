package com.movtery.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class GuideBuilder internal constructor() {
    internal val entries = mutableListOf<GuideEntry>()

    fun entry(
        key: GuideKey,
        nodeClick: NodeClickMode = NodeClickMode.Intercept,
        waitForNextNode: Boolean = nodeClick == NodeClickMode.PassThrough,
        advanceOnScrimClick: Boolean = true,
        placement: GuidePlacement = GuidePlacement.Auto,
        showNextTip: Boolean = true,
        content: @Composable (GuideScope) -> Unit
    ) {
        entries += GuideEntry(
            key = key,
            nodeClick = nodeClick,
            waitForNextNode = waitForNextNode,
            advanceOnScrimClick = advanceOnScrimClick,
            placement = placement,
            showNextTip = showNextTip,
            content = content
        )
    }

    fun intro(showNextTip: Boolean = true, content: @Composable (GuideScope) -> Unit) {
        entries += GuideEntry(
            key = object : GuideKey {},
            nodeClick = NodeClickMode.Intercept,
            advanceOnScrimClick = true,
            placement = GuidePlacement.Fixed(Alignment.Center),
            showNextTip = showNextTip,
            content = content,
            isIntro = true
        )
    }
}

@Composable
fun rememberGuide(
    colors: GuideColors = GuideDefaults.colors,
    holeRadius: Dp = GuideDefaults.holeRadius,
    holeBorderWidth: Dp = 0.dp,
    shape: Shape = GuideDefaults.contentShape,
    backBehavior: GuideBack = GuideBack.Block,
    builder: GuideBuilder.() -> Unit
): GuideController = remember(colors, holeRadius, holeBorderWidth, shape, backBehavior) {
    val result = GuideBuilder().apply(builder)
    GuideController(result.entries.toList(), colors, holeRadius, holeBorderWidth, shape, backBehavior)
}
