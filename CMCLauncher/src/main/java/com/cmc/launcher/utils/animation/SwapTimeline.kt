package com.cmc.launcher.utils.animation

object SwapTimeline {

    const val EXIT_ITEM = 70

    const val EXIT_ALPHA = 70

    const val EXIT_STAGGER = 15

    const val EXIT_STAGGER_CAP = 40

    const val EXIT_WINDOW = EXIT_ITEM + EXIT_STAGGER_CAP

    const val GAP = 30

    const val ENTER_ITEM = 350

    const val ENTER_ALPHA = 200

    const val ENTER_STAGGER = 25

    const val ENTER_STAGGER_CAP = 250

    const val CONTENT_BASE = 350

    const val EXIT_DISTANCE_SCALE = 6f / 16f

    fun isSwapClosed(): Boolean = true

    fun speedScale(): Float = 1f

    fun time(ms: Int): Int = 0

    fun swapTime(ms: Int): Int = 0

    fun enterStagger(index: Int): Int = 0

    fun exitStagger(index: Int): Int = 0
}

class SwapWindow(
    val exitDelayMillis: Int,
    val enterDelayMillis: Int
)

object SwapClock {

    @Volatile
    private var swapInitial: Any? = null

    @Volatile
    private var swapTarget: Any? = null

    @Volatile
    private var exitEndAtMillis: Long = 0

    @Volatile
    var enterAtMillis: Long = 0
        private set

    @Synchronized
    fun record(initial: Any?, target: Any?): SwapWindow {
        val now = System.nanoTime() / 1_000_000
        if (initial != target) {

            val continuing = now < enterAtMillis &&
                    (initial == swapInitial || initial == swapTarget ||
                            target == swapInitial || target == swapTarget)
            if (!continuing) {
                swapInitial = initial
                swapTarget = target
                exitEndAtMillis = now + SwapTimeline.swapTime(SwapTimeline.EXIT_WINDOW)
                enterAtMillis = exitEndAtMillis + SwapTimeline.swapTime(SwapTimeline.GAP)
            }
        }
        return SwapWindow(
            exitDelayMillis = (exitEndAtMillis - now).coerceAtLeast(0).toInt(),
            enterDelayMillis = (enterAtMillis - now).coerceAtLeast(0).toInt()
        )
    }
}
