package com.cmc.launcher.game.account.microsoft

import com.cmc.launcher.R
import com.cmc.launcher.game.account.microsoft.MinecraftProfileException.ExceptionStatus.BLOCKED_IP
import com.cmc.launcher.game.account.microsoft.MinecraftProfileException.ExceptionStatus.FREQUENT
import com.cmc.launcher.game.account.microsoft.MinecraftProfileException.ExceptionStatus.PROFILE_NOT_EXISTS
import com.cmc.launcher.ui.AndroidStringText
import com.cmc.launcher.ui.androidText

class MinecraftProfileException(val status: ExceptionStatus) : RuntimeException() {
    enum class ExceptionStatus {

        FREQUENT,

        BLOCKED_IP,

        PROFILE_NOT_EXISTS
    }
}

fun MinecraftProfileException.toLocal(): AndroidStringText {
    return androidText(
        when (status) {
            FREQUENT -> R.string.account_logging_frequent
            BLOCKED_IP -> R.string.account_logging_blocked_ip
            PROFILE_NOT_EXISTS -> R.string.account_logging_profile_not_exists
        }
    )
}
