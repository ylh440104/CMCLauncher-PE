package com.cmc.launcher.ui.screens.content.home.version

import com.google.gson.annotations.SerializedName
import com.cmc.launcher.game.path.GamePathManager
import com.cmc.launcher.game.version.installed.Version

sealed interface VersionCardDir {

    fun resolveGameHome(): String

    data object Default : VersionCardDir {
        override fun resolveGameHome(): String = GamePathManager.getDefaultPath()
    }

    data class Custom(val path: String) : VersionCardDir {
        override fun resolveGameHome(): String = path
    }

    companion object {

        fun fromGameHome(gameHome: String): VersionCardDir =
            if (gameHome == GamePathManager.getDefaultPath()) Default else Custom(gameHome)
    }
}

data class VersionCardRecord(
    @SerializedName("cardId")
    val cardId: String,
    @SerializedName("versionName")
    val versionName: String,
    @SerializedName("dir")
    val dir: VersionCardDir
)

sealed interface VersionCardStatus {

    data object Loading : VersionCardStatus

    data class Available(val version: Version) : VersionCardStatus

    data object Deleted : VersionCardStatus

    data object Inaccessible : VersionCardStatus
}

data class VersionCardState(
    val record: VersionCardRecord,
    val status: VersionCardStatus
)
