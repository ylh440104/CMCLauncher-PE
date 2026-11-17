package com.cmc.launcher.game.control

import com.movtery.layer_controller.data.ButtonStyle
import com.movtery.layer_controller.data.JoystickStyle
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.movtery.layer_controller.observable.ObservableJoystickStyle
import com.movtery.layer_controller.utils.loadButtonStyle
import com.movtery.layer_controller.utils.loadJoystickStyle
import com.movtery.layer_controller.utils.saveToStyleFile
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "StyleCabinet"

sealed interface CabinetStyleItem {

    val file: File

    val name: String

    data class Button(
        override val file: File,
        val style: ObservableButtonStyle
    ) : CabinetStyleItem {
        override val name: String get() = style.name
    }

    data class Joystick(
        override val file: File,
        val style: ObservableJoystickStyle
    ) : CabinetStyleItem {
        override val name: String get() = style.name
    }
}

class StyleCabinetState {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val dirControls = File(PathManager.DIR_CONTROL_LAYOUTS, "styles/controls")

    private val dirJoystick = File(PathManager.DIR_CONTROL_LAYOUTS, "styles/joystick")

    private val _items = MutableStateFlow<List<CabinetStyleItem>>(emptyList())
    val items = _items.asStateFlow()

    fun load() {
        scope.launch {
            val buttons = loadItems(dirControls) { file ->
                CabinetStyleItem.Button(file, ObservableButtonStyle(loadButtonStyle(file)))
            }
            val joysticks = loadItems(dirJoystick) { file ->
                CabinetStyleItem.Joystick(file, ObservableJoystickStyle(loadJoystickStyle(file)))
            }
            _items.update { old ->

                val loaded = buttons + joysticks
                sortItems(loaded.filterNot { item -> old.any { sameIdentity(it, item) } } + old)
            }
        }
    }

    fun exists(isJoystick: Boolean, uuid: String): Boolean {
        return _items.value.any { item ->
            when (item) {
                is CabinetStyleItem.Button -> !isJoystick && item.style.uuid == uuid
                is CabinetStyleItem.Joystick -> isJoystick && item.style.uuid == uuid
            }
        }
    }

    fun favorite(style: ButtonStyle, onResult: (Boolean) -> Unit = {}) {
        launchFavorite(onResult) {
            val file = style.saveToStyleFile(dirControls)
            CabinetStyleItem.Button(file, ObservableButtonStyle(style))
        }
    }

    fun favorite(style: JoystickStyle, onResult: (Boolean) -> Unit = {}) {
        launchFavorite(onResult) {
            val file = style.saveToStyleFile(dirJoystick)
            CabinetStyleItem.Joystick(file, ObservableJoystickStyle(style))
        }
    }

    fun save(item: CabinetStyleItem) {
        scope.launch {
            runCatching {
                when (item) {
                    is CabinetStyleItem.Button -> item.style.pack().saveToStyleFile(dirControls)
                    is CabinetStyleItem.Joystick -> item.style.pack().saveToStyleFile(dirJoystick)
                }
            }.onFailure { e ->
                Logger.warning(TAG, "Failed to save style file! file = ${item.file}", e)
            }
        }
    }

    fun delete(item: CabinetStyleItem) {
        scope.launch {
            runCatching {
                item.file.delete()
            }.onSuccess {
                _items.update { old -> old.filterNot { it == item } }
            }.onFailure { e ->
                Logger.warning(TAG, "Failed to delete style file! file = ${item.file}", e)
            }
        }
    }

    private fun launchFavorite(
        onResult: (Boolean) -> Unit,
        block: suspend () -> CabinetStyleItem
    ) {
        scope.launch {
            val result = runCatching { block() }
            if (result.isSuccess) {
                val newItem = result.getOrThrow()
                _items.update { old ->
                    sortItems(old.filterNot { sameIdentity(it, newItem) } + newItem)
                }
            } else {
                Logger.warning(TAG, "Failed to favorite style!", result.exceptionOrNull())
            }
            withContext(Dispatchers.Main) {
                onResult(result.isSuccess)
            }
        }
    }

    private fun loadItems(
        dir: File,
        create: (File) -> CabinetStyleItem
    ): List<CabinetStyleItem> {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.exists() && it.extension.equals("json", true) }
            ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching {
                create(file)
            }.onFailure { e ->
                Logger.warning(TAG, "Failed to load style file! file = $file", e)
            }.getOrNull()
        }
    }

    private fun sameIdentity(a: CabinetStyleItem, b: CabinetStyleItem): Boolean {
        return when {
            a is CabinetStyleItem.Button && b is CabinetStyleItem.Button ->
                a.style.uuid == b.style.uuid
            a is CabinetStyleItem.Joystick && b is CabinetStyleItem.Joystick ->
                a.style.uuid == b.style.uuid
            else -> false
        }
    }

    private fun sortItems(items: List<CabinetStyleItem>): List<CabinetStyleItem> {
        return items.sortedWith(
            compareBy({ it is CabinetStyleItem.Joystick }, { it.name })
        )
    }
}
