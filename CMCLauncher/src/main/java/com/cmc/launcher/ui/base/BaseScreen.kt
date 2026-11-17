package com.cmc.launcher.ui.base

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import com.cmc.launcher.ui.screens.TitledNavKey
import com.cmc.launcher.utils.animation.SwapClock
import com.cmc.launcher.utils.animation.SwapTimeline
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BaseScreen(
    screenKey: TitledNavKey,
    currentKey: TitledNavKey?,
    useClassEquality: Boolean = false,
    content: @Composable (isVisible: Boolean) -> Unit,
) {
    val targetVisible = remember(currentKey, screenKey, useClassEquality) {
        isTagVisible(screenKey, currentKey, useClassEquality)
    }

    val visible = rememberVisibleState(targetVisible)

    BaseScreen(
        content = content,
        visible = visible
    )
}

@Composable
fun BaseScreen(
    vararg levels: Triple<TitledNavKey, TitledNavKey?, Boolean>,
    content: @Composable (isVisible: Boolean) -> Unit,
) {
    val targetVisible = remember(levels) {
        levels.all { (tag, currentKey, useReferenceEquality) ->
            isTagVisible(tag, currentKey, useReferenceEquality)
        }
    }

    val visible = rememberVisibleState(targetVisible)

    BaseScreen(
        content = content,
        visible = visible
    )
}

@Composable
fun BaseScreen(
    levels1: List<Pair<Class<out TitledNavKey>, TitledNavKey?>>,
    vararg levels2: Triple<TitledNavKey, TitledNavKey?, Boolean>,
    content: @Composable (isVisible: Boolean) -> Unit,
) {
    val targetVisible = remember(levels1, levels2) {
        val v1 = levels1.all { (key, currentKey) ->
            isTagVisible(key, currentKey)
        }
        val v2 = levels2.all { (key, currentKey, useClassEquality) ->
            isTagVisible(key, currentKey, useClassEquality)
        }
        v1 && v2
    }

    val visible = rememberVisibleState(targetVisible)

    BaseScreen(
        content = content,
        visible = visible
    )
}

@Composable
private fun rememberVisibleState(targetVisible: Boolean): Boolean {
    val visibleState = remember { mutableStateOf(false) }

    var exitStartAt by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(targetVisible) {
        if (targetVisible) {

            val reversingExit = exitStartAt >= 0 &&
                    System.nanoTime() / 1_000_000 - exitStartAt <
                    SwapTimeline.swapTime(SwapTimeline.EXIT_WINDOW)
            exitStartAt = -1L
            if (!reversingExit) awaitEnterStart()
        } else if (visibleState.value) {

            exitStartAt = System.nanoTime() / 1_000_000
        }
        visibleState.value = targetVisible
    }
    return visibleState.value
}

private suspend fun awaitEnterStart() {
    val waitMillis = SwapClock.enterAtMillis - System.nanoTime() / 1_000_000
    if (waitMillis > 0) delay(waitMillis.milliseconds)
}

@Composable
private fun BaseScreen(
    content: @Composable (isVisible: Boolean) -> Unit,
    visible: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        content(visible)

        if (!visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {}
                    )
            )
        }
    }
}

private fun isTagVisible(key: Class<out TitledNavKey>, current: TitledNavKey?): Boolean {
    return key.isInstance(current)
}

private fun isTagVisible(key: TitledNavKey, current: TitledNavKey?, useClassEquality: Boolean): Boolean {
    return when {
        current == null -> false
        useClassEquality -> key::class == current::class
        else -> key == current
    }
}
