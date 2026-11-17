package com.cmc.launcher.game.version.installed

import android.os.Parcelable
import androidx.annotation.Keep
import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.versioninfo.MinecraftVersion
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
class VersionInfo(
    val minecraftVersion: String,
    val quickPlay: QuickPlay,
    val loaderInfos: List<LoaderInfo>,
    val type: MinecraftVersion.Type,
): Parcelable {

    val primaryLoader: LoaderInfo?
        get() = PRIMARY_PRIORITY.firstNotNullOfOrNull { loader -> getLoader(loader) }

    fun getLoader(loader: ModLoader): LoaderInfo? {
        return loaderInfos.firstOrNull { it.loader == loader }
    }

    fun hasLoader(loader: ModLoader): Boolean {
        return getLoader(loader) != null
    }

    fun getInfoString(): String {
        val infoList = mutableListOf<String>().apply {
            add(minecraftVersion)
            loaderInfos.forEach { info ->
                info.takeIf { it.version.isNotBlank() }?.let {
                    add("${it.loader.displayName} - ${it.version}")
                }
            }
        }
        return infoList.joinToString(", ")
    }

    companion object {

        val PRIMARY_PRIORITY = listOf(
            ModLoader.FORGE, ModLoader.NEOFORGE,
            ModLoader.FABRIC, ModLoader.QUILT, ModLoader.LEGACY_FABRIC, ModLoader.BABRIC,
            ModLoader.OPTIFINE, ModLoader.LITE_LOADER
        )
    }

    @Keep
    @Parcelize
    data class LoaderInfo(
        val loader: ModLoader,
        val version: String
    ): Parcelable {

        fun getLoaderEnvKey(): String? {
            return when(loader) {
                ModLoader.OPTIFINE -> "INST_OPTIFINE"
                ModLoader.FORGE -> "INST_FORGE"
                ModLoader.NEOFORGE -> "INST_NEOFORGE"

                ModLoader.FABRIC,
                ModLoader.LEGACY_FABRIC,
                ModLoader.BABRIC
                    -> "INST_FABRIC"

                ModLoader.QUILT -> "INST_QUILT"
                ModLoader.LITE_LOADER -> "INST_LITELOADER"
                else -> null
            }
        }
    }

    @Keep
    @Parcelize
    data class QuickPlay(
        val hasQuickPlaysSupport: Boolean,
        val isQuickPlaySingleplayer: Boolean,
        val isQuickPlayMultiplayer: Boolean
    ): Parcelable
}
