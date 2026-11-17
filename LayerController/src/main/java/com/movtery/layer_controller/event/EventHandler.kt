package com.movtery.layer_controller.event

import com.movtery.layer_controller.observable.ObservableControlLayer

class EventHandler(
    private val handle: (event: ClickEvent, pressed: Boolean) -> Unit = { _, _ -> }
) {

    internal fun onKeyPressed(
        clickEvents: List<ClickEvent>,
        isPressed: Boolean,
        handle: (ClickEvent) -> Boolean = { true }
    ) {
        for (event in clickEvents) {
            if (handle(event)) handle(event, isPressed)
        }
    }

    internal fun onSwitchLayer(
        clickEvent: ClickEvent,
        allLayers: List<ObservableControlLayer>,
        switch: (ObservableControlLayer) -> Unit,
        show: (ObservableControlLayer) -> Unit,
        hide: (ObservableControlLayer) -> Unit
    ) {
        fun findLayer() = allLayers.find { it.uuid == clickEvent.key }

        when (clickEvent.type) {
            ClickEvent.Type.SwitchLayer -> findLayer()?.let { switch(it) }
            ClickEvent.Type.ShowLayer -> findLayer()?.let { show(it) }
            ClickEvent.Type.HideLayer -> findLayer()?.let { hide(it) }
            else -> {}
        }
    }
}
