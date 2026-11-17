package com.cmc.launcher.game.download.addon.favorites

import android.os.Parcelable
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import kotlinx.parcelize.Parcelize

@Parcelize
class FavoriteProject(
    val projectId: String,
    val iconUrl: String? = null,
    val title: String,
    val description: String = "",
    val authors: List<String>? = null,
    val classes: PlatformClasses,
    val followTime: Long
) : Parcelable
