package com.cmc.launcher.notification

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NoticeProgress(
    val max: Int,
    val progress: Int,
    val indeterminate: Boolean = false
): Parcelable
