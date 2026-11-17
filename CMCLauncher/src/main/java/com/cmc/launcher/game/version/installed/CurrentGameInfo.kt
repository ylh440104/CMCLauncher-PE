package com.cmc.launcher.game.version.installed

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.logging.Logger
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "CurrentGameInfo"

@Keep
data class CurrentGameInfo(
    @SerializedName("version")
    var version: String = "",
    @SerializedName("favoritesInfo")
    val favoritesMap: MutableMap<String, MutableSet<String>> = ConcurrentHashMap()
) {

    fun saveCurrentInfo(gameHome: String) {
        val infoFile = getInfoFile(gameHome)
        runCatching {
            FileUtils.writeByteArrayToFile(infoFile, GSON.toJson(this).toByteArray(Charsets.UTF_8))
            Logger.debug(TAG, "Current version $version has been saved to the config file.")
        }.onFailure { e ->
            Logger.error(TAG, "Save failed: ${infoFile.absolutePath}", e)
        }
    }
}

private fun getInfoFile(gameHome: String) = File(gameHome, "cmc-game.cfg")

fun refreshCurrentInfo(gameHome: String): CurrentGameInfo {
    val infoFile = getInfoFile(gameHome)

    return runCatching {
        when {
            infoFile.exists() -> loadFromJsonFile(infoFile)
            else -> createNewConfig(gameHome)
        }
    }.getOrElse { e ->
        Logger.error(TAG, "Refresh failed", e)
        createNewConfig(gameHome)
    }
}

private fun loadFromJsonFile(infoFile: File): CurrentGameInfo {
    return GSON.fromJson(infoFile.readText(), CurrentGameInfo::class.java).also { info ->
        checkNotNull(info) { "Deserialization returned null" }
    }
}

private fun createNewConfig(gameHome: String) = CurrentGameInfo().applyPostActions(gameHome)

private fun CurrentGameInfo.applyPostActions(gameHome: String): CurrentGameInfo {
    saveCurrentInfo(gameHome)
    return this
}
