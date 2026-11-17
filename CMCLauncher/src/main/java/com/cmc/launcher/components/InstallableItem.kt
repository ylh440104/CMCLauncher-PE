package com.cmc.launcher.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InstallableItem(
    val name: String,
    val summary: String?,
    val task: AbstractUnpackTask
) : Comparable<InstallableItem> {
    private val _state = MutableStateFlow(State.NOT_STARTED)

    val state = _state.asStateFlow()

    fun updateState(state: State) {
        this._state.update { state }
    }

    override fun compareTo(other: InstallableItem): Int {
        return name.compareTo(other.name, ignoreCase = true)
    }

    enum class State {

        NOT_STARTED,

        PENDING,

        RUNNING,

        FINISHED,

        FAILED,

        NOT_EXISTS
    }
}
