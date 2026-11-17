package com.cmc.launcher.setting.unit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

abstract class AbstractSettingUnit<V>(
    val key: String,
    val defaultValue: V
) {

    abstract fun getValue(): V

    protected abstract fun saveValue(v: V): V

    var state by mutableStateOf(defaultValue)
        protected set

    fun init() {
        getValue()
    }

    fun save() {
        saveValue(this.state)
    }

    fun save(value: V) {
        this.state = saveValue(value)
    }

    open fun updateState(value: V) {
        this.state = value
    }

    fun reset() {
        this.state = saveValue(defaultValue)
    }
}
