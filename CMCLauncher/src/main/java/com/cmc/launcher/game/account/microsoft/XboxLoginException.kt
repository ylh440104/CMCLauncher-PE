package com.cmc.launcher.game.account.microsoft

import com.cmc.launcher.R
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.BANNED
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.BLOCKED_REGION
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.NOT_ACCEPTED_SERVICE
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.REACHED_PLAYTIME_LIMIT
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.REQUIRES_PROOF_OF_AGE
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.RESTRICTED
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.UNDERAGE
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.UNREGISTERED
import com.cmc.launcher.ui.AndroidStringText
import com.cmc.launcher.ui.androidText

class XboxLoginException(val status: ExceptionStatus) : RuntimeException() {
    enum class ExceptionStatus {

        BANNED,

        RESTRICTED,

        UNREGISTERED,

        NOT_ACCEPTED_SERVICE,

        BLOCKED_REGION,

        REQUIRES_PROOF_OF_AGE,

        REACHED_PLAYTIME_LIMIT,

        UNDERAGE
    }
}

fun XboxLoginException.toLocal(): AndroidStringText {
    return androidText(
        when(status) {
            BANNED -> R.string.account_logging_xbox_banned
            RESTRICTED -> R.string.account_logging_xbox_restricted
            UNREGISTERED -> R.string.account_logging_xbox_unregistered
            NOT_ACCEPTED_SERVICE -> R.string.account_logging_xbox_not_accepted_service
            BLOCKED_REGION -> R.string.account_logging_xbox_blocked_region
            REQUIRES_PROOF_OF_AGE -> R.string.account_logging_xbox_requires_proof_of_age
            REACHED_PLAYTIME_LIMIT -> R.string.account_logging_xbox_reached_playtime_limit
            UNDERAGE -> R.string.account_logging_xbox_underage
        }
    )
}
