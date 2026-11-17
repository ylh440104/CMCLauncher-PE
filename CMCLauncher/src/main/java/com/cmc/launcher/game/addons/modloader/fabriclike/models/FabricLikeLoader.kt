package com.cmc.launcher.game.addons.modloader.fabriclike.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FabricLikeLoader(
    @SerialName("separator")
    val separator: String,
    @SerialName("build")
    val build: Int,
    @SerialName("maven")
    val maven: String,

    @SerialName("version")
    val version: String,

    @SerialName("stable")
    val stable: Boolean = true
)
