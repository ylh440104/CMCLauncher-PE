package com.cmc.launcher.viewmodel

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cmc.launcher.ui.control.gamepad.GamepadRemapOperation
import com.cmc.launcher.ui.control.gamepad.GamepadRemapper
import com.cmc.launcher.ui.control.gamepad.remapperMMKV
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GamepadRemapperViewModel: ViewModel() {
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    val allRemappers = mutableMapOf<String, GamepadRemapper>()

    var isSavingMapping by mutableStateOf(false)

    var uiOperation by mutableStateOf<GamepadRemapOperation>(GamepadRemapOperation.None)

    fun sendEvent(event: Event) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    fun startRemapperUI(deviceName: String) {
        if (uiOperation == GamepadRemapOperation.None) {
            uiOperation = GamepadRemapOperation.Tip(deviceName)
        }
    }

    fun findMapping(deviceName: String): GamepadRemapper? {
        return allRemappers[deviceName] ?: loadByDeviceName(deviceName)?.also { remapper ->
            applyMapping(deviceName, remapper)
        }
    }

    fun applyMapping(
        deviceName: String,
        motionMapping: Map<Int, Int>,
        keyMapping: Map<Int, Int>
    ) {
        applyMapping(
            deviceName = deviceName,
            remapper = GamepadRemapper(motionMapping, keyMapping)
        )
    }

    fun applyMapping(deviceName: String, remapper: GamepadRemapper) {
        allRemappers[deviceName] = remapper
    }

    private fun loadByDeviceName(deviceName: String): GamepadRemapper? {
        val mmkv = remapperMMKV()
        return mmkv.decodeParcelable(deviceName, GamepadRemapper::class.java)?.takeIf { remapper ->

            !remapper.isOldVersion()
        }
    }

    private val saveMutex = Mutex()

    fun save() {
        viewModelScope.launch {
            saveMutex.withLock {
                isSavingMapping = true
                val mmkv = remapperMMKV()
                allRemappers.entries.forEach { entry ->
                    mmkv.encode(entry.key, entry.value)
                }
                isSavingMapping = false
            }
        }
    }

    sealed interface Event {

        data class Button(val code: Int, val event: KeyEvent) : Event

        data class Axis(val event: MotionEvent) : Event
    }
}
