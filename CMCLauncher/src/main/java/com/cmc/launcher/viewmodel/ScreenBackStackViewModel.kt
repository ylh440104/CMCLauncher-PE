package com.cmc.launcher.viewmodel

import androidx.lifecycle.ViewModel
import com.cmc.launcher.ui.screens.NestedNavKey
import com.cmc.launcher.ui.screens.NormalNavKey

class ScreenBackStackViewModel : ViewModel() {

    val mainScreen = NestedNavKey.Main()

    val settingsScreen = NestedNavKey.Settings()

    val downloadScreen = NestedNavKey.Download()

    val downloadGameScreen = NestedNavKey.DownloadGame()

    val downloadModPackScreen = NestedNavKey.DownloadModPack()

    val downloadModScreen = NestedNavKey.DownloadMod()

    val downloadResourcePackScreen = NestedNavKey.DownloadResourcePack()

    val downloadSavesScreen = NestedNavKey.DownloadSaves()

    val downloadShadersScreen = NestedNavKey.DownloadShaders()

    val downloadFavoritesScreen = NestedNavKey.DownloadFavorites()

    val clearBeforeNavKeys = listOf(
        settingsScreen::class,
        downloadScreen::class,
        NormalNavKey.Multiplayer::class
    )
}
