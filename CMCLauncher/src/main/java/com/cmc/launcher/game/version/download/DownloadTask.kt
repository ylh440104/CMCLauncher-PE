package com.cmc.launcher.game.version.download

import com.cmc.launcher.game.download.engine.BatchDownloader
import com.cmc.launcher.game.download.engine.DownloadRequest
import com.cmc.launcher.utils.file.check7z
import com.cmc.launcher.utils.file.checkZip
import com.cmc.launcher.utils.file.compareSHA1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File

class DownloadTask(
    val urls: List<String>,
    private val verifyIntegrity: Boolean,
    val targetFile: File,
    val sha1: String? = null,

    val size: Long = -1L,

    val isDownloadable: Boolean = true
) {
    init {
        require(urls.isNotEmpty()) { "DownloadTask requires at least one url" }
    }

    var fileDownloadedTask: (suspend () -> Unit)? = null

    internal fun toRequest(): DownloadRequest = DownloadRequest(
        urls = urls,
        targetFile = targetFile,
        sha1 = sha1,
        expectedSize = size,
        tag = this
    )

    internal suspend fun runFileDownloadedTask() {
        withContext(Dispatchers.IO) {
            fileDownloadedTask?.invoke()
        }
    }

    fun existingFileValid(): Boolean {
        val file = targetFile
        if (!file.exists()) return false
        if (!verifyIntegrity) return true

        if (sha1.isNullOrBlank()) {

            if (!isDownloadable) return true
            return archiveOrPlainValid(file)
        }

        if (compareSHA1(file, sha1)) return true
        FileUtils.deleteQuietly(file)
        return false
    }

    private fun archiveOrPlainValid(file: File): Boolean = when (file.extension.lowercase()) {
        "zip", "jar" -> checkZip(file)
        "7z" -> check7z(file)
        else -> true
    }
}
