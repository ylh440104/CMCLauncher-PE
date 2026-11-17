package com.movtery.layer_controller.observable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.TextData
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.data.cloneNew
import com.movtery.layer_controller.event.EventHandler

open class ObservableTextData(data: TextData) : ObservableWidget() {
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

    override val behavior: InteractionBehavior
        get() = InteractionBehavior.Press

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

    override fun canTouch(): Boolean = false

    override fun onCompositionStart(eventHandler: EventHandler?) {

    }

    override fun onCompositionDispose(eventHandler: EventHandler?) {

    }

    override fun onCheckVisibilityType(): VisibilityType {
        return visibilityType
    }

    override fun supportsDeepTouchDetection(): Boolean {
        return false
    }

    override fun canProcess(): Boolean {
        return false
    }

    override fun onTouchEvent(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>,
        activeWidgets: List<ObservableWidget>,
        addThis: () -> Unit,
        consumeEvent: (Boolean) -> Unit
    ) {

    }

    override fun isReleaseOnOutOfBounds(): Boolean {
        return false
    }

    override fun onPointerBackInBounds(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>
    ) {

    }

    override fun onReleaseEvent(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>
    ) {

    }

    fun packText(): TextData {
        return TextData(
            text = text.pack(),
            uuid = uuid,
            position = position,
            buttonSize = buttonSize,
            buttonStyle = buttonStyle,
            textAlignment = textAlignment,
            textBold = textBold,
            textItalic = textItalic,
            textUnderline = textUnderline,
            visibilityType = visibilityType
        )
    }
}

fun ObservableTextData.cloneText(): ObservableTextData {
    return ObservableTextData(packText().cloneNew())
}
