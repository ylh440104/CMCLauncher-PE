package com.cmc.launcher.game.account.wardrobe

import java.io.File

class CapeFileDownloader: WardrobeDownloader() {

    @Throws(Exception::class)
    suspend fun download(
        url: String,
        capeFile: File,
        uuid: String,
    ) {
        val valueObject = yggdrasil(url, uuid)
        val capeObject = valueObject.get("textures").asJsonObject.get("CAPE").asJsonObject
        val skinUrl = capeObject.get("url").asString

        download(skinUrl, capeFile)
    }
}
