package com.cmc.launcher.game.account.wardrobe

import com.cmc.launcher.utils.logging.Logger
import java.io.File

private const val TAG = "SkinFileDownloader"

class SkinFileDownloader: WardrobeDownloader() {

    @Throws(Exception::class)
    suspend fun download(
        url: String,
        skinFile: File,
        uuid: String,
        changeSkinModel: (SkinModelType) -> Unit
    ) {
        val valueObject = yggdrasil(url, uuid)
        val skinObject = valueObject.get("textures").asJsonObject.get("SKIN").asJsonObject
        val skinUrl = skinObject.get("url").asString

        val skinModelType = runCatching {
            skinObject.takeIf {
                it.has("metadata")
            }?.get("metadata")?.let {

                SkinModelType.ALEX
            } ?: SkinModelType.STEVE
        }.getOrElse {
            Logger.warning(TAG, "Can not get skin model type.")
            SkinModelType.NONE
        }

        download(skinUrl, skinFile)
        changeSkinModel(skinModelType)
    }
}
