package com.cmc.launcher.game.download.addon.platform.curseforge.models

import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformDependencyType
import com.cmc.launcher.game.download.addon.platform.PlatformDisplayLabel
import com.cmc.launcher.game.download.addon.platform.PlatformReleaseType
import com.cmc.launcher.game.download.addon.platform.PlatformVersion
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeFile.Hash
import com.cmc.launcher.game.download.addon.platform.mirroredCurseForgeSource
import com.cmc.launcher.game.download.addon.platform.mirroredPlatformSearcher
import com.cmc.launcher.game.versioninfo.filterRelease
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.string.parseInstant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant

@Serializable
class CurseForgeFile(

    @SerialName("id")
    val id: Int,

    @SerialName("gameId")
    val gameId: Int,

    @SerialName("modId")
    val modId: Int,

    @SerialName("isAvailable")
    val isAvailable: Boolean,

    @SerialName("displayName")
    val displayName: String,

    @SerialName("fileName")
    val fileName: String? = null,

    @SerialName("releaseType")
    val releaseType: PlatformReleaseType,

    @SerialName("fileStatus")
    val fileStatus: Int,

    @SerialName("hashes")
    val hashes: Array<Hash>,

    @SerialName("fileDate")
    val fileDate: String,

    @SerialName("fileLength")
    val fileLength: Long,

    @SerialName("downloadCount")
    val downloadCount: Long,

    @SerialName("fileSizeOnDisk")
    val fileSizeOnDisk: Long? = null,

    @SerialName("downloadUrl")
    val downloadUrl: String? = null,

    @SerialName("gameVersions")
    val gameVersions: Array<String>,

    @SerialName("sortableGameVersions")
    val sortableGameVersions: JsonArray,

    @SerialName("dependencies")
    val dependencies: Array<Dependency>,

    @SerialName("exposeAsAlternative")
    val exposeAsAlternative: Boolean? = null,

    @SerialName("parentProjectFileId")
    val parentProjectFileId: Int? = null,

    @SerialName("alternateFileId")
    val alternateFileId: Int? = null,

    @SerialName("isServerPack")
    val isServerPack: Boolean? = null,

    @SerialName("serverPackFileId")
    val serverPackFileId: Int? = null,

    @SerialName("isEarlyAccessContent")
    val isEarlyAccessContent: Boolean? = null,

    @SerialName("earlyAccessEndDate")
    val earlyAccessEndDate: String? = null,

    @SerialName("fileFingerprint")
    val fileFingerprint: Long,

    @SerialName("modules")
    val modules: Array<Module>? = null
) : PlatformVersion {
    @Serializable
    class Hash(
        @SerialName("value")
        val value: String,
        @SerialName("algo")
        val algo: Algo
    ) {
        @Serializable(with = Algo.Serializer::class)
        enum class Algo(val code: Int) {
            SHA1(1),
            MD5(2);

            companion object {
                private val map = entries.associateBy { it.code }
                fun fromCode(code: Int): Algo = map[code] ?: error("Unknown algo code: $code")
            }

            object Serializer : KSerializer<Algo> {
                override val descriptor: SerialDescriptor =
                    PrimitiveSerialDescriptor("Algo", PrimitiveKind.INT)

                override fun deserialize(decoder: Decoder): Algo {
                    val code = decoder.decodeInt()
                    return Algo.fromCode(code)
                }

                override fun serialize(encoder: Encoder, value: Algo) {
                    encoder.encodeInt(value.code)
                }
            }
        }
    }

    @Serializable
    class Dependency(
        @SerialName("modId")
        val modId: Int,
        @SerialName("relationType")
        val relationType: PlatformDependencyType
    )

    @Serializable
    class Module(
        @SerialName("name")
        val name: String,
        @SerialName("fingerprint")
        val fingerprint: Long
    )

    @Transient
    private lateinit var thisPrimaryFile: CurseForgeFile

    @Transient
    private lateinit var primaryDownloadUrl: String

    override suspend fun initFile(currentProjectId: String): Boolean {
        val file = this.takeIf { it.fileName != null && it.fixedFileUrl() != null }

            ?: run {
                val fileId = id.toString()
                runCatching {
                    mirroredPlatformSearcher(
                        searchers = mirroredCurseForgeSource()
                    ) { searcher ->
                        searcher.getVersion(
                            projectID = currentProjectId,
                            fileID = fileId
                        )
                    }.data
                }.onFailure { e ->
                    when (e) {
                        is FileNotFoundException -> Logger.warning("InitCFFile", "Could not query api.curseforge.com for deleted mods: $currentProjectId, $fileId", e)
                        is IOException, is SerializationException -> Logger.warning("InitCFFile", "Unable to fetch the file name projectID=$currentProjectId, fileID=$fileId", e)
                    }
                }.getOrNull() ?: return false
            }
        val link = file.fixedFileUrl() ?: run {
            Logger.warning("InitCFFile", "No download link available, projectID=$currentProjectId, fileID=${file.id}")
            return false
        }

        thisPrimaryFile = file
        primaryDownloadUrl = link
        return true
    }

    override fun platform(): Platform = Platform.CURSEFORGE

    override fun platformId(): String = id.toString()

    override fun platformProjectId(): String = modId.toString()

    override fun platformDisplayName(): String = thisPrimaryFile.displayName

    override fun platformFileName(): String = thisPrimaryFile.fileName!!

    override fun platformGameVersion(): Array<String> {
        return thisPrimaryFile.gameVersions.filter { gameVersion ->
            filterRelease(gameVersion)
        }.toTypedArray()
    }

    override fun platformLoaders(): List<PlatformDisplayLabel> {
        return thisPrimaryFile.gameVersions.mapNotNull { loaderName ->
            CurseForgeModLoader.entries.find {
                it.getDisplayName().equals(loaderName, true)
            }
        }
    }

    override fun platformReleaseType(): PlatformReleaseType = thisPrimaryFile.releaseType

    override fun platformDependencies(): List<PlatformVersion.PlatformDependency> {
        return thisPrimaryFile.dependencies.map { dependency ->
            PlatformVersion.PlatformDependency(
                platform = platform(),
                projectId = dependency.modId.toString(),
                type = dependency.relationType
            )
        }
    }

    override fun platformDownloadCount(): Long = thisPrimaryFile.downloadCount

    override fun platformDownloadUrl(): String = primaryDownloadUrl

    override fun platformDatePublished(): Instant = parseInstant(thisPrimaryFile.fileDate)

    override fun platformSha1(): String? = thisPrimaryFile.getSHA1()

    override fun platformFileSize(): Long = thisPrimaryFile.fileLength

    override fun platformVersion(): String = displayName
}

fun CurseForgeFile.fixedFileUrl(): String? {
    return downloadUrl
        ?: fileName?.let { curseForgeCDNUrl(id, it) }
}

fun curseForgeCDNUrl(fileId: Int, fileName: String): String {
    val encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
    return "https://edge.forgecdn.net/files/${fileId / 1000}/${fileId % 1000}/$encoded"
}

fun CurseForgeFile.getSHA1(): String? {
    return hashes.find { hash ->
        hash.algo == Hash.Algo.SHA1
    }?.value
}
