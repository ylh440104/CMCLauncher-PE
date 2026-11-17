package com.movtery.guide

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

fun Modifier.guideLazyList(
    state: LazyListState,
    keyOf: (GuideKey) -> Any?
): Modifier = composed {
    val registry = LocalGuideRegistry.current ?: return@composed this
    val currentKeyOf by rememberUpdatedState(keyOf)
    DisposableEffect(registry) {
        val attempted = mutableSetOf<GuideKey>()
        val handler: GuideScrollHandler = handler@{ key ->
            val target = currentKeyOf(key) ?: return@handler false
            if (!attempted.add(key)) return@handler false
            seekToItem(registry, state, key, target)
        }
        registry.addScrollHandler(handler)
        onDispose { registry.removeScrollHandler(handler) }
    }
    this
}

private const val MAX_SEEK_ATTEMPTS = 64

private suspend fun seekToItem(
    registry: GuideRegistry,
    state: LazyListState,
    key: GuideKey,
    itemKey: Any
): Boolean {

    var frames = 0
    while (state.layoutInfo.totalItemsCount == 0 && frames < 30) {
        withFrameNanos { }
        frames++
    }
    if (state.layoutInfo.totalItemsCount == 0) return false

    var index = 0
    var attempts = 0
    while (attempts < MAX_SEEK_ATTEMPTS) {
        state.scrollToItem(index)

        withFrameNanos { }
        withFrameNanos { }

        if (registry.rectsFor(key).isNotEmpty()) return true

        val visible = state.layoutInfo.visibleItemsInfo
        val target = visible.firstOrNull { it.key == itemKey }
        if (target != null) {

            state.scrollToItem(target.index)
            withFrameNanos { }
            withFrameNanos { }
            if (registry.rectsFor(key).isNotEmpty()) return true
        }

        val lastIndex = visible.lastOrNull()?.index ?: return false
        if (lastIndex >= state.layoutInfo.totalItemsCount - 1) return false
        index = lastIndex + 1
        attempts++
    }
    return false
}
