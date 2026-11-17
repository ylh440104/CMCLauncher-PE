package com.cmc.launcher.ui.screens

import com.cmc.launcher.R
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.ui.screens.splash.SplashOperation
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

sealed interface NestedNavKey {

    @Serializable class Splash : BackStackNavKey<TitledNavKey>() {
        init {
            backStack.addIfEmpty(SplashOperation.Loading)
        }
    }

    @Serializable class Main : BackStackNavKey<TitledNavKey>() {
        init {
            backStack.addIfEmpty(NormalNavKey.LauncherMain)
        }
    }

    @Serializable class Settings : BackStackNavKey<TitledNavKey>(androidText(R.string.generic_setting)) {
        init {
            backStack.addIfEmpty(NormalNavKey.Settings.JavaManager)
        }
    }

    @Serializable
    class VersionSettings(@Contextual val version: Version) : BackStackNavKey<TitledNavKey>(
        androidText(R.string.page_title_version_manage)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.Versions.OverView)
        }
    }

    @Serializable
    class VersionExport(@Contextual val version: Version) : BackStackNavKey<TitledNavKey>(
        androidText(R.string.versions_export)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.VersionExports.SelectType)
        }
    }

    @Serializable class Download : BackStackNavKey<TitledNavKey>(
        androidText(R.string.generic_download)
    )

    @Serializable class DownloadGame : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_game)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.DownloadGame.SelectGameVersion)
        }
    }

    @Serializable class DownloadModPack : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_modpack)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.SearchModPack)
        }
    }

    @Serializable class DownloadMod : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_mod)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.SearchMod)
        }
    }

    @Serializable class DownloadResourcePack : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_resource_pack)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.SearchResourcePack)
        }
    }

    @Serializable class DownloadSaves : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_saves)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.SearchSaves)
        }
    }

    @Serializable class DownloadShaders : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_shaders)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.SearchShaders)
        }
    }

    @Serializable class DownloadFavorites : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_favorites)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.Favorites)
        }
    }

    @Serializable
    class AddonInfo(
        val platform: Platform,
        val projectId: String,
        val classes: PlatformClasses
    ) : BackStackNavKey<TitledNavKey>(
        androidText(R.string.generic_download)
    ) {
        init {
            backStack.addIfEmpty(
                NormalNavKey.DownloadAddon(platform, projectId, classes)
            )
        }
    }
}
