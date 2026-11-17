package com.cmc.launcher.ui.screens.game.elements

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.ui.screens.game.elements.LogState.CLOSE
import com.cmc.launcher.ui.screens.game.elements.LogState.SHOW_BEFORE_LOADING

enum class LogState(val value: Boolean) {

    SHOW_BEFORE_LOADING(true) {
        override fun next(): LogState = CLOSE
    },

    SHOW(true) {
        override fun next(): LogState = CLOSE
    },

    CLOSE(false) {
        override fun next(): LogState = SHOW
    };

    abstract fun next(): LogState
}

fun mutableStateOfLog(): MutableState<LogState> {
    return mutableStateOf(
        if (AllSettings.showLogAutomatic.getValue()) SHOW_BEFORE_LOADING
        else CLOSE
    )
}
