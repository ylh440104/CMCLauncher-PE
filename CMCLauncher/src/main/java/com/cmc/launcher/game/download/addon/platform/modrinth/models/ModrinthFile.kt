package com.cmc.launcher.game.download.addon.platform.modrinth.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ModrinthFile(

    @SerialName("hashes")
    val hashes: Hash,

    @SerialName("url")
    val url: String,

    @SerialName("filename")
    val fileName: String,

    @SerialName("primary")
    val primary: Boolean,

    @SerialName("size")
    val size: Long,

    @SerialName("file_type")
    val fileType: String? = null
) {
    @Serializable
    class Hash(
        @SerialName("sha1")
        val sha1: String,

        @SerialName("sha512")
        val sha512: String
    )
}

fun Array<ModrinthFile>.getPrimary(): ModrinthFile? {
    val files = this.takeIf { it.isNotEmpty() } ?: run {
        return null
    }
    return files.find { it.primary } ?: this[0]
}
