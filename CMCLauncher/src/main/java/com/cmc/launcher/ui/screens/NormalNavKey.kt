package com.cmc.launcher.ui.screens

import com.cmc.launcher.R
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.versioninfo.MinecraftVersion
import com.cmc.launcher.ui.AndroidStringText
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.ui.screens.content.FirstLoginMenu
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

sealed interface NormalNavKey : TitledNavKey {
    @Contextual override val title: AndroidStringText?
        get() = null

    @Serializable data object LauncherMain : NormalNavKey

    @Serializable data class AccountManager(
        val loginMenu: FirstLoginMenu = FirstLoginMenu.NONE
    ) : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.page_title_account_list)
    }

    @Serializable data class WebScreen(val url: String) : NormalNavKey

    @Serializable data object VersionsManager : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.page_title_version_list)
    }

    @Serializable data class FileSelector(
        val startPath: String,
        val selectFile: Boolean,
        val saveKey: TitledNavKey,
        val onSelected: (path: String) -> Unit
    ) : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.page_title_select_files)
    }

    @Serializable data object Multiplayer: NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.terracotta_terracotta)
    }

    @Serializable data class LogView(
        val logPath: String
    ) : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.versions_overview_log)
    }

    sealed interface Settings : NormalNavKey {

        @Serializable data object JavaManager : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_java_manage)
        }

        @Serializable data object Mirror : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.generic_setting)
        }
    }

    sealed interface Versions : NormalNavKey {

        @Serializable data object OverView : Versions {
            @Contextual override val title: AndroidStringText = androidText(R.string.versions_settings_overview)
        }

        @Serializable data object Config : Versions {
            @Contextual override val title: AndroidStringText = androidText(R.string.versions_settings_config)
        }

        @Serializable data object ModifyVersion : Versions {
            @Contextual override val title: AndroidStringText = androidText(R.string.versions_modify_version)
        }

        @Serializable data object SelectGameVersion : Versions {
            @Contextual override val title: AndroidStringText = androidText(R.string.versions_modify_select_mc)
        }

        @Serializable data object ModsManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.mods_manage)
        }

        @Serializable data object SavesManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.saves_manage)
        }

        @Serializable data object ResourcePackManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.resource_pack_manage)
        }

        @Serializable data object ShadersManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.shader_pack_manage)
        }

        @Serializable data object ScreenshotsManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.screenshots_manage)
        }

        @Serializable data object ServerList : Versions {
            @Contextual override val title: AndroidStringText = androidText(R.string.servers_list)
        }
    }

    sealed interface VersionExports : NormalNavKey {

        @Serializable data object SelectType : VersionExports

        @Serializable data object EditInfo : VersionExports

        @Serializable data object SelectFiles : VersionExports
    }

    sealed interface DownloadGame : NormalNavKey {

        @Serializable data object SelectGameVersion : Versions

        @Serializable data class Addons(
            val gameVersion: String,
            val type: MinecraftVersion.Type
        ) : Versions
    }

    @Serializable data object SearchModPack : NormalNavKey

    @Serializable data object SearchMod : NormalNavKey

    @Serializable data object SearchResourcePack : NormalNavKey

    @Serializable data object SearchSaves : NormalNavKey

    @Serializable data object SearchShaders : NormalNavKey

    @Serializable data object SearchId : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.download_category_by_id)
    }

    @Serializable data object Favorites : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.download_category_favorites)
    }

    @Serializable data class DownloadAddon(
        val platform: Platform,
        val projectId: String,
        val classes: PlatformClasses,
        val iconUrl: String? = null
    ) : NormalNavKey
}
