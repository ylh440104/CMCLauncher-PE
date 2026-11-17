package com.movtery.layer_controller.observable

import com.movtery.layer_controller.data.JoystickDirection
import com.movtery.layer_controller.event.ClickEvent

abstract class ObservableClickEventsProvider {
    abstract val clickEvents: List<ClickEvent>
    abstract fun onRemoveAllEvents(events: List<ClickEvent>)
    abstract fun onRemoveAllEvents(type: ClickEvent.Type)
    abstract fun onAddEvent(event: ClickEvent)
    abstract fun onRemoveEvent(event: ClickEvent)
}

fun clickEventsProvider(data: ObservableNormalData): ObservableClickEventsProvider {
    return object : ObservableClickEventsProvider() {
        override val clickEvents: List<ClickEvent>
            get() = data.clickEvents
        override fun onRemoveAllEvents(events: List<ClickEvent>) {
            data.removeAllEvent(events)
        }
        override fun onRemoveAllEvents(type: ClickEvent.Type) {
            data.removeAllEvent(type)
        }
        override fun onAddEvent(event: ClickEvent) {
            data.addEvent(event)
        }
        override fun onRemoveEvent(event: ClickEvent) {
            data.removeEvent(event)
        }
    }
}

fun joystickLockEventsProvider(data: ObservableJoystickData): ObservableClickEventsProvider {
    return object : ObservableClickEventsProvider() {
        override val clickEvents: List<ClickEvent>
            get() = data.lockEvents
        override fun onRemoveAllEvents(events: List<ClickEvent>) {
            events.forEach { event ->
                data.removeLockEvent { it == event }
            }
        }
        override fun onRemoveAllEvents(type: ClickEvent.Type) {
            data.removeLockEvent { it.type == type }
        }
        override fun onAddEvent(event: ClickEvent) {
            data.addLockEvent(event)
        }
        override fun onRemoveEvent(event: ClickEvent) {
            data.removeLockEvent { it == event }
        }
    }
}

fun joystickDirectionEventsProvider(
    data: ObservableJoystickData,
    direction: JoystickDirection?,
): ObservableClickEventsProvider {
    return object : ObservableClickEventsProvider() {
        override val clickEvents: List<ClickEvent>
            get() = direction?.let { data.directionEvents[it] } ?: emptyList()
        override fun onRemoveAllEvents(events: List<ClickEvent>) {
            events.forEach { event ->
                data.removeDirectionEvent(direction) { it == event }
            }
        }
        override fun onRemoveAllEvents(type: ClickEvent.Type) {
            data.removeDirectionEvent(direction) { it.type == type }
        }
        override fun onAddEvent(event: ClickEvent) {
            data.addDirectionEvent(direction, event)
        }
        override fun onRemoveEvent(event: ClickEvent) {
            data.removeDirectionEvent(direction) { it == event }
        }
    }
}
