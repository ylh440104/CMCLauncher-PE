package com.cmc.launcher.game.download.addon.platform.curseforge.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CurseForgePagination(

    @SerialName("index")
    val index: Int,

    @SerialName("pageSize")
    val pageSize: Int,

    @SerialName("resultCount")
    val resultCount: Int,

    @SerialName("totalCount")
    val totalCount: Long
)
