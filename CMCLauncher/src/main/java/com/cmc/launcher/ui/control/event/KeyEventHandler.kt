package com.cmc.launcher.ui.control.event

import java.util.concurrent.ConcurrentHashMap

@Deprecated("怀疑 KeyEventHandler 会导致按键事件混乱，导致游戏乱操作，暂时移除 #894")
class KeyEventHandler(
    private val handle: (key: String, pressed: Boolean) -> Unit
) {

    private val keyEvents = ConcurrentHashMap<String, Int>()

    fun pressKey(key: String) {
        var shouldPress = false
        keyEvents.compute(key) { _, old ->
            val oldCount = old ?: 0
            shouldPress = oldCount == 0
            oldCount + 1
        }
        if (shouldPress) {
            handle(key, true)
        }
    }

    fun releaseKey(key: String) {
        var shouldRelease = false
        keyEvents.compute(key) { _, old ->
            when {
                old == null || old <= 0 -> {
                    shouldRelease = false
                    null
                }
                old == 1 -> {
                    shouldRelease = true
                    null
                }
                else -> {
                    shouldRelease = false
                    old - 1
                }
            }
        }
        if (shouldRelease) {
            handle(key, false)
        }
    }

    fun clearEvent() {
        val allKeys = keyEvents.keys.toSet()
        keyEvents.clear()
        allKeys.forEach {
            handle(it, false)
        }
    }
}
