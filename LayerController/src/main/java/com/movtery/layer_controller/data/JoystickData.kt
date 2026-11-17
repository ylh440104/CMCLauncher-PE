package com.movtery.layer_controller.data

import com.movtery.inputmap.keycodes.ControlEventKeycode.GLFW_KEY_A
import com.movtery.inputmap.keycodes.ControlEventKeycode.GLFW_KEY_D
import com.movtery.inputmap.keycodes.ControlEventKeycode.GLFW_KEY_LEFT_CONTROL
import com.movtery.inputmap.keycodes.ControlEventKeycode.GLFW_KEY_S
import com.movtery.inputmap.keycodes.ControlEventKeycode.GLFW_KEY_W
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.observable.Modifiable
import com.movtery.layer_controller.utils.checkInRange
import com.movtery.layer_controller.utils.getAButtonUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val JOYSTICK_MIN_SIZE_DP = 20f

const val JOYSTICK_MIN_SIZE_PERCENTAGE = 2000

val JOYSTICK_KNOB_SIZE_RANGE: ClosedFloatingPointRange<Float> = 0.1f..1.0f

val JOYSTICK_DEAD_ZONE_RANGE: ClosedFloatingPointRange<Float> = 0.0f..0.9f

val JOYSTICK_LOCK_THRESHOLD_RANGE: ClosedFloatingPointRange<Float> = 0.0f..1.0f

@Serializable
data class JoystickData(
    @SerialName("uuid")
    val uuid: String,
    @SerialName("position")
    val position: ButtonPosition,
    @SerialName("sizeType")
    val sizeType: ButtonSize.Type = ButtonSize.Type.Percentage,
    @SerialName("sizeDp")
    val sizeDp: Float = 200f,
    @SerialName("sizePercentage")
    val sizePercentage: Int = 2500,
    @SerialName("visibilityType")
    val visibilityType: VisibilityType = VisibilityType.ALWAYS,
    @SerialName("joystickStyleId")
    val joystickStyleId: String? = null,
    @SerialName("deadZoneRatio")
    val deadZoneRatio: Float = 0.5f,
    @SerialName("lockThreshold")
    val lockThreshold: Float = 0.3f,
    @SerialName("canLock")
    val canLock: Boolean = true,
    @SerialName("triggerMode")
    val triggerMode: JoystickTriggerMode = JoystickTriggerMode.DRAG,
    @SerialName("directionEvents")
    val directionEvents: Map<JoystickDirection, List<ClickEvent>> = DefaultDirectionEvents,
    @SerialName("lockEvents")
    val lockEvents: List<ClickEvent> = DefaultLockEvents,
): Widget, Modifiable<JoystickData> {
    init {
        require(sizeType != ButtonSize.Type.WrapContent) { "JoystickData does not support WrapContent size type" }
        checkInRange("deadZoneRatio", deadZoneRatio, JOYSTICK_DEAD_ZONE_RANGE)
        checkInRange("lockThreshold", lockThreshold, JOYSTICK_LOCK_THRESHOLD_RANGE)
    }

    fun toButtonSize(): ButtonSize {
        val clampedDp = sizeDp.coerceAtLeast(JOYSTICK_MIN_SIZE_DP)
        val clampedPercentage = sizePercentage.coerceAtLeast(JOYSTICK_MIN_SIZE_PERCENTAGE)
        return when (sizeType) {
            ButtonSize.Type.Dp -> ButtonSize(
                type = ButtonSize.Type.Dp,
                widthDp = clampedDp,
                heightDp = clampedDp,
                widthPercentage = MIN_SIZE_PERCENTAGE,
                heightPercentage = MIN_SIZE_PERCENTAGE,
                widthReference = ButtonSize.Reference.ScreenHeight,
                heightReference = ButtonSize.Reference.ScreenHeight
            )
            ButtonSize.Type.Percentage -> ButtonSize(
                type = ButtonSize.Type.Percentage,
                widthDp = clampedDp,
                heightDp = clampedDp,
                widthPercentage = clampedPercentage,
                heightPercentage = clampedPercentage,
                widthReference = ButtonSize.Reference.ScreenHeight,
                heightReference = ButtonSize.Reference.ScreenHeight
            )
            else -> ButtonSize(
                type = ButtonSize.Type.Dp,
                widthDp = 200f,
                heightDp = 200f,
                widthPercentage = MIN_SIZE_PERCENTAGE,
                heightPercentage = MIN_SIZE_PERCENTAGE,
                widthReference = ButtonSize.Reference.ScreenHeight,
                heightReference = ButtonSize.Reference.ScreenHeight
            )
        }
    }

    override fun isModified(other: JoystickData): Boolean {
        return this.uuid != other.uuid ||
                this.position.isModified(other.position) ||
                this.sizeType != other.sizeType ||
                this.sizeDp != other.sizeDp ||
                this.sizePercentage != other.sizePercentage ||
                this.visibilityType != other.visibilityType ||
                this.joystickStyleId != other.joystickStyleId ||
                this.deadZoneRatio != other.deadZoneRatio ||
                this.lockThreshold != other.lockThreshold ||
                this.canLock != other.canLock ||
                this.triggerMode != other.triggerMode ||
                this.directionEvents != other.directionEvents ||
                this.lockEvents != other.lockEvents
    }
}

val DefaultDirectionEvents = buildMap {
    val forward = ClickEvent(ClickEvent.Type.Key, GLFW_KEY_W)
    val back = ClickEvent(ClickEvent.Type.Key, GLFW_KEY_S)
    val left = ClickEvent(ClickEvent.Type.Key, GLFW_KEY_A)
    val right = ClickEvent(ClickEvent.Type.Key, GLFW_KEY_D)

    put(JoystickDirection.North, listOf(forward))
    put(JoystickDirection.NorthEast, listOf(forward, right))
    put(JoystickDirection.NorthWest, listOf(forward, left))

    put(JoystickDirection.South, listOf(back))
    put(JoystickDirection.SouthEast, listOf(back, right))
    put(JoystickDirection.SouthWest, listOf(back, left))

    put(JoystickDirection.East, listOf(right))
    put(JoystickDirection.West, listOf(left))
}

val DefaultLockEvents = buildList {
    add(ClickEvent(ClickEvent.Type.Key, GLFW_KEY_LEFT_CONTROL))
}

fun JoystickData.cloneNew(): JoystickData = JoystickData(
    uuid = getAButtonUUID(),
    position = CenterPosition,
    sizeType = sizeType,
    sizeDp = sizeDp,
    sizePercentage = sizePercentage,
    visibilityType = visibilityType,
    joystickStyleId = joystickStyleId,
    deadZoneRatio = deadZoneRatio,
    lockThreshold = lockThreshold,
    canLock = canLock,
    triggerMode = triggerMode,
    directionEvents = directionEvents,
    lockEvents = lockEvents
)
