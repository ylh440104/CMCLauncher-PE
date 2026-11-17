package com.cmc.launcher.game.download.addon.platform.curseforge.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CurseForgeVersions(

    @SerialName("data")
    val data: Array<CurseForgeFile>,

    @SerialName("pagination")
    val pagination: CurseForgePagination
)
