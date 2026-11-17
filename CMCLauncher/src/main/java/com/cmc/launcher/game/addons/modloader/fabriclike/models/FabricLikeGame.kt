package com.cmc.launcher.game.addons.modloader.fabriclike.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FabricLikeGame(

    @SerialName("version")
    val version: String,

    @SerialName("stable")
    val stable: Boolean
)
