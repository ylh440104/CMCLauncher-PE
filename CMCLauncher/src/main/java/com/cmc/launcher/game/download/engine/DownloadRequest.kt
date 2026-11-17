package com.cmc.launcher.game.download.engine

import java.io.File
import java.io.IOException

class DownloadRequest(
    val urls: List<String>,
    val targetFile: File,
    val sha1: String? = null,

    val expectedSize: Long = -1L,

    val tag: Any? = null
) {
    init {
        require(urls.isNotEmpty()) { "Download requires at least one url" }
    }

    override fun toString(): String = targetFile.name
}

fun Throwable.findHttpCode(): Int? =
    generateSequence(this) { it.cause }
        .filterIsInstance<HttpResultException>()
        .firstOrNull()
        ?.code

class AllSourcesFailedException(
    summary: String,
    cause: Throwable?
) : IOException(summary, cause)

class HttpResultException(
    val code: Int,
    message: String
) : IOException(message)
