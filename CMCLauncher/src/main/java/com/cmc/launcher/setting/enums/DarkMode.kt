package com.cmc.launcher.setting.enums

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.cmc.launcher.R
import com.cmc.launcher.setting.AllSettings

enum class DarkMode(val textRes: Int) {
    Enable(R.string.generic_enable),
    Disable(R.string.generic_disable),

    FollowSystem(R.string.generic_follow_system)
}

@Composable
fun isLauncherInDarkTheme(): Boolean {
    val value = AllSettings.launcherDarkMode.state
    return key(value) {
        when (value) {
            DarkMode.Enable -> true
            DarkMode.Disable -> false
            DarkMode.FollowSystem -> isSystemInDarkTheme()
        }
    }
}
