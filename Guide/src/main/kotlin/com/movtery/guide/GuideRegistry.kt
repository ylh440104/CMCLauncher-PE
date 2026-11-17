package com.movtery.guide

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp

internal class GuideRegistry {
    private val nodes = HashMap<GuideKey, MutableList<GuideNode>>()
    private val scrollHandlers = mutableListOf<GuideScrollHandler>()
    private val versionState = mutableIntStateOf(0)

    val version: Int
        get() = versionState.intValue

    fun attach(node: GuideNode) {
        nodes.getOrPut(node.key) { mutableListOf() }.add(node)
        bump()
    }

    fun detach(node: GuideNode) {
        val list = nodes[node.key] ?: return
        if (list.remove(node)) {
            if (list.isEmpty()) nodes.remove(node.key)
            bump()
        }
    }

    fun updateBounds(node: GuideNode, bounds: Rect) {
        if (node.bounds != bounds) {
            node.bounds = bounds
            bump()
        }
    }

    fun updatePreferSide(node: GuideNode, side: GuideSide?) {
        if (node.preferSide != side) {
            node.preferSide = side
            bump()
        }
    }

    fun updateStyle(node: GuideNode, radius: Dp?, borderWidth: Dp?, borderColor: Color?, shape: Shape?) {
        if (node.holeRadius != radius || node.holeBorderWidth != borderWidth || node.holeBorderColor != borderColor || node.shape != shape) {
            node.holeRadius = radius
            node.holeBorderWidth = borderWidth
            node.holeBorderColor = borderColor
            node.shape = shape
            bump()
        }
    }

    internal fun nodesFor(key: GuideKey): List<GuideNode> = nodes[key].orEmpty()

    fun rectsFor(key: GuideKey): List<Rect> = nodesFor(key).map(GuideNode::bounds)

    fun sideHintFor(key: GuideKey): GuideSide? =
        nodes[key]?.singleOrNull()?.preferSide

    suspend fun requestScroll(key: GuideKey): Boolean {
        scrollHandlers.toList().forEach { handler ->
            if (handler(key)) return true
        }
        return false
    }

    suspend fun bringIntoView(key: GuideKey) {
        nodes[key].orEmpty().forEach { node ->
            node.bringIntoViewRequester.bringIntoView(Rect.Zero)
        }
    }

    fun addScrollHandler(handler: GuideScrollHandler) {
        scrollHandlers.add(handler)
    }

    fun removeScrollHandler(handler: GuideScrollHandler) {
        scrollHandlers.remove(handler)
    }

    private fun bump() {
        versionState.intValue++
    }
}

internal typealias GuideScrollHandler = suspend (GuideKey) -> Boolean

internal class GuideNode internal constructor(internal val key: GuideKey) {
    internal var bounds: Rect = Rect.Zero
    internal var preferSide: GuideSide? = null
    internal var holeRadius: Dp? = null
    internal var holeBorderWidth: Dp? = null
    internal var holeBorderColor: Color? = null
    internal var shape: Shape? = null
    internal val bringIntoViewRequester = BringIntoViewRequester()
}

internal val LocalGuideRegistry = compositionLocalOf<GuideRegistry?> { null }

fun Modifier.guideNode(
    key: GuideKey,
    preferSide: GuideSide? = null,
    holeRadius: Dp? = null,
    holeBorderWidth: Dp? = null,
    holeBorderColor: Color? = null,
    shape: Shape? = null
): Modifier = composed {
    val registry = LocalGuideRegistry.current ?: return@composed this
    val node = remember(key) { GuideNode(key) }
    DisposableEffect(key, registry) {
        registry.attach(node)
        onDispose { registry.detach(node) }
    }
    SideEffect {
        registry.updatePreferSide(node, preferSide)
        registry.updateStyle(node, holeRadius, holeBorderWidth, holeBorderColor, shape)
    }
    this
        .bringIntoViewRequester(node.bringIntoViewRequester)
        .onGloballyPositioned { coordinates ->
            registry.updateBounds(node, coordinates.boundsInRoot())
        }
}

fun Modifier.guideScrollTo(scroll: suspend (GuideKey) -> Boolean): Modifier = composed {
    val registry = LocalGuideRegistry.current ?: return@composed this
    val current by rememberUpdatedState(scroll)
    DisposableEffect(registry) {
        val handler: GuideScrollHandler = { key -> current(key) }
        registry.addScrollHandler(handler)
        onDispose { registry.removeScrollHandler(handler) }
    }
    this
}
