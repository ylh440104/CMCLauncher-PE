package com.cmc.launcher.game.version.mod.meta

import com.google.gson.annotations.SerializedName

class ForgeOldModMetadataList(
    @SerializedName("modListVersion")
    val modListVersion: Int?,
    @SerializedName("modList")
    val modList: List<ForgeOldModMetadata>?
)
