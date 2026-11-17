package com.cmc.launcher.ui.control.input

enum class TextInputMode {

    ENABLE {
        override fun switch() = DISABLE
    },

    DISABLE {
        override fun switch() = ENABLE
    };

    abstract fun switch(): TextInputMode
}
