package com.cmc.launcher.ui.components

import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cmc.launcher.utils.animation.EaseOutFluent
import com.cmc.launcher.utils.animation.EnterTranslateEasing
import com.cmc.launcher.utils.animation.SwapTimeline

enum class AnimatedDirection {
    Auto, Top, Bottom, Left, Right, None
}

@Stable
class AnimatedContext(
    val progress: State<Float>,
    val alphaProgress: State<Float>,
    val distanceScale: State<Float>,
    val defaultDirection: AnimatedDirection
)

@Composable
fun rememberAnimatedContext(
    isVisible: Boolean,
    delayMillis: Int = 0
): AnimatedContext = rememberAnimatedContext(
    isVisible = isVisible,
    index = 0,
    extraDelayMillis = delayMillis,
    defaultDirection = AnimatedDirection.Top
)

@Composable
private fun rememberAnimatedContext(
    isVisible: Boolean,
    index: Int,
    extraDelayMillis: Int,
    defaultDirection: AnimatedDirection
): AnimatedContext {
    val progress = swapAnimateProgressAsState(
        isVisible = isVisible,
        index = index,
        extraDelayMillis = extraDelayMillis
    )
    val alphaProgress = swapAnimateAlphaProgressAsState(
        isVisible = isVisible,
        index = index,
        extraDelayMillis = extraDelayMillis
    )
    val distanceScale = swapAnimateDistanceScaleAsState(
        isVisible = isVisible,
        index = index
    )
    return remember(progress, alphaProgress, distanceScale) {
        AnimatedContext(progress, alphaProgress, distanceScale, defaultDirection)
    }
}

fun Modifier.animated(
    ctx: AnimatedContext,
    from: AnimatedDirection = AnimatedDirection.Auto,
    distance: Dp = 40.dp,
    fade: Boolean = true
): Modifier = this.graphicsLayer {
    val progress = ctx.progress.value
    val direction = if (from == AnimatedDirection.Auto) ctx.defaultDirection else from
    if (direction != AnimatedDirection.None) {

        val hiddenOffset = distance.toPx() * ctx.distanceScale.value * (1f - progress)
        when (direction) {
            AnimatedDirection.Top -> {
                translationX = 0f
                translationY = -hiddenOffset
            }
            AnimatedDirection.Bottom -> {
                translationX = 0f
                translationY = hiddenOffset
            }
            AnimatedDirection.Left -> {
                translationX = -hiddenOffset
                translationY = 0f
            }
            AnimatedDirection.Right -> {
                translationX = hiddenOffset
                translationY = 0f
            }
            else -> {
                translationX = 0f
                translationY = 0f
            }
        }
    } else {
        translationX = 0f
        translationY = 0f
    }
    alpha = if (fade) ctx.alphaProgress.value else 1f
}

@Composable
private fun swapAnimateProgressAsState(
    isVisible: Boolean,
    index: Int,
    extraDelayMillis: Int
): State<Float> {
    if (SwapTimeline.isSwapClosed()) return rememberUpdatedState(newValue = 1f)
    val spec: TweenSpec<Float> = if (isVisible) {
        tween(
            durationMillis = SwapTimeline.swapTime(SwapTimeline.ENTER_ITEM),
            delayMillis = SwapTimeline.enterStagger(index) + SwapTimeline.swapTime(extraDelayMillis),
            easing = EnterTranslateEasing
        )
    } else {
        tween(
            durationMillis = SwapTimeline.swapTime(SwapTimeline.EXIT_ITEM),
            delayMillis = SwapTimeline.exitStagger(index),
            easing = LinearEasing
        )
    }
    return swapAnimateProgressAsState(isVisible = isVisible, animationSpec = spec)
}

@Composable
fun swapAnimateProgressAsState(
    isVisible: Boolean,
    animationSpec: DurationBasedAnimationSpec<Float>
): State<Float> {
    if (SwapTimeline.isSwapClosed()) return rememberUpdatedState(newValue = 1f)
    return animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = animationSpec
    )
}

@Composable
private fun swapAnimateAlphaProgressAsState(
    isVisible: Boolean,
    index: Int,
    extraDelayMillis: Int
): State<Float> {
    if (SwapTimeline.isSwapClosed()) return rememberUpdatedState(newValue = 1f)
    val spec: TweenSpec<Float> = if (isVisible) {
        tween(
            durationMillis = SwapTimeline.swapTime(SwapTimeline.ENTER_ALPHA),
            delayMillis = SwapTimeline.enterStagger(index) + SwapTimeline.swapTime(extraDelayMillis),
            easing = EaseOutFluent(2)
        )
    } else {
        tween(
            durationMillis = SwapTimeline.swapTime(SwapTimeline.EXIT_ALPHA),
            delayMillis = SwapTimeline.exitStagger(index),
            easing = LinearEasing
        )
    }
    return swapAnimateAlphaProgressAsState(isVisible = isVisible, animationSpec = spec)
}

@Composable
fun swapAnimateAlphaProgressAsState(
    isVisible: Boolean,
    animationSpec: DurationBasedAnimationSpec<Float>
): State<Float> {
    if (SwapTimeline.isSwapClosed()) return rememberUpdatedState(newValue = 1f)
    return animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = animationSpec
    )
}

@Composable
private fun swapAnimateDistanceScaleAsState(
    isVisible: Boolean,
    index: Int
): State<Float> {
    if (SwapTimeline.isSwapClosed()) return rememberUpdatedState(newValue = 1f)
    val spec: TweenSpec<Float> = if (isVisible) {
        tween(
            durationMillis = SwapTimeline.swapTime(SwapTimeline.ENTER_ITEM),
            delayMillis = SwapTimeline.enterStagger(index),
            easing = LinearEasing
        )
    } else {
        tween(
            durationMillis = SwapTimeline.swapTime(SwapTimeline.EXIT_ITEM),
            delayMillis = SwapTimeline.exitStagger(index),
            easing = LinearEasing
        )
    }
    return animateFloatAsState(
        targetValue = if (isVisible) 1f else SwapTimeline.EXIT_DISTANCE_SCALE,
        animationSpec = spec
    )
}

@Composable
fun AnimatedColumn(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    stagger: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable AnimatedColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment
    ) {
        AnimatedColumnScopeImpl(this, isVisible, stagger).content()
    }
}

interface AnimatedColumnScope : ColumnScope {

    @Composable
    fun AnimatedItem(
        delay: Int? = null,
        content: @Composable ColumnScope.(ctx: AnimatedContext) -> Unit
    )
}

private class AnimatedColumnScopeImpl(
    columnScope: ColumnScope,
    private val isVisible: Boolean,
    private val stagger: Boolean
) : AnimatedColumnScope, ColumnScope by columnScope {
    private var itemIndex = 0

    @Composable
    override fun AnimatedItem(
        delay: Int?,
        content: @Composable ColumnScope.(ctx: AnimatedContext) -> Unit
    ) {
        val currentIndex = itemIndex++
        val ctx = rememberAnimatedContext(
            isVisible = isVisible,
            index = if (stagger) currentIndex else 0,
            extraDelayMillis = delay ?: 0,
            defaultDirection = AnimatedDirection.Top
        )
        content(ctx)
    }
}

@Composable
fun AnimatedRow(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    stagger: Boolean = true,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    content: @Composable AnimatedRowScope.() -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement
    ) {
        AnimatedRowScopeImpl(this, isVisible, stagger).content()
    }
}

interface AnimatedRowScope : RowScope {

    @Composable
    fun AnimatedItem(
        delay: Int? = null,
        content: @Composable RowScope.(ctx: AnimatedContext) -> Unit
    )
}

private class AnimatedRowScopeImpl(
    rowScope: RowScope,
    private val isVisible: Boolean,
    private val stagger: Boolean
) : AnimatedRowScope, RowScope by rowScope {
    private var itemIndex = 0

    @Composable
    override fun AnimatedItem(
        delay: Int?,
        content: @Composable RowScope.(ctx: AnimatedContext) -> Unit
    ) {
        val currentIndex = itemIndex++
        val ctx = rememberAnimatedContext(
            isVisible = isVisible,
            index = if (stagger) currentIndex else 0,
            extraDelayMillis = delay ?: 0,
            defaultDirection = AnimatedDirection.Right
        )
        content(ctx)
    }
}

@Composable
fun AnimatedLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    isVisible: Boolean,
    stagger: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: AnimatedLazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        verticalArrangement = verticalArrangement,
        contentPadding = contentPadding
    ) {
        AnimatedLazyListScopeImpl(this, isVisible, stagger).content()
    }
}

interface AnimatedLazyListScope {

    fun animatedItem(
        key: Any? = null,
        delay: Int? = null,
        content: @Composable LazyItemScope.(ctx: AnimatedContext) -> Unit
    )

    fun <T> animatedItems(
        items: List<T>,
        delay: Int = 0,
        key: ((item: T) -> Any)? = null,
        content: @Composable LazyItemScope.(index: Int, item: T, ctx: AnimatedContext) -> Unit
    )
}

private class AnimatedLazyListScopeImpl(
    private val lazyListScope: LazyListScope,
    private val isVisible: Boolean,
    private val stagger: Boolean
) : AnimatedLazyListScope {
    private var itemIndex = 0

    override fun animatedItem(
        key: Any?,
        delay: Int?,
        content: @Composable LazyItemScope.(ctx: AnimatedContext) -> Unit
    ) {
        val currentIndex = itemIndex++
        lazyListScope.item(key = key) {
            val ctx = rememberAnimatedContext(
                isVisible = isVisible,
                index = if (stagger) currentIndex else 0,
                extraDelayMillis = delay ?: 0,
                defaultDirection = AnimatedDirection.Top
            )
            content(ctx)
        }
    }

    override fun <T> animatedItems(
        items: List<T>,
        delay: Int,
        key: ((item: T) -> Any)?,
        content: @Composable LazyItemScope.(index: Int, item: T, ctx: AnimatedContext) -> Unit
    ) {
        lazyListScope.itemsIndexed(
            items = items,
            key = if (key != null) { _, item -> key(item) } else null
        ) { index, item ->
            val ctx = rememberAnimatedContext(
                isVisible = isVisible,
                index = if (stagger) index else 0,
                extraDelayMillis = delay,
                defaultDirection = AnimatedDirection.Top
            )
            content(index, item, ctx)
        }
    }
}
