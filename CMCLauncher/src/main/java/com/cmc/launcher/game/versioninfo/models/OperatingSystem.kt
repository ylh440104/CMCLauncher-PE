package com.cmc.launcher.game.versioninfo.models

import com.google.gson.annotations.SerializedName

enum class OperatingSystem(val identifier: String) {

    @SerializedName("windows")
    Windows("windows"),

    @SerializedName("linux")
    Linux("linux"),

    @SerializedName("osx")
    MacOS("osx"),

    @SerializedName("universal")
    Unknown("universal")
}
