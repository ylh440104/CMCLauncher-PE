package com.cmc.launcher.ui.control.mouse

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CursorHotspot(
    val xPercent: Int,
    val yPercent: Int
): Parcelable

val CENTER_HOTSPOT = CursorHotspot(xPercent = 50, yPercent = 50)

val LEFT_TOP_HOTSPOT = CursorHotspot(xPercent = 0, yPercent = 0)
