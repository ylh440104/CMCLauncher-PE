package com.cmc.launcher.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cmc.launcher.crashlogs.AbstractAPI
import com.cmc.launcher.crashlogs.LinkNotFoundException
import com.cmc.launcher.crashlogs.MCLogsResponse
import com.cmc.launcher.crashlogs.platform.MCLogsAPI
import com.cmc.launcher.crashlogs.platform.MirroredAPI
import com.cmc.launcher.ui.screens.main.crashlogs.ShareLinkOperation
import com.cmc.launcher.utils.isChinaMainland
import com.cmc.launcher.utils.network.isInterruptedIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class LogsUploadViewModel: ViewModel() {
    private var uploadJob: Job? = null
    private var checkJob: Job? = null

    var operation by mutableStateOf<ShareLinkOperation>(ShareLinkOperation.None)

    var canUpload by mutableStateOf(false)
        private set

    fun check(logFile: File) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch(Dispatchers.IO) {

            val canUpload0 = !logFile.exceeds2MB()
            withContext(Dispatchers.Main) {
                canUpload = canUpload0
            }
        }
    }

    private fun File.exceeds2MB(): Boolean {
        return !exists() && !isFile && length() > 2 * 1024 * 1024
    }

    private suspend fun <E: AbstractAPI> mirroredAPI(
        list: List<E>,
        content: String
    ): MCLogsResponse {
        require(list.isNotEmpty()) { "API list must not be empty." }

        val errors = mutableListOf<Exception>()
        var lastException: Exception? = null

        for (api in list) {
            try {
                withContext(Dispatchers.Main) {
                    operation = ShareLinkOperation.Uploading(api.root)
                }

                return api.onUpload(content)
            } catch (e: Exception) {
                lastException = e

                if (e.isInterruptedIOException()) {
                    throw e
                } else {
                    errors.add(e)
                }
            }
        }

        throw IOException("All sources have failed to attempt", lastException).apply {
            errors.forEachIndexed { i, e ->
                addSuppressed(Exception("Mirror error #${i + 1}: ${e.message}"))
            }
        }
    }

    fun upload(
        logFile: File,
        onSuccess: (link: String) -> Unit
    ) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch(Dispatchers.IO) {
            if (logFile.exceeds2MB()) return@launch

            val content = logFile.readText()

            val apiList = if (isChinaMainland()) {
                listOf(MirroredAPI, MCLogsAPI)
            } else {
                listOf(MCLogsAPI)
            }

            try {
                val response = mirroredAPI(
                    list = apiList,
                    content = content
                )
                withContext(Dispatchers.Main) {
                    val link = response.url
                    if (link == null) {

                        operation = ShareLinkOperation.Error(
                            LinkNotFoundException()
                        )
                    } else {
                        onSuccess(link.replace("\\/", "/"))
                        operation = ShareLinkOperation.None
                    }
                }
            } catch (e: Exception) {
                if (e.isInterruptedIOException()) {
                    return@launch
                } else {
                    withContext(Dispatchers.Main) {
                        operation = ShareLinkOperation.Error(e)
                    }
                }
            }
        }
    }

    fun cancel() {
        uploadJob?.cancel()
        uploadJob = null
        operation = ShareLinkOperation.None
    }

    override fun onCleared() {
        checkJob?.cancel()
        checkJob = null
        cancel()
    }
}
