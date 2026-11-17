package com.cmc.launcher.coroutine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class MutableTransitionStateFlow<T>(initial: T?) {

    data class Transition<T>(
        val old: T?,
        val new: T
    )

    private val internal = MutableStateFlow(initial)
    private var lastValue: T? = initial

    val value
        get () = internal.value

    val stateFlow: StateFlow<T?> = internal.asStateFlow()

    val changes: Flow<Transition<T>> = internal
        .filterNotNull()
        .map { new ->
            val transition = Transition(lastValue, new)
            lastValue = new
            transition
        }

    fun set(value: T) {
        internal.value = value
    }
}
