package com.cmc.launcher.game.account.wardrobe

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
enum class SkinModelType(
    val string: String,
    val targetParity: Int,
    val modelType: String
): Parcelable {

    NONE("none", -1, ""),

    STEVE("wide", 0, "classic"),

    ALEX("slim", 1, "slim")
}
