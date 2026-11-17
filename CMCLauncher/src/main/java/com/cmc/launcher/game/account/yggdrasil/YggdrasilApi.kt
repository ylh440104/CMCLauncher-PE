package com.cmc.launcher.game.account.yggdrasil

import com.cmc.launcher.game.account.microsoft.MinecraftProfileException
import com.cmc.launcher.game.account.microsoft.MinecraftProfileException.ExceptionStatus.FREQUENT
import com.cmc.launcher.game.account.microsoft.MinecraftProfileException.ExceptionStatus.PROFILE_NOT_EXISTS
import com.cmc.launcher.game.account.wardrobe.SkinModelType
import com.cmc.launcher.game.download.engine.BatchDownloader
import com.cmc.launcher.game.version.download.DownloadTask
import com.cmc.launcher.path.GLOBAL_CLIENT
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.network.safeBodyAsJson
import com.cmc.launcher.utils.network.withRetry
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "YggdrasilApi"

suspend fun uploadSkin(
    apiUrl: String,
    accessToken: String,
    file: File,
    modelType: SkinModelType,
    maxRetries: Int = 1
) {
    val skinData = file.readBytes()
    val logTag = "YggdrasilApi.uploadSkin"

    Logger.info(TAG, "$logTag: uploading skin -> ${file.name}")
    withRetry(logTag = logTag, maxRetries = maxRetries) {
        GLOBAL_CLIENT.submitFormWithBinaryData(
            url = "$apiUrl/minecraft/profile/skins",
            formData = formData {
                append("variant", modelType.modelType)
                append("file", skinData, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                })
            }
        ) {
            method = HttpMethod.Post
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
    }
}

suspend fun changeCape(
    apiUrl: String,
    accessToken: String,
    capeId: String = "",
    maxRetries: Int = 1
) {
    val url = "$apiUrl/minecraft/profile/capes/active"
    val logTag = "YggdrasilApi.changeCape"

    if (capeId.isBlank()) {

        Logger.info(TAG, "$logTag: reset cape")
        withRetry(logTag = logTag, maxRetries = maxRetries) {
            GLOBAL_CLIENT.request(url) {
                method = HttpMethod.Delete
                headers {
                    append("Authorization", "Bearer $accessToken")
                    append("Content-Type", "application/json")
                }
            }
        }
    } else {
        Logger.info(TAG, "$logTag: capeId -> $capeId")
        withRetry(logTag = logTag, maxRetries = maxRetries) {
            GLOBAL_CLIENT.request(url) {
                method = HttpMethod.Put
                headers {
                    append("Authorization", "Bearer $accessToken")
                    append("Content-Type", "application/json")
                }
                setBody(JsonObject(mapOf("capeId" to JsonPrimitive(capeId))))
            }
        }
    }
}

suspend fun getPlayerProfile(
    apiUrl: String,
    accessToken: String
) = runCatching {
    GLOBAL_CLIENT.get("$apiUrl/minecraft/profile") {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }.safeBodyAsJson<PlayerProfile>()
}.onFailure { e ->
    if (e is ResponseException) {
        when (e.response.status.value) {
            429 -> throw MinecraftProfileException(FREQUENT)
            404 -> throw MinecraftProfileException(PROFILE_NOT_EXISTS)
        }
    }
}.getOrThrow()

suspend fun cacheAllCapes(
    profile: PlayerProfile,
    maxThreads: Int = 6
) = runCatching {
    withContext(Dispatchers.IO) {
        val tasks = profile.capes.mapNotNull { cape ->
            val file = cape.getFile(PathManager.DIR_ACCOUNT_CAPE)
            if (file.exists()) {
                if (file.lastModified() + TimeUnit.DAYS.toMillis(7) < System.currentTimeMillis()) {

                    FileUtils.deleteQuietly(file)
                } else {
                    return@mapNotNull null
                }
            }
            DownloadTask(
                urls = listOf(cape.url),
                verifyIntegrity = false,
                targetFile = file
            )
        }

        if (tasks.isNotEmpty()) {
            BatchDownloader(
                requests = tasks.map { it.toRequest() },
                maxConnections = maxThreads,
                retryRounds = 0
            ).run()
        }
    }
}.onFailure { e ->
    if (e is ResponseException) {
        when (e.response.status.value) {
            429 -> throw MinecraftProfileException(FREQUENT)
            404 -> throw MinecraftProfileException(PROFILE_NOT_EXISTS)
        }
    }
}.getOrThrow()

suspend fun executeWithAuthorization(
    block: suspend () -> Unit,
    onRefreshRequest: suspend () -> Unit
) {
    var refreshed = false
    while (true) {
        try {
            block()
            break
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.Unauthorized) {
                if (refreshed) throw e
                onRefreshRequest()
                refreshed = true
                continue
            } else throw e
        }
    }
}
