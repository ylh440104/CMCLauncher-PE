package com.cmc.launcher.utils.animation


/**
 * 页面切换的串行时间轴
 */
object SwapTimeline {
    /** 旧页单项退场时长 */
    const val EXIT_ITEM = 70
    /** 旧页单项淡出时长 */
    const val EXIT_ALPHA = 70
    /** 旧页退场相邻项错峰间隔 */
    const val EXIT_STAGGER = 15
    /** 旧页退场错峰封顶 */
    const val EXIT_STAGGER_CAP = 40
    /** 旧页独占退场的总窗口 */
    const val EXIT_WINDOW = EXIT_ITEM + EXIT_STAGGER_CAP
    /** 旧页退场结束到新页进场的间隔 */
    const val GAP = 30



    /** 新页单项位移时长 */
    const val ENTER_ITEM = 350
    /** 新页单项淡入时长 */
    const val ENTER_ALPHA = 200
    /** 新页进场相邻项错峰间隔 */
    const val ENTER_STAGGER = 25
    /** 新页进场错峰封顶 */
    const val ENTER_STAGGER_CAP = 250



    /** 通用内容动画的基准时长 */
    const val CONTENT_BASE = 350
    /** 退场位移距离相对进场位移距离的比例 */
    const val EXIT_DISTANCE_SCALE = 6f / 16f



    /**
     * @return 页面切换动画是否关闭
     */
    fun isSwapClosed(): Boolean = true
    /**
     * @return 速度倍率
     */
    fun speedScale(): Float = 1f
    /**
     * @return 时长恒为 0
     */
    fun time(ms: Int): Int = 0
    /**
     * @return 页面切换时长恒为 0
     */
    fun swapTime(ms: Int): Int = 0
    /**
     * @return 进场错峰延迟恒为 0
     */
    fun enterStagger(index: Int): Int = 0
    /**
     * @return 退场错峰延迟恒为 0
     */
    fun exitStagger(index: Int): Int = 0
}


/**
 * 一次页面切换的延迟窗口
 * @param exitDelayMillis 旧页场景层开始淡出前的等待时长
 * @param enterDelayMillis 新页场景层开始淡入前的等待时长
 */
class SwapWindow(
    val exitDelayMillis: Int,
    val enterDelayMillis: Int
)

object SwapClock {
    /** 当前切换段落的起始场景；无进行中的切换时为 null */
    @Volatile
    private var swapInitial: Any? = null

    /** 当前切换段落的目标场景；无进行中的切换时为 null */
    @Volatile
    private var swapTarget: Any? = null

    /** 当前切换中，旧内容完成退场的时刻（ms） */
    @Volatile
    private var exitEndAtMillis: Long = 0

    /** 当前切换中，新内容允许进场的时刻（ms） */
    @Volatile
    var enterAtMillis: Long = 0
        private set

    /**
     * 场景层切换段落变更时打点，计算本次切换的退场/进场延迟
     * @param initial 切换段落的起始场景
     * @param target 切换段落的目标场景
     */
    @Synchronized
    fun record(initial: Any?, target: Any?): SwapWindow {
        val now = System.nanoTime() / 1_000_000
        if (initial != target) {
            // 场景真正发生变更才算一次切换
            // 否则只是段落回稳（如首次组合、切换动画播放完毕）
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
