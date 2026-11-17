package com.cmc.launcher.ui.screens.content.home.server

import com.cmc.launcher.game.version.multiplayer.ServerPingResult
import com.cmc.launcher.ui.screens.content.home.version.VersionCardDir
import com.cmc.launcher.ui.screens.content.home.version.VersionCardStatus

enum class ServerCardVersionMode {

    CURRENT,

    SPECIFIC
}

data class ServerCardRecord(
    val cardId: String,
    val name: String,
    val icon: ByteArray? = null,
    val quickLaunchEnabled: Boolean = true,
    val versionMode: ServerCardVersionMode = ServerCardVersionMode.CURRENT,
    val dir: VersionCardDir? = null,
    val versionName: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ServerCardRecord

        return cardId == other.cardId &&
                name == other.name &&
                icon.contentEquals(other.icon) &&
                quickLaunchEnabled == other.quickLaunchEnabled &&
                versionMode == other.versionMode &&
                dir == other.dir &&
                versionName == other.versionName
    }

    override fun hashCode(): Int {
        var result = cardId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (icon?.contentHashCode() ?: 0)
        result = 31 * result + quickLaunchEnabled.hashCode()
        result = 31 * result + versionMode.hashCode()
        result = 31 * result + (dir?.hashCode() ?: 0)
        result = 31 * result + (versionName?.hashCode() ?: 0)
        return result
    }
}

sealed interface ServerCardPingStatus {

    data object Loading : ServerCardPingStatus

    data class Loaded(val result: ServerPingResult) : ServerCardPingStatus

    data object Failed : ServerCardPingStatus
}

data class ServerCardState(
    val record: ServerCardRecord,
    val ping: ServerCardPingStatus,

    val versionStatus: VersionCardStatus? = null
)
