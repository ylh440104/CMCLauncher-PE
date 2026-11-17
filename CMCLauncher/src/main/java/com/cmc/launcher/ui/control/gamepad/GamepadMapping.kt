package com.cmc.launcher.ui.control.gamepad

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GamepadMapping(
    val key: Int,
    val dpadDirection: DpadDirection?,
    val targetsInGame: Set<String> = emptySet(),
    val targetsInMenu: Set<String> = emptySet()
): Parcelable
