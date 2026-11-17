package com.cmc.launcher.game.version.mod

import android.os.Parcelable
import com.cmc.launcher.game.download.addon.platform.ModLoaderDisplayLabel
import com.cmc.launcher.game.download.addon.platform.Platform
import kotlinx.parcelize.Parcelize

@Parcelize
class ModFile(
    val id: String,
    val projectId: String,
    val platform: Platform,
    val loaders: Array<ModLoaderDisplayLabel>,
    val datePublished: String
): Parcelable
