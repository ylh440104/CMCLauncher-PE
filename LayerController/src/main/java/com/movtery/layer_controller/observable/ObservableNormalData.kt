package com.movtery.layer_controller.observable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.NormalData
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.data.cloneNew
import com.movtery.layer_controller.data.filterValidEvent
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.event.EventHandler

class ObservableNormalData(data: NormalData) : ObservableWidget() {
    val text = ObservableTranslatableString(data.text)
    val uuid: String = data.uuid
    var position by mutableStateOf(data.position)
    var buttonSize by mutableStateOf(data.buttonSize)
    var buttonStyle by mutableStateOf(data.buttonStyle)
    var textAlignment by mutableStateOf(data.textAlignment)
    var textBold by mutableStateOf(data.textBold)
    var textItalic by mutableStateOf(data.textItalic)
    var textUnderline by mutableStateOf(data.textUnderline)
    var visibilityType by mutableStateOf(data.visibilityType)
    var clickEvents by mutableStateOf(data.clickEvents)
    var isSwipple by mutableStateOf(data.isSwipple)
    var isPenetrable by mutableStateOf(data.isPenetrable)
    var isToggleable by mutableStateOf(data.isToggleable)

    override val behavior: InteractionBehavior
        get() = InteractionBehavior.from(
            isSwipple = isSwipple,
            isToggleable = isToggleable
        )

    var isPressed by mutableStateOf(false)
        private set

    private fun pressStart(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>
    ) {
        when (behavior) {
            is InteractionBehavior.Toggle -> isPressed = !isPressed
            else -> {
                if (isPressed) return
                isPressed = true
            }
        }
        eventHandler.onKeyPressed(clickEvents, isPressed) { event ->
            eventHandler.onSwitchLayer(
                clickEvent = event,
                allLayers = allLayers,
                switch = { layer ->
                    layer.hide = !layer.hide
                },
                show = { layer ->
                    layer.hide = false
                },
                hide = { layer ->
                    layer.hide = true
                }
            )
            true
        }
    }

    override val internalRenderPosition: ButtonPosition
        get() = position

    override fun putRenderPosition(position: ButtonPosition) {
        this.position = position
    }

    override fun putWidgetSize(size: ButtonSize) {
        this.buttonSize = size
    }

    override val styleId: String?
        get() = buttonStyle

    override val widgetSize: ButtonSize
        get() = buttonSize

    override fun onCompositionStart(eventHandler: EventHandler?) {

    }

    override fun onCompositionDispose(eventHandler: EventHandler?) {
        if (isPressed) {

            isPressed = false
            eventHandler?.onKeyPressed(clickEvents, isPressed)
        }
    }

    override fun onCheckVisibilityType(): VisibilityType {
        return visibilityType
    }

    override fun supportsDeepTouchDetection(): Boolean {

        return !isSwipple || !(isSwipple && isPenetrable)
    }

    override fun canProcess(): Boolean {

        return isPenetrable && isSwipple
    }

    override fun onTouchEvent(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>,
        activeWidgets: List<ObservableWidget>,
        addThis: () -> Unit,
        consumeEvent: (Boolean) -> Unit
    ) {
        if (activeWidgets.isEmpty()) {

            addThis()
            consumeEvent(!isPenetrable)
            pressStart(eventHandler, allLayers)
        } else if (this !in activeWidgets && behavior.canBeSwipedTo) {

            if (activeWidgets.none { it.behavior.blocksSwipeChain }) {
                addThis()
                pressStart(eventHandler, allLayers)
            }
        }
    }

    override fun isReleaseOnOutOfBounds(): Boolean = behavior.releaseOnOutOfBounds

    override fun onPointerBackInBounds(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>
    ) {
        if (behavior.releaseOnOutOfBounds) {
            pressStart(eventHandler, allLayers)
        }
    }

    override fun onReleaseEvent(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>
    ) {
        if (behavior is InteractionBehavior.Toggle || !isPressed) return
        isPressed = false
        eventHandler.onKeyPressed(clickEvents, isPressed)
    }

    fun addEvent(event: ClickEvent) {
        if (clickEvents.none { it.type == event.type && it.key == event.key }) {
            clickEvents = clickEvents + event
        }
    }

    fun removeEvent(event: ClickEvent) {
        removeEvent(event.type, event.key)
    }

    fun removeEvent(eventType: ClickEvent.Type, key: String) {
        clickEvents = clickEvents.filterNot { it.type == eventType && it.key == key }
    }

    fun removeAllEvent(eventType: ClickEvent.Type) {
        clickEvents = clickEvents.filterNot { it.type == eventType }
    }

    fun removeAllEvent(events: Collection<ClickEvent>) {
        val keysToRemove = events.map { it.type to it.key }.toSet()
        clickEvents = clickEvents.filterNot { (it.type to it.key) in keysToRemove }
    }

    fun packNormal(): NormalData {
        return NormalData(
            text = text.pack(),
            uuid = uuid,
            position = position,
            buttonSize = buttonSize,
            buttonStyle = buttonStyle,
            textAlignment = textAlignment,
            textBold = textBold,
            textItalic = textItalic,
            textUnderline = textUnderline,
            visibilityType = visibilityType,
            _clickEvents = clickEvents.filterValidEvent(),
            isSwipple = isSwipple,
            isPenetrable = isPenetrable,
            isToggleable = isToggleable
        )
    }
}

fun ObservableNormalData.cloneNormal(): ObservableNormalData {
    return ObservableNormalData(packNormal().cloneNew())
}
