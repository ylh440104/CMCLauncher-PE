package com.cmc.launcher.ui.screens.main.control_editor

import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.movtery.layer_controller.observable.ObservableClickEventsProvider
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableJoystickStyle
import com.movtery.layer_controller.observable.ObservableTranslatableString
import com.movtery.layer_controller.observable.ObservableWidget
import com.cmc.launcher.game.control.CabinetStyleItem
import com.cmc.launcher.ui.screens.main.control_editor.edit_widget.SelectedWidgetData
import com.cmc.launcher.ui.screens.main.control_editor.style_cabinet.OpenCabinetSource
import com.cmc.launcher.viewmodel.EditorViewModel

sealed interface EditorOperation {

    data object None : EditorOperation

    data object SelectButton : EditorOperation

    data class EditLayer(val layer: ObservableControlLayer) : EditorOperation

    data class DeleteLayer(val layer: ObservableControlLayer) : EditorOperation

    data object OpenStyleList : EditorOperation

    data object CreateStyle : EditorOperation

    data class EditButtonStyle(val style: ObservableButtonStyle) : EditorOperation

    data class DeleteButtonStyle(val style: ObservableButtonStyle) : EditorOperation

    data class EditJoystickStyle(val style: ObservableJoystickStyle) : EditorOperation

    data class DeleteJoystickStyle(val style: ObservableJoystickStyle) : EditorOperation

    data object OpenJoystickStyleList : EditorOperation

    data object CreateJoystickStyle : EditorOperation

    data class OpenStyleCabinet(val source: OpenCabinetSource) : EditorOperation

    data class FavoriteButtonStyleConflict(val style: ObservableButtonStyle) : EditorOperation

    data class FavoriteJoystickStyleConflict(val style: ObservableJoystickStyle) : EditorOperation

    data class AddToLayoutButtonStyleConflict(
        val item: CabinetStyleItem.Button,
        val source: OpenCabinetSource
    ) : EditorOperation

    data class AddToLayoutJoystickStyleConflict(
        val item: CabinetStyleItem.Joystick,
        val source: OpenCabinetSource
    ) : EditorOperation

    data class EditCabinetButtonStyle(
        val item: CabinetStyleItem.Button,
        val source: OpenCabinetSource
    ) : EditorOperation

    data class EditCabinetJoystickStyle(
        val item: CabinetStyleItem.Joystick,
        val source: OpenCabinetSource
    ) : EditorOperation

    data object Saving : EditorOperation

    data class SaveFailed(val error: Throwable) : EditorOperation
}

sealed interface EditorWidgetOperation {

    data object None : EditorWidgetOperation

    data class CloneButton(
        val data: ObservableWidget,
        val layer: ObservableControlLayer
    ) : EditorWidgetOperation

    data class DeleteButton(
        val data: ObservableWidget,
        val layer: ObservableControlLayer
    ) : EditorWidgetOperation

    data class EditWidgetText(val string: ObservableTranslatableString) : EditorWidgetOperation

    data class SwitchLayersVisibility(
        val data: ObservableClickEventsProvider,
        val type: ClickEvent.Type
    ) : EditorWidgetOperation

    data class SendText(val data: ObservableClickEventsProvider) : EditorWidgetOperation
}

sealed interface EditorWarningOperation {

    data object None : EditorWarningOperation

    data object WarningNoLayers : EditorWarningOperation

    data object WarningNoSelectLayer : EditorWarningOperation
}
