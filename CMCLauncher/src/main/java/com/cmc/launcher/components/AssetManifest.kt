package com.cmc.launcher.components

import com.cmc.launcher.utils.file.calculateFileSha1
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

const val ASSET_MANIFEST_FILE = "manifest.json"

const val LEGACY_VERSION_FILE = "version"

private const val ASSET_MANIFEST_FORMAT = 1
private const val SHA1_ALGORITHM = "SHA-1"
private val SHA1_PATTERN = Regex("[0-9a-f]{40}")
private val manifestJson = Json { ignoreUnknownKeys = false }

@Serializable
data class AssetManifest(
    val formatVersion: Int,
    val component: String,
    val algorithm: String,
    val files: List<AssetManifestFile>
)

@Serializable
data class AssetManifestFile(
    val path: String,
    val size: Long,
    val sha1: String
)

data class ComponentFile(
    val assetPath: String,
    val outputPath: String,
    val size: Long,
    val sha1: String
)

fun AssetManifestFile.toComponentFile(outputPath: String = path): ComponentFile {
    validateComponentPath(outputPath)
    return ComponentFile(
        assetPath = path,
        outputPath = outputPath,
        size = size,
        sha1 = sha1
    )
}

fun parseAssetManifest(json: String, expectedComponent: String): AssetManifest {
    val manifest = manifestJson.decodeFromString(AssetManifest.serializer(), json)
    require(manifest.formatVersion == ASSET_MANIFEST_FORMAT) {
        "Unsupported asset manifest format: ${manifest.formatVersion}"
    }
    require(manifest.component == expectedComponent) {
        "Asset manifest component mismatch: ${manifest.component}"
    }
    require(manifest.algorithm == SHA1_ALGORITHM) {
        "Unsupported asset manifest algorithm: ${manifest.algorithm}"
    }
    require(manifest.files.isNotEmpty()) { "Asset manifest contains no files" }

    val paths = HashSet<String>(manifest.files.size)
    manifest.files.forEach { file ->
        validateComponentPath(file.path)
        require(file.path != ASSET_MANIFEST_FILE && file.path != LEGACY_VERSION_FILE) {
            "Asset manifest metadata must not be listed as a payload: ${file.path}"
        }
        require(paths.add(file.path)) { "Duplicate asset manifest path: ${file.path}" }
        require(file.size >= 0L) { "Invalid file size: ${file.path}" }
        require(SHA1_PATTERN.matches(file.sha1)) { "Invalid SHA-1: ${file.path}" }
    }
    return manifest
}

fun validateComponentFiles(files: List<ComponentFile>) {
    require(files.isNotEmpty()) { "Component contains no installable files" }
    val paths = HashSet<String>(files.size)
    files.forEach { file ->
        validateComponentPath(file.outputPath)
        require(paths.add(file.outputPath)) { "Duplicate component output path: ${file.outputPath}" }
    }
}

fun validateComponentPath(path: String) {
    require(path.isNotEmpty() && !path.startsWith('/') && !path.contains('\\')) {
        "Invalid component path: $path"
    }
    require(path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
        "Unsafe component path: $path"
    }
}

suspend fun verifyComponentDirectory(
    directory: File,
    files: List<ComponentFile>,
    ignoredFiles: Set<String> = emptySet()
): Boolean {
    if (!directory.isDirectory) return false

    files.forEach { expected ->
        val file = File(directory, expected.outputPath)
        if (!file.isFile || file.length() != expected.size) return false
        if (calculateFileSha1(file) != expected.sha1) return false
    }

    val expectedPaths = files.mapTo(HashSet(files.size)) { it.outputPath }
    val actualPaths = directory.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(directory).invariantSeparatorsPath }
        .filter { it !in ignoredFiles }
        .toSet()
    return actualPaths == expectedPaths
}
