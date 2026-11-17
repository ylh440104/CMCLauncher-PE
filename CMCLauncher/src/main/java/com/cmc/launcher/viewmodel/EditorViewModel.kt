package com.cmc.launcher.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.movtery.layer_controller.data.ButtonStyle
import com.movtery.layer_controller.data.HideLayerWhen
import com.movtery.layer_controller.data.JoystickStyle
import com.movtery.layer_controller.data.cloneNew
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.createNewLayer
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.movtery.layer_controller.observable.ObservableClickEventsProvider
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.observable.ObservableJoystickData
import com.movtery.layer_controller.observable.ObservableJoystickStyle
import com.movtery.layer_controller.observable.ObservableNormalData
import com.movtery.layer_controller.observable.ObservableTextData
import com.movtery.layer_controller.observable.ObservableTranslatableString
import com.movtery.layer_controller.observable.ObservableWidget
import com.movtery.layer_controller.observable.cloneJoystick
import com.movtery.layer_controller.observable.cloneNormal
import com.movtery.layer_controller.observable.cloneText
import com.movtery.layer_controller.utils.saveToFile
import com.cmc.launcher.R
import com.cmc.launcher.game.control.CabinetStyleItem
import com.cmc.launcher.game.control.StyleCabinetState
import com.cmc.launcher.ui.components.MenuState
import com.cmc.launcher.ui.screens.main.control_editor.EditorOperation
import com.cmc.launcher.ui.screens.main.control_editor.EditorWarningOperation
import com.cmc.launcher.ui.screens.main.control_editor.EditorWidgetOperation
import com.cmc.launcher.ui.screens.main.control_editor.PreviewScenario
import com.cmc.launcher.ui.screens.main.control_editor.edit_widget.SelectedWidgetData
import com.cmc.launcher.ui.screens.main.control_editor.style_cabinet.OpenCabinetSource
import com.cmc.launcher.ui.theme.showThemed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class EditorViewModel : ViewModel() {
    lateinit var observableLayout: ObservableControlLayout
        private set

    val styleCabinet = StyleCabinetState()

    var selectedLayer by mutableStateOf<ObservableControlLayer?>(null)

    var selectedWidget by mutableStateOf<SelectedWidgetData?>(null)

    var editorMenu by mutableStateOf(MenuState.HIDE)

    var editorBallPosition by mutableStateOf(Offset.Zero)

    var editorOperation by mutableStateOf<EditorOperation>(EditorOperation.None)
        private set

    var editorWidgetOperation by mutableStateOf<EditorWidgetOperation>(EditorWidgetOperation.None)
        private set

    var editorWarningOperation by mutableStateOf<EditorWarningOperation>(EditorWarningOperation.None)
        private set

    var isLayerFocus by mutableStateOf(false)

    var isPreviewMode by mutableStateOf(false)

    var previewScenario by mutableStateOf(PreviewScenario.InMenu)

    var previewHideLayerWhen by mutableStateOf(HideLayerWhen.None)

    fun initLayout(layout: ControlLayout) {
        if (!::observableLayout.isInitialized) {
            this.observableLayout = ObservableControlLayout(layout)
        }
    }

    fun onWidgetTapped(data: ObservableWidget, layer: ObservableControlLayer) {
        val current = selectedWidget?.data
        selectedWidget = SelectedWidgetData(data, layer)
        if (current == data) {

            editorOperation = EditorOperation.SelectButton
        }
    }

    fun openWidgetEditor() {
        if (selectedWidget != null) {
            editorOperation = EditorOperation.SelectButton
        }
    }

    fun createLayer(defaultLayerName: String) {
        val newLayer = observableLayout.addLayer(
            layer = createNewLayer(defaultLayerName = defaultLayerName)
        )
        editorOperation = EditorOperation.EditLayer(newLayer)
    }

    fun editLayer(layer: ObservableControlLayer) {
        editorOperation = EditorOperation.EditLayer(layer)
    }

    fun copyLayer(layer: ObservableControlLayer, defaultLayerName: String) {
        val baseLayer = layer.pack()
        val newLayer = observableLayout.addLayer(
            layer = createNewLayer(defaultLayerName = defaultLayerName).copy(
                hide = baseLayer.hide,
                hideWhenMouse = baseLayer.hideWhenMouse,
                hideWhenGamepad = baseLayer.hideWhenGamepad,
                visibilityType = baseLayer.visibilityType,
                normalButtons = baseLayer.normalButtons,
                textBoxes = baseLayer.textBoxes,
                joystickButtons = baseLayer.joystickButtons
            )
        )
        editorOperation = EditorOperation.EditLayer(newLayer)
    }

    fun requestDeleteLayer(layer: ObservableControlLayer) {
        editorOperation = EditorOperation.DeleteLayer(layer)
    }

    fun confirmDeleteLayer(layer: ObservableControlLayer) {
        val isWidgetLayer = selectedWidget?.layer == layer
        removeLayer(layer)
        if (isWidgetLayer) {
            selectedWidget = null
        }
        editorOperation = EditorOperation.None
    }

    fun mergeLayerDownward(layer: ObservableControlLayer) {
        observableLayout.mergeDownward(layer)
    }

    fun onLayerHideChanged(hide: Boolean, layer: ObservableControlLayer) {
        if (hide && selectedWidget?.layer == layer) {
            selectedWidget = null
        }
    }

    fun switchMenu() {
        editorMenu = editorMenu.next()
    }

    fun removeLayer(layer: ObservableControlLayer) {
        if (layer == selectedLayer) selectedLayer = null
        observableLayout.removeLayer(layer.uuid)
    }

    fun addWidget(layers: List<ObservableControlLayer>, addToLayer: (ObservableControlLayer) -> Unit) {
        val layer = selectedLayer
        if (layers.isEmpty()) {
            editorWarningOperation = EditorWarningOperation.WarningNoLayers
        } else if (layer == null) {
            editorWarningOperation = EditorWarningOperation.WarningNoSelectLayer
        } else {
            addToLayer(layer)
        }
    }

    fun removeWidget(layer: ObservableControlLayer, widget: ObservableWidget) {
        when (widget) {
            is ObservableNormalData -> layer.removeNormalButton(widget.uuid)
            is ObservableTextData -> layer.removeTextBox(widget.uuid)
            is ObservableJoystickData -> layer.removeJoystickButton(widget.uuid)
        }
    }

    fun dismissOperation() {
        editorOperation = EditorOperation.None
    }

    fun openStyleList() {
        editorOperation = EditorOperation.OpenStyleList
    }

    fun openJoystickStyleList() {
        editorOperation = EditorOperation.OpenJoystickStyleList
    }

    fun startCreateStyle() {
        editorOperation = EditorOperation.CreateStyle
    }

    fun confirmCreateStyle(name: String) {
        createNewStyle(name)
        editorOperation = EditorOperation.OpenStyleList
    }

    fun startCreateJoystickStyle() {
        editorOperation = EditorOperation.CreateJoystickStyle
    }

    fun confirmCreateJoystickStyle(name: String) {
        createNewJoystickStyle(name)
        editorOperation = EditorOperation.OpenJoystickStyleList
    }

    fun editStyle(style: ObservableButtonStyle) {
        editorOperation = EditorOperation.EditButtonStyle(style)
    }

    fun editJoystickStyle(style: ObservableJoystickStyle) {
        editorOperation = EditorOperation.EditJoystickStyle(style)
    }

    fun requestDeleteStyle(style: ObservableButtonStyle) {
        editorOperation = EditorOperation.DeleteButtonStyle(style)
    }

    fun confirmDeleteStyle(style: ObservableButtonStyle) {
        removeStyle(style)
        editorOperation = EditorOperation.None
    }

    fun requestDeleteJoystickStyle(style: ObservableJoystickStyle) {
        editorOperation = EditorOperation.DeleteJoystickStyle(style)
    }

    fun confirmDeleteJoystickStyle(style: ObservableJoystickStyle) {
        removeJoystickStyle(style)
        editorOperation = EditorOperation.None
    }

    fun dismissWidgetOperation() {
        editorWidgetOperation = EditorWidgetOperation.None
    }

    fun requestCloneWidget(data: ObservableWidget, layer: ObservableControlLayer) {
        editorWidgetOperation = EditorWidgetOperation.CloneButton(data, layer)
    }

    fun requestDeleteWidget(data: ObservableWidget, layer: ObservableControlLayer) {
        editorWidgetOperation = EditorWidgetOperation.DeleteButton(data, layer)
    }

    fun confirmCloneWidgets(widget: ObservableWidget, layers: List<ObservableControlLayer>) {
        cloneWidgetToLayers(widget, layers)
        editorWidgetOperation = EditorWidgetOperation.None
    }

    fun confirmDeleteWidget(widget: ObservableWidget, layer: ObservableControlLayer) {
        removeWidget(layer, widget)
        selectedWidget = null
        editorOperation = EditorOperation.None
        editorWidgetOperation = EditorWidgetOperation.None
    }

    fun editWidgetText(string: ObservableTranslatableString) {
        editorWidgetOperation = EditorWidgetOperation.EditWidgetText(string)
    }

    fun switchLayersVisibility(data: ObservableClickEventsProvider, type: ClickEvent.Type) {
        editorWidgetOperation = EditorWidgetOperation.SwitchLayersVisibility(data, type)
    }

    fun sendText(data: ObservableClickEventsProvider) {
        editorWidgetOperation = EditorWidgetOperation.SendText(data)
    }

    fun confirmSendText(data: ObservableClickEventsProvider, value: String) {

        data.onRemoveAllEvents(ClickEvent.Type.SendText)
        if (value.isNotEmpty()) {
            data.onAddEvent(ClickEvent(ClickEvent.Type.SendText, value))
        }
        editorWidgetOperation = EditorWidgetOperation.None
    }

    fun dismissWarning() {
        editorWarningOperation = EditorWarningOperation.None
    }

    fun createNewStyle(name: String) {
        observableLayout.addStyle(
            com.movtery.layer_controller.data.createNewButtonStyle(name)
        )
    }

    fun cloneStyle(style: ObservableButtonStyle) {
        observableLayout.cloneStyle(style)
    }

    fun removeStyle(style: ObservableButtonStyle) {
        observableLayout.removeStyle(style.uuid)
    }

    fun removeJoystickStyle(style: ObservableJoystickStyle) {
        observableLayout.removeJoystickStyle(style.uuid)
    }

    fun createNewJoystickStyle(name: String) {
        observableLayout.addJoystickStyle(
            com.movtery.layer_controller.data.createNewJoystickStyle(name)
        )
    }

    fun cloneJoystickStyle(style: ObservableJoystickStyle) {
        observableLayout.cloneJoystickStyle(style)
    }

    fun favoriteStyle(style: ObservableButtonStyle, onResult: (Boolean) -> Unit) {
        if (styleCabinet.exists(isJoystick = false, uuid = style.uuid)) {
            editorOperation = EditorOperation.FavoriteButtonStyleConflict(style)
        } else {
            performFavorite(style.pack(), independent = false, onResult)
        }
    }

    fun favoriteJoystickStyle(style: ObservableJoystickStyle, onResult: (Boolean) -> Unit) {
        if (styleCabinet.exists(isJoystick = true, uuid = style.uuid)) {
            editorOperation = EditorOperation.FavoriteJoystickStyleConflict(style)
        } else {
            performFavorite(style.pack(), independent = false, onResult)
        }
    }

    fun resolveFavoriteConflict(
        independent: Boolean,
        onResult: (Boolean) -> Unit
    ) {
        when (val operation = editorOperation) {
            is EditorOperation.FavoriteButtonStyleConflict -> {
                performFavorite(operation.style.pack(), independent, onResult)
                editorOperation = EditorOperation.OpenStyleList
            }
            is EditorOperation.FavoriteJoystickStyleConflict -> {
                performFavorite(operation.style.pack(), independent, onResult)
                editorOperation = EditorOperation.OpenJoystickStyleList
            }
            else -> {}
        }
    }

    fun cancelFavoriteConflict() {
        when (editorOperation) {
            is EditorOperation.FavoriteButtonStyleConflict -> editorOperation = EditorOperation.OpenStyleList
            is EditorOperation.FavoriteJoystickStyleConflict -> editorOperation = EditorOperation.OpenJoystickStyleList
            else -> {}
        }
    }

    fun openStyleCabinet(source: OpenCabinetSource) {
        editorOperation = EditorOperation.OpenStyleCabinet(source)
    }

    fun closeStyleCabinet() {
        val source = (editorOperation as? EditorOperation.OpenStyleCabinet)?.source
        val back = when (source) {
            OpenCabinetSource.ControlStyle -> EditorOperation.OpenStyleList
            OpenCabinetSource.JoystickStyle -> EditorOperation.OpenJoystickStyleList
            else -> EditorOperation.None
        }
        editorOperation = back
    }

    fun editCabinetItem(item: CabinetStyleItem) {
        val source = (editorOperation as? EditorOperation.OpenStyleCabinet)?.source ?: OpenCabinetSource.Menu
        editorOperation = when (item) {
            is CabinetStyleItem.Button -> EditorOperation.EditCabinetButtonStyle(item, source)
            is CabinetStyleItem.Joystick -> EditorOperation.EditCabinetJoystickStyle(item, source)
        }
    }

    fun deleteCabinetItem(item: CabinetStyleItem) {
        styleCabinet.delete(item)
    }

    fun addCabinetStyleToLayout(item: CabinetStyleItem, onAdded: () -> Unit) {
        val source = (editorOperation as? EditorOperation.OpenStyleCabinet)?.source ?: OpenCabinetSource.Menu
        when (item) {
            is CabinetStyleItem.Button -> {
                if (observableLayout.styles.value.any { it.uuid == item.style.uuid }) {
                    editorOperation = EditorOperation.AddToLayoutButtonStyleConflict(item, source)
                } else {
                    observableLayout.addStyle(item.style.pack())
                    onAdded()
                }
            }
            is CabinetStyleItem.Joystick -> {
                if (observableLayout.joystickStyles.value.any { it.uuid == item.style.uuid }) {
                    editorOperation = EditorOperation.AddToLayoutJoystickStyleConflict(item, source)
                } else {
                    observableLayout.addJoystickStyle(item.style.pack())
                    onAdded()
                }
            }
        }
    }

    fun resolveAddToLayoutConflict(overwrite: Boolean) {
        when (val operation = editorOperation) {
            is EditorOperation.AddToLayoutButtonStyleConflict -> {
                if (overwrite) {
                    observableLayout.replaceStyle(operation.item.style.pack())
                } else {
                    observableLayout.addStyle(operation.item.style.pack().cloneNew())
                }
                editorOperation = EditorOperation.OpenStyleCabinet(operation.source)
            }
            is EditorOperation.AddToLayoutJoystickStyleConflict -> {
                if (overwrite) {
                    observableLayout.replaceJoystickStyle(operation.item.style.pack())
                } else {
                    observableLayout.addJoystickStyle(operation.item.style.pack().cloneNew())
                }
                editorOperation = EditorOperation.OpenStyleCabinet(operation.source)
            }
            else -> {}
        }
    }

    fun cancelAddToLayoutConflict() {
        val source = when (val operation = editorOperation) {
            is EditorOperation.AddToLayoutButtonStyleConflict -> operation.source
            is EditorOperation.AddToLayoutJoystickStyleConflict -> operation.source
            else -> return
        }
        editorOperation = EditorOperation.OpenStyleCabinet(source)
    }

    fun closeCabinetEdit(item: CabinetStyleItem, source: OpenCabinetSource) {
        styleCabinet.save(item)
        editorOperation = EditorOperation.OpenStyleCabinet(source)
    }

    fun closeButtonStyleEdit() {
        when (val operation = editorOperation) {
            is EditorOperation.EditCabinetButtonStyle -> {
                closeCabinetEdit(operation.item, operation.source)
            }
            is EditorOperation.EditButtonStyle -> {
                editorOperation = EditorOperation.None
            }
            else -> {}
        }
    }

    fun closeJoystickStyleEdit() {
        when (val operation = editorOperation) {
            is EditorOperation.EditCabinetJoystickStyle -> {
                closeCabinetEdit(operation.item, operation.source)
            }
            is EditorOperation.EditJoystickStyle -> {
                editorOperation = EditorOperation.None
            }
            else -> {}
        }
    }

    fun saveCabinetItem(item: CabinetStyleItem) {
        styleCabinet.save(item)
    }

    fun cloneWidgetToLayers(widget: ObservableWidget, layers: List<ObservableControlLayer>) {
        when (widget) {
            is ObservableNormalData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneNormal()
                    layer.addNormalButton(newData)
                }
            }
            is ObservableTextData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneText()
                    layer.addTextBox(newData)
                }
            }
            is ObservableJoystickData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneJoystick()
                    layer.addJoystickButton(newData)
                }
            }
        }
    }

    fun applyEditorHide() {
        observableLayout.applyEditorHide()
    }

    fun save(
        targetFile: File,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            editorOperation = EditorOperation.Saving
            val layout = observableLayout.pack()
            runCatching {
                layout.saveToFile(targetFile)
            }.onFailure { e ->
                editorOperation = EditorOperation.SaveFailed(e)
            }.onSuccess {
                editorOperation = EditorOperation.None
                onSaved()
            }
        }
    }

    fun onBackPressed(
        context: Context,
        onExit: () -> Unit
    ) {

        when (editorOperation) {
            is EditorOperation.SelectButton,
            is EditorOperation.EditButtonStyle,
            is EditorOperation.OpenStyleCabinet -> {
                editorOperation = EditorOperation.None
            }

            is EditorOperation.EditCabinetButtonStyle -> closeButtonStyleEdit()
            is EditorOperation.EditCabinetJoystickStyle -> closeJoystickStyleEdit()
            else -> {
                showExitEditorDialog(
                    context = context,
                    onExit = onExit
                )
            }
        }
    }

    private val checkModified = Mutex()

    fun showExitEditorDialog(
        context: Context,
        onExit: () -> Unit
    ) {
        viewModelScope.launch {
            val isModified = checkModified.withLock {
                observableLayout.isModified()
            }
            if (isModified) {
                showExitEditorDialogSuspend(
                    context = context,
                    onExit = onExit
                )
            } else {

                onExit()
            }
        }
    }

    private suspend fun showExitEditorDialogSuspend(
        context: Context,
        onExit: () -> Unit
    ) = withContext(Dispatchers.Main) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.generic_warning)
            .setMessage(R.string.control_editor_exit_message)
            .setPositiveButton(R.string.generic_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton(R.string.control_editor_exit_confirm) { dialog, _ ->
                dialog.dismiss()
                onExit()
            }
            .showThemed()
    }

    private fun performFavorite(style: ButtonStyle, independent: Boolean, onResult: (Boolean) -> Unit) {
        val data = if (independent) style.cloneNew() else style
        styleCabinet.favorite(data, onResult)
    }

    private fun performFavorite(style: JoystickStyle, independent: Boolean, onResult: (Boolean) -> Unit) {
        val data = if (independent) style.cloneNew() else style
        styleCabinet.favorite(data, onResult)
    }
}
