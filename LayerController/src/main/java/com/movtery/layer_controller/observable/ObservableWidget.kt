package com.movtery.layer_controller.observable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.unit.IntSize
import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.event.EventHandler

abstract class ObservableWidget {

    var isEditingPos by mutableStateOf(false)

    var movingOffset by mutableStateOf(Offset.Zero)

    internal var internalRenderSize by mutableStateOf(IntSize.Zero)

    internal abstract val internalRenderPosition: ButtonPosition

    internal abstract fun putRenderPosition(position: ButtonPosition)

    internal abstract val styleId: String?

    internal abstract val widgetSize: ButtonSize

    internal abstract fun putWidgetSize(size: ButtonSize)

    abstract val behavior: InteractionBehavior

    open fun canTouch(): Boolean = true

    open fun Modifier.touchModifier(
        pointerEventBus: PointerEventBus,
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>,
        screenSize: IntSize,
        onOccupiedPointer: (PointerId) -> Unit = {},
        onReleasePointer: (PointerId) -> Unit = {}
    ): Modifier = this

    abstract fun onCompositionStart(eventHandler: EventHandler?)

    abstract fun onCompositionDispose(eventHandler: EventHandler?)

    abstract fun onCheckVisibilityType(): VisibilityType

    abstract fun supportsDeepTouchDetection(): Boolean

    abstract fun canProcess(): Boolean

    abstract fun onTouchEvent(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>,
        activeWidgets: List<ObservableWidget>,
        addThis: () -> Unit,
        consumeEvent: (Boolean) -> Unit
    )

    abstract fun isReleaseOnOutOfBounds(): Boolean

    abstract fun onPointerBackInBounds(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>
    )

    abstract fun onReleaseEvent(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>
    )
}
