package com.movtery.layer_controller.observable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import com.movtery.layer_controller.event.EventHandler

class TouchProcessor(
    private val eventHandler: EventHandler,
    private val widgetPosition: (ObservableWidget) -> Offset,
) {

    private val hitTest: (widget: ObservableWidget, position: Offset) -> Boolean = { widget, position ->
        val size = widget.internalRenderSize
        val offset = widgetPosition(widget)
        position.x in offset.x..(offset.x + size.width) &&
                position.y in offset.y..(offset.y + size.height)
    }

    fun processFrame(
        session: PointerEventBus,
        change: PointerInputChange,
        visibleWidgets: List<ObservableWidget>,
        allLayers: List<ObservableControlLayer>,
        consumeEvent: (PointerInputChange) -> Unit,
        markPointerAsMoveOnly: (PointerId) -> Unit,
    ) {
        val pointerId = change.id
        val position = change.position

        val targets = findTargets(visibleWidgets, position)
        handleOutOfBounds(session, pointerId, position, allLayers)

        routeToTargets(
            session = session,
            change = change,
            pointerId = pointerId,
            targets = targets,
            allLayers = allLayers,
            consumeEvent = consumeEvent,
            markPointerAsMoveOnly = markPointerAsMoveOnly,
        )
    }

    private fun findTargets(
        visibleWidgets: List<ObservableWidget>,
        position: Offset,
    ): List<ObservableWidget> {
        var hitList: MutableList<ObservableWidget>? = null
        var deepIndex = -1

        for (widget in visibleWidgets) {
            if (!widget.canTouch() || !hitTest(widget, position)) continue
            val list = hitList ?: mutableListOf<ObservableWidget>().also { hitList = it }
            list.add(widget)

            if (deepIndex == -1 && widget.supportsDeepTouchDetection()) deepIndex = list.lastIndex
        }

        val hits = hitList ?: return emptyList()
        if (deepIndex == -1) return hits

        for (i in hits.lastIndex downTo deepIndex) {
            if (i > deepIndex || hits[i].canProcess()) hits.removeAt(i)
        }
        return hits
    }

    private fun handleOutOfBounds(
        session: PointerEventBus,
        pointerId: PointerId,
        position: Offset,
        allLayers: List<ObservableControlLayer>,
    ) {
        val widgets = session.activeWidgets(pointerId)
        if (widgets.isEmpty()) return

        val backInBounds = mutableListOf<ObservableWidget>()
        val removed = mutableListOf<ObservableWidget>()

        for (widget in widgets) {
            if (!widget.behavior.releaseOnOutOfBounds) continue

            if (!hitTest(widget, position)) {
                widget.onReleaseEvent(eventHandler, allLayers)
                removed.add(widget)
            } else {

                backInBounds.add(widget)
            }
        }

        if (removed.isNotEmpty()) {
            session.setActiveWidgets(pointerId, widgets - removed)
        }

        for (widget in backInBounds) {
            widget.onPointerBackInBounds(eventHandler, allLayers)
        }

        val currentWidgets = session.activeWidgets(pointerId)
        if (
            currentWidgets.isEmpty() &&

            widgets.any { it.behavior is InteractionBehavior.Swipable }
        ) {
            session.enterSwipeChain(pointerId)
        }

        if (currentWidgets.isNotEmpty() && session.isInSwipeChain(pointerId)) {
            session.exitSwipeChain(pointerId)
        }
    }

    private fun routeToTargets(
        session: PointerEventBus,
        change: PointerInputChange,
        pointerId: PointerId,
        targets: List<ObservableWidget>,
        allLayers: List<ObservableControlLayer>,
        consumeEvent: (PointerInputChange) -> Unit,
        markPointerAsMoveOnly: (PointerId) -> Unit,
    ) {
        if (targets.isEmpty()) return

        val activeWidgets = session.activeWidgets(pointerId)

        for (target in targets) {
            if (target.canProcess()) return

            if (
                session.isInSwipeChain(pointerId) &&
                !target.behavior.canBeSwipedTo
            ) {
                continue
            }

            target.onTouchEvent(
                eventHandler = eventHandler,
                allLayers = allLayers,
                activeWidgets = activeWidgets,
                addThis = {
                    session.addActiveWidget(pointerId, target)
                },
                consumeEvent = { shouldConsume ->
                    if (shouldConsume) {
                        consumeEvent(change)
                    } else {
                        markPointerAsMoveOnly(pointerId)
                    }
                },
            )
        }
    }
}
