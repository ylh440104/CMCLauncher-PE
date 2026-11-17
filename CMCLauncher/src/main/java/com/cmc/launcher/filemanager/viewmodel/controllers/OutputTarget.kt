package com.cmc.launcher.filemanager.viewmodel.controllers

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.nio.file.Path

internal sealed interface OutputTarget {

    data class Local(val dir: Path) : OutputTarget

    data class Saf(val treeUri: Uri) : OutputTarget

    data class SafDir(val treeUri: Uri, val name: String) : OutputTarget
}

internal fun findChildDocument(context: Context, treeUri: Uri, name: String): Uri? {
    return runCatching {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val child = DocumentsContract.buildDocumentUriUsingTree(treeUri, "$docId/$name")
        if (DocumentsContract.isDocumentUri(context, child)) {

            context.contentResolver.openInputStream(child)?.close()
            child
        } else null
    }.getOrNull()
}

internal fun guessMime(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".zip") -> "application/zip"
        lower.endsWith(".7z") -> "application/x-7z-compressed"
        lower.endsWith(".tar") -> "application/x-tar"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".gif") -> "image/gif"
        lower.endsWith(".json") -> "application/json"
        lower.endsWith(".txt") || lower.endsWith(".log") -> "text/plain"
        lower.endsWith(".xml") -> "text/xml"
        lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
        else -> "application/octet-stream"
    }
}
