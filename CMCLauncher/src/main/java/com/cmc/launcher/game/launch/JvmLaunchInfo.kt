package com.cmc.launcher.game.launch

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class JvmLaunchInfo(
    val jvmArgs: String,
    val jreName: String? = null,
    val userHome: String? = null,
    val useUserJvm: Boolean = true,
) : Parcelable
