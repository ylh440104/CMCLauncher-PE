package com.cmc.launcher.contract

import android.webkit.MimeTypeMap

fun String.extensionToMimeType(): String {
    val extensionMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(this)
    return extensionMimeType ?: "*/*"
}
