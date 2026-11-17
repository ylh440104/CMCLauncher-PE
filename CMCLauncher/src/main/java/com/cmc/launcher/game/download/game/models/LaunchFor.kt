package com.cmc.launcher.game.download.game.models

import com.google.gson.annotations.SerializedName

class LaunchFor(
    @SerializedName("infos")
    val infos: Array<Info>
) {
    class Info(

        @SerializedName("version")
        val version: String,

        @SerializedName("name")
        val name: String
    )
}
