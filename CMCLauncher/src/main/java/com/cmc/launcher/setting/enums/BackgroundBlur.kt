package com.cmc.launcher.setting.enums

enum class BackgroundBlur {

    Background,

    Foreground;

    fun switch(): BackgroundBlur {
        return when (this) {
            Background -> Foreground
            Foreground -> Background
        }
    }
}
