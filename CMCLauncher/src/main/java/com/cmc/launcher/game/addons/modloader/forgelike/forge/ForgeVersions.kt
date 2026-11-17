package com.cmc.launcher.game.addons.modloader.forgelike.forge

import com.cmc.launcher.game.addons.mirror.MirrorSource
import com.cmc.launcher.game.addons.mirror.SourceType
import com.cmc.launcher.game.addons.mirror.orderedByGameSourcePreference
import com.cmc.launcher.game.addons.mirror.runMirrorable
import com.cmc.launcher.game.addons.modloader.ResponseTooShortException
import com.cmc.launcher.path.GLOBAL_CLIENT
import com.cmc.launcher.path.URL_USER_AGENT
import com.cmc.launcher.utils.isChinaMainland
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.network.safeBodyAsJson
import com.cmc.launcher.utils.network.safeBodyAsText
import com.cmc.launcher.utils.network.withRetry
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object ForgeVersions {
    private const val TAG = "ForgeVersions"
    private const val FORGE_FILE_URL = "https://files.minecraftforge.net/maven/net/minecraftforge/forge"

    suspend fun fetchForgeList(mcVersion: String): List<ForgeVersion>? = withContext(Dispatchers.Default) {
        if (isChinaMainland()) {
            runMirrorable(
                listOf(officialSource(mcVersion), bmclapiSource(mcVersion)).orderedByGameSourcePreference()
            )
        } else {
            fetchListWithOfficial(mcVersion)
        }?.sortedWith { o1, o2 ->
            o2.forgeBuildVersion.compareTo(o1.forgeBuildVersion)
        }
    }

    private fun officialSource(mcVersion: String): MirrorSource<List<ForgeVersion>?> = MirrorSource(
        type = SourceType.OFFICIAL
    ) {
        fetchListWithOfficial(mcVersion)
    }

    private fun bmclapiSource(mcVersion: String): MirrorSource<List<ForgeVersion>?> = MirrorSource(
        type = SourceType.BMCLAPI
    ) {
        fetchListWithBMCLAPI(mcVersion)
    }

    private suspend fun fetchListWithOfficial(mcVersion: String) = withContext(Dispatchers.IO) {
        val url = "https://files.minecraftforge.net/maven/net/minecraftforge/forge/index_${
            mcVersion.replace("-", "_")
        }.html"

        try {
            val html = withContext(Dispatchers.IO) {
                withRetry(TAG, maxRetries = 2) {
                    GLOBAL_CLIENT.get(url) {
                        headers {
                            append(HttpHeaders.UserAgent, "Mozilla/5.0/$URL_USER_AGENT")
                        }
                    }.safeBodyAsText()
                }
            }

            if (html.length < 100) throw ResponseTooShortException("Response too short")

            html.split("<td class=\"download-version")
                .drop(1)
                .mapNotNull { parseVersionFromHtml(it, mcVersion) }
        } catch (e: ClientRequestException) {
            val statusCode = e.response.status
            if (statusCode == HttpStatusCode.NotFound) {
                Logger.debug(TAG, "Not found.")
                null
            } else {
                throw e
            }
        } catch (_: CancellationException) {
            Logger.debug(TAG, "Client cancelled.")
            null
        } catch (e: Exception) {
            throw RuntimeException("Failed to fetch forge list! url: $url", e)
        }
    }

    private suspend fun fetchListWithBMCLAPI(mcVersion: String): List<ForgeVersion>? {
        val url = "https://bmclapi2.bangbang93.com/forge/minecraft/${mcVersion.replace("-", "_")}"

        return try {
            val tokens: List<ForgeVersionToken> = withContext(Dispatchers.IO) {
                withRetry(TAG, maxRetries = 2) {
                    GLOBAL_CLIENT.get(url).safeBodyAsJson()
                }
            }

            tokens.map { token ->
                val (hash, category) = determinePreferredFile(token.files)
                val formattedDate = ZonedDateTime.parse(token.modified, DateTimeFormatter.ISO_ZONED_DATE_TIME)
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))

                ForgeVersion(
                    versionName = token.version,
                    branch = token.branch,
                    inherit = mcVersion,
                    releaseTime = formattedDate,
                    hash = hash,
                    isRecommended = false,
                    category = category,
                    fileVersion = token.branch?.let { "${token.version}-$it" } ?: token.version
                )
            }
        } catch (e: ClientRequestException) {
            val statusCode = e.response.status
            if (statusCode == HttpStatusCode.NotFound) {
                Logger.debug(TAG, "Not found.")
                null
            } else {
                throw e
            }
        } catch (_: CancellationException) {
            Logger.debug(TAG, "Client cancelled.")
            null
        } catch (e: Exception) {
            throw RuntimeException("Failed to fetch forge list! url: $url", e)
        }
    }

    private fun determinePreferredFile(files: List<ForgeVersionToken.ForgeFile>): Pair<String?, String> {
        var hash: String? = null
        var category = "unknown"
        var priority = -1

        fun updateSelection(file: ForgeVersionToken.ForgeFile, newCategory: String, newPriority: Int) {
            hash = file.hash
            category = newCategory
            priority = newPriority
        }

        files.forEach { file ->
            when {
                file.isInstallerJar() && priority <= 2 -> updateSelection(file, "installer", 2)
                file.isUniversalZip() && priority <= 1 -> updateSelection(file, "universal", 1)
                file.isClientZip() && priority <= 0 -> updateSelection(file, "client", 0)
            }
        }
        return hash to category
    }

    fun getDownloadUrl(version: ForgeVersion) =
        "$FORGE_FILE_URL/${version.inherit}-${version.fileVersion}/forge-${version.inherit}-${version.fileVersion}-${version.category}.${version.fileExtension}"

    private fun parseVersionFromHtml(html: String, mcVersion: String): ForgeVersion? {
        return try {
            val name = Regex("""(?<=\D)\d+(\.\d+)+""").find(html)?.value ?: return null

            val isRecommended = html.contains("promo-latest  fa" ) || html.contains("promo-recommended  fa" )
            val branch = Regex("""(?<=-$name-)[^-"]+(?=-[a-z]+\.[a-z]{3})""").find(html)?.value
            val timeStr = Regex("""(?<="download-time" title=")[^"]+""").find(html)?.value ?: return null

            val dateTime = LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))

            val (category, hash) = when {
                html.contains("classifier-installer") -> parseInstaller(html)
                html.contains("classifier-universal") -> parseUniversal(html)
                html.contains("client.zip") -> parseClient(html)
                else -> return null
            } ?: return null

            ForgeVersion(
                versionName = name,
                branch = branch,
                inherit = mcVersion,
                releaseTime = dateTime,
                hash = hash,
                isRecommended = isRecommended,
                category = category,
                fileVersion = "$name${branch?.let { "-$it" } ?: ""}"
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseInstaller(html: String): Pair<String, String>? {
        val section = html.substringAfter("installer.jar")
        val hash = Regex("""(?<=MD5:</strong> )[^<]+""").find(section)?.value?.trim()
        return hash?.let { "installer" to it }
    }

    private fun parseUniversal(html: String): Pair<String, String>? {
        val section = html.substringAfter("universal.zip")
        val hash = Regex("""(?<=MD5:</strong> )[^<]+""").find(section)?.value?.trim()
        return hash?.let { "universal" to it }
    }

    private fun parseClient(html: String): Pair<String, String>? {
        val section = html.substringAfter("client.zip")
        val hash = Regex("""(?<=MD5:</strong> )[^<]+""").find(section)?.value?.trim()
        return hash?.let { "client" to it }
    }
}
