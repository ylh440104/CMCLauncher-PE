package com.cmc.launcher.game.download.addon.favorites

import com.cmc.launcher.game.download.addon.platform.Platform
import com.tencent.mmkv.MMKV

fun favoritesModrinth(): MMKV = MMKV.mmkvWithID("FavoriteModrinthProject")

fun favoritesCurseForge(): MMKV = MMKV.mmkvWithID("FavoriteCurseForgeProject")

fun favoritesMMKV(platform: Platform): MMKV = when (platform) {
    Platform.MODRINTH -> favoritesModrinth()
    Platform.CURSEFORGE -> favoritesCurseForge()
}
