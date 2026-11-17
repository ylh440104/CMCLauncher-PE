package com.movtery.guide

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun GuideHost(
    vararg guides: GuideController,
    modifier: Modifier = Modifier,
    nextTip: @Composable (NextTip) -> Unit = {},
    animations: GuideAnimations = GuideAnimations.Default,
    content: @Composable () -> Unit
) {
    val registry = remember { GuideRegistry() }
    val arbiter = remember(guides.firstOrNull(), registry) {
        GuideEventArbiter(guides.toList(), registry)
    }

    var activeController: GuideController? = null
    var activeState: GuideState.Active? = null
    guides.forEach { guide ->
        val state = guide.state.collectAsState().value
        if (state is GuideState.Active) {
            activeController = guide
            activeState = state
        }
    }

    var shown by remember { mutableStateOf<GuideController?>(null) }
    var shownState by remember { mutableStateOf<GuideState.Active?>(null) }
    if (activeController != null && activeState != null) {
        shown = activeController
        shownState = activeState
    }

    val fadeAlpha by animateFloatAsState(
        targetValue = if (activeController != null) 1f else 0f,
        animationSpec = if (animations.enabled) {
            spring(
                dampingRatio = animations.fadeSpring.dampingRatio,
                stiffness = animations.fadeSpring.stiffness
            )
        } else {
            snap()
        },
        label = "guideFade"
    )

    BackHandler(enabled = activeController != null) {
        val controller = activeController ?: return@BackHandler
        if (controller.backBehavior == GuideBack.Finish) {
            controller.finish()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalGuideRegistry provides registry) {
            content()
        }

        val controller = shown
        val state = shownState
        if (controller != null && state != null && fadeAlpha > 0f) {
            GuideOverlay(
                controller = controller,
                state = state,
                registry = registry,
                arbiter = arbiter,
                fadeAlpha = fadeAlpha,
                nextTip = nextTip,
                animations = animations
            )
        }
    }
}
