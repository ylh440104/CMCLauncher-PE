package com.movtery.layer_controller.observable

import androidx.compose.ui.input.pointer.PointerId

class PointerEventBus {

    private val _activeWidgets = mutableMapOf<PointerId, MutableList<ObservableWidget>>()

    private val _swipeChainPointers = mutableSetOf<PointerId>()

    var checkOccupiedPointers: (PointerId) -> Boolean = { false }

    var markPointerAsMoveOnly: (PointerId) -> Unit = {}

    fun endPointer(pointerId: PointerId): List<ObservableWidget> {
        _swipeChainPointers.remove(pointerId)
        return _activeWidgets.remove(pointerId)?.toList() ?: emptyList()
    }

    fun activeWidgets(pointerId: PointerId): List<ObservableWidget> {
        return _activeWidgets[pointerId]?.toList() ?: emptyList()
    }

    fun addActiveWidget(pointerId: PointerId, widget: ObservableWidget) {
        _activeWidgets.getOrPut(pointerId) { mutableListOf() }.add(widget)
    }

    fun setActiveWidgets(pointerId: PointerId, widgets: List<ObservableWidget>) {
        _activeWidgets[pointerId] = widgets.toMutableList()
    }

    fun isInSwipeChain(pointerId: PointerId): Boolean = pointerId in _swipeChainPointers

    fun enterSwipeChain(pointerId: PointerId) {
        _swipeChainPointers.add(pointerId)
    }

    fun exitSwipeChain(pointerId: PointerId) {
        _swipeChainPointers.remove(pointerId)
    }
}
