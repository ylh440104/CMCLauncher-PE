package com.cmc.launcher.utils.file

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.string.naturalCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile as CompressZipFile

private const val TAG = "FileUtils"

fun File.ifExists() = this.takeIf { it.exists() }

fun compareSHA1(file: File, sourceSHA: String?, default: Boolean = false): Boolean {
    if (!file.exists()) return false

    val computedSHA = runCatching {
        FileInputStream(file).use { fis ->
            String(Hex.encodeHex(DigestUtils.sha1(fis)))
        }
    }.getOrElse { e ->
        Logger.info(TAG, "An exception occurred while reading, returning the default value.", e)
        return default
    }

    return sourceSHA?.equals(computedSHA, ignoreCase = true) ?: default
}

suspend fun calculateFileSha1(file: File): String = withContext(Dispatchers.IO) {
    require(file.exists()) { "File does not exist: ${file.absolutePath}" }
    require(file.isFile) { "Path is not a file: ${file.absolutePath}" }

    val digest = MessageDigest.getInstance("SHA-1")
    file.inputStream().use { stream ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            ensureActive()
            digest.update(buffer, 0, bytesRead)
        }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}

@SuppressLint("DefaultLocale")
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB")
    var unitIndex = 0
    var value = bytes.toDouble()

    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024.0
        unitIndex++
    }
    return String.format("%.2f %s", value, units[unitIndex])
}

fun sortWithFileName(o1: File, o2: File): Int {
    val isDir1 = o1.isDirectory
    val isDir2 = o2.isDirectory

    if (isDir1 && !isDir2) return -1
    if (!isDir1 && isDir2) return 1

    return naturalCompare(o1.name, o2.name)
}

const val INVALID_CHARACTERS_REGEX = "[\\\\/:*?\"<>|\\t\\n]"

@Throws(InvalidFilenameException::class)
fun checkFilenameValidity(str: String) {
    val illegalCharsRegex = INVALID_CHARACTERS_REGEX.toRegex()

    val illegalChars = illegalCharsRegex.findAll(str)
        .map { it.value }
        .distinct()
        .toMutableSet()

    if (str.contains("..")) {
        throw InvalidFilenameException("Filename contains path traversal sequence '..'", "..")
    }
    if (str.startsWith("/") || str.startsWith("\\")) {
        throw InvalidFilenameException("Filename cannot start with '/' or '\\'", str.first().toString())
    }

    findAllUnsafeUnicodeChars(str).takeIf { it.isNotEmpty() }?.let { chars ->
        illegalChars += chars
    }

    if (illegalChars.isNotEmpty()) {
        throw InvalidFilenameException("The filename contains illegal characters", illegalChars.joinToString(""))
    }

    if (str.length > 255) {
        throw InvalidFilenameException("Invalid filename length", str.length)
    }

    if (str.startsWith(" ") || str.endsWith(" ")) {
        throw InvalidFilenameException("The filename starts or ends with a space", true)
    }
}

fun findAllUnsafeUnicodeChars(name: String?): List<String> {
    if (name.isNullOrEmpty()) return emptyList()

    val unsafeChars = mutableListOf<String>()
    var i = 0
    while (i < name.length) {
        val cp = name.codePointAt(i)
        if (cp < 0x20 || (cp in 0xD800..0xDFFF) || cp > 0xFFFF) {
            unsafeChars += String(Character.toChars(cp))
        }
        i += Character.charCount(cp)
    }
    return unsafeChars
}

@Throws(IOException::class)
fun File.ensureDirectory(): File {
    if (isFile) throw IOException("Target directory is a file, path = $this")
    if (exists()) {
        if (!canWrite()) throw IOException("Target directory is not writable, path = $this")
    } else {
        if (!mkdirs()) throw IOException("Unable to create target directory, path = $this")
    }
    return this
}

@Throws(IOException::class)
fun File.ensureParentDirectory(): File {
    val parentDir: File = parentFile ?: throw IOException("targetFile does not have a parent, path = $this")
    parentDir.ensureDirectory()
    return this
}

fun File.ensureDirectorySilently(): Boolean {
    if (isFile) return false
    return if (exists()) canWrite()
    else mkdirs()
}

fun File.child(vararg paths: String): File {
    return paths.fold(this) { acc, path ->
        File(acc, path.trim().removeSurrounding("/").removeSurrounding("\\"))
    }
}

fun InputStream.readString(): String {
    return use {
        IOUtils.toString(this, StandardCharsets.UTF_8)
    }
}

fun shareFile(
    context: Context,
    file: File,
    cantProcess: () -> Unit = {}
) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooserIntent = Intent.createChooser(shareIntent, file.name)

    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooserIntent)
    } catch (_: ActivityNotFoundException) {
        cantProcess()
    }
}

fun ZipFile.readText(
    entryPath: String,
    readSource: BufferedSource.() -> String = {
        readUtf8()
    }
): String = getEntry(entryPath)
    .readText(zip = this, readSource = readSource)

fun ZipEntry.readText(
    zip: ZipFile,
    readSource: BufferedSource.() -> String = {
        readUtf8()
    }
): String {
    return zip.getInputStream(this)
        .source()
        .buffer()
        .use { bufferedSource ->
            bufferedSource.readSource()
        }
}

suspend fun ZipFile.extractFromZip(internalPath: String, outputDir: File) {
    val e = entries()
    val iterator = object : Iterator<JavaZipEntryAdapter> {
        override fun hasNext(): Boolean = e.hasMoreElements()
        override fun next(): JavaZipEntryAdapter =
            JavaZipEntryAdapter(e.nextElement())
    }

    extractZipEntries(
        entriesIter = iterator,
        inputStreamProvider = { entry -> getInputStream(entry.entry) },
        internalPath = internalPath,
        outputDir = outputDir
    )
}

suspend fun CompressZipFile.extractFromZip(internalPath: String, outputDir: File) {
    val entriesEnum = entries
    val iterator = object : Iterator<CompressZipEntryAdapter> {
        private val it = entriesEnum.iterator()
        override fun hasNext(): Boolean = it.hasNext()
        override fun next(): CompressZipEntryAdapter =
            CompressZipEntryAdapter(it.next() as ZipArchiveEntry)
    }

    extractZipEntries(
        entriesIter = iterator,
        inputStreamProvider = { entry -> getInputStream(entry.entry) },
        internalPath = internalPath,
        outputDir = outputDir
    )
}

private suspend fun <T : ZipEntryBase> extractZipEntries(
    entriesIter: Iterator<T>,
    inputStreamProvider: (T) -> InputStream,
    internalPath: String,
    outputDir: File
) {
    require(outputDir.isDirectory || outputDir.mkdirs()) {
        "Output directory does not exist and cannot be created: $outputDir"
    }

    val prefix = when {
        internalPath.isEmpty() -> ""
        internalPath.endsWith("/") -> internalPath
        else -> "$internalPath/"
    }

    val rootPath = outputDir.absoluteFile.toPath().normalize()

    val createdDirs = HashSet<String>()

    withContext(Dispatchers.IO) {
        while (entriesIter.hasNext()) {
            ensureActive()

            val entry = entriesIter.next()
            val name = entry.name

            if (!name.startsWith(prefix)) continue

            val relative = name.removePrefix(prefix)
            if (relative.isEmpty()) continue

            if (relative.contains("../") || relative.contains("..\\")) {
                throw SecurityException("Illegal path traversal detected: $name")
            }

            val targetFile = File(outputDir, relative)
            val targetPath = targetFile.toPath().normalize()

            if (!targetPath.startsWith(rootPath)) {
                throw SecurityException("Illegal path outside output directory: $name")
            }

            if (entry.isDirectory) {
                val absDir = targetFile.absolutePath
                if (createdDirs.add(absDir)) {
                    targetFile.mkdirs()
                }
                continue
            }

            val parent = targetFile.parentFile!!
            val parentPath = parent.absolutePath
            if (createdDirs.add(parentPath)) {
                parent.mkdirs()
            }

            inputStreamProvider(entry).source().use { source ->
                targetFile.sink().buffer().use { sink ->
                    sink.writeAll(source)
                }
            }
        }
    }
}

fun ZipFile.extractEntryToFile(entryPath: String, outputFile: File) {
    val entry = getEntry(entryPath) ?: throw IllegalArgumentException("ZIP entry does not exist: $entryPath")
    this.extractEntryToFile(entry, outputFile)
}

fun ZipFile.extractEntryToFile(entry: ZipEntry, outputFile: File) {
    require(!entry.isDirectory) { "Cannot extract directory to file: ${entry.name}" }

    outputFile.ensureParentDirectory()

    getInputStream(entry).use { input ->
        outputFile.outputStream().use { output ->
            input.source().buffer().use { source ->
                output.sink().buffer().use { sink ->
                    sink.writeAll(source)
                    sink.flush()
                }
            }
        }
    }
}

suspend fun zipDirectory(
    sourceDir: File,
    outputZipFile: File,
    preserveFileTime: Boolean = true
) = withContext(Dispatchers.IO) {
    if (!sourceDir.exists() || !sourceDir.isDirectory) {
        throw IllegalArgumentException("Source path must be an existing directory")
    }

    ZipOutputStream(FileOutputStream(outputZipFile)).use { zipOut ->
        sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val entryName = file.relativeTo(sourceDir).path.replace("\\", "/")
            val zipEntry = ZipEntry(entryName)
            if (preserveFileTime) {
                zipEntry.time = file.lastModified()
            }
            zipOut.putNextEntry(zipEntry)
            file.inputStream().use { input ->
                input.copyTo(zipOut)
            }
            zipOut.closeEntry()
        }
    }
}

suspend fun copyDirectoryContents(
    from: File,
    to: File,
    onProgress: ((Float) -> Unit)? = null
) = withContext(Dispatchers.IO) {
    val normalizedFrom = from.absoluteFile.normalize()
    val normalizedTo = to.absoluteFile.normalize()

    val allFiles = mutableListOf<File>()

    normalizedFrom.walkTopDown().forEach { file ->
        ensureActive()
        val targetPath = File(normalizedTo, file.relativeTo(normalizedFrom).path)
        if (file.isDirectory) {
            targetPath.mkdirs()
        } else {
            allFiles.add(file)
        }
    }

    val fileCount = allFiles.size

    if (fileCount == 0) {
        onProgress?.invoke(1.0f)
        return@withContext
    }

    allFiles.forEachIndexed { index, file ->
        ensureActive()
        val targetFile = File(normalizedTo, file.relativeTo(normalizedFrom).path)
        try {
            targetFile.ensureParentDirectory()
            file.copyTo(targetFile, overwrite = true)
            Logger.info(TAG, "copied: ${file.path} -> ${targetFile.path}")
        } catch (e: IOException) {
            Logger.error(TAG, "Failed to copy: ${file.path} -> ${targetFile.path}", e)
        }
        onProgress?.invoke((index + 1).toFloat() / fileCount)
    }
}

fun collectFiles(
    folder: File,
    summitFile: (File) -> Unit
) {
    if (!folder.exists()) return
    folder.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            collectFiles(file, summitFile)
        } else if (file.isFile) {
            summitFile(file)
        }
    }
}

suspend fun findRedundantFiles(sourceFiles: List<File>, targetFiles: List<File>): List<File> {
    return withContext(Dispatchers.IO) {
        if (targetFiles.isEmpty()) return@withContext sourceFiles
        if (sourceFiles.isEmpty()) return@withContext emptyList()

        val targetPaths = targetFiles.mapTo(
            HashSet(targetFiles.size)
        ) {
            ensureActive()
            it.absolutePath
        }

        sourceFiles.filter { sourceFile ->
            ensureActive()
            sourceFile.absolutePath !in targetPaths
        }
    }
}

fun locateRealRoot(directory: File): File {
    require(directory.exists() && directory.isDirectory) { "The directory does not exist or is not a folder" }

    var currentDir = directory
    var shouldContinue = true

    while (shouldContinue) {
        val files = currentDir.listFiles()

        if (files.isNullOrEmpty()) {
            shouldContinue = false
            continue
        }

        if (files.size > 1) {
            shouldContinue = false
            continue
        }

        val file = files[0]

        if (!file.isDirectory) {
            shouldContinue = false
            continue
        }

        currentDir = file
    }

    return currentDir
}

fun checkZip(file: File): Boolean {
    return runCatching {
        val zipFile = CompressZipFile.Builder()
            .setFile(file)
            .get()
        zipFile.use { zip ->
            val buffer = ByteArray(8 * 1024)
            val entries = zip.entries
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                zip.getInputStream(entry).use { input ->
                    while (input.read(buffer) != -1) {

                    }
                }
            }
        }
        true
    }.getOrDefault(false)
}

fun check7z(file: File): Boolean {
    return runCatching {
        val sevenZ = SevenZFile.Builder()
            .setFile(file)
            .get()
        sevenZ.use { sevenZ ->
            val buffer = ByteArray(8 * 1024)
            var entry = sevenZ.nextEntry
            while (entry != null) {
                while (sevenZ.read(buffer) > 0) {

                }
                entry = sevenZ.nextEntry
            }
        }
        true
    }.getOrDefault(false)
}

fun File.checkExtensionOrThrow(extensions: List<String>) {
    if (extension !in extensions) {
        throw IOException("File extension {$extension} is not supported")
    }
}

fun String.checkExtensionOrThrow(extensions: List<String>) {
    val extension = substringAfterLast(".")
    if (extension !in extensions) {
        throw IOException("File extension {$extension} is not supported")
    }
}
