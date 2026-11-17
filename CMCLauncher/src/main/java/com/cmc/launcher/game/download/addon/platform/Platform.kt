package com.cmc.launcher.game.download.addon.platform

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
enum class Platform(val displayName: String): Parcelable {
    @SerialName("CURSEFORGE")
    CURSEFORGE("CurseForge"),
    @SerialName("MODRINTH")
    MODRINTH("Modrinth")
}
