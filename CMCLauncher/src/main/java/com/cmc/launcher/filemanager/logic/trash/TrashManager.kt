package com.cmc.launcher.filemanager.logic.trash

import com.cmc.launcher.filemanager.logic.AccessScope
import com.cmc.launcher.filemanager.logic.ops.ConflictResolution
import com.cmc.launcher.filemanager.logic.ops.FileOps
import com.cmc.launcher.filemanager.os.FmLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

private const val TAG = "FmTrash"
private const val CONTENT_DIR = "content"
private const val META_FILE = "meta.json"

class TrashManager(
    val trashRoot: Path,
    private val scope: AccessScope,
    private val fileOps: FileOps = FileOps(scope)
) {
    init {
        Files.createDirectories(trashRoot)
    }

    suspend fun moveIn(
        original: Path,
        onProgress: (name: String?) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val checkCancel = { coroutineContext.ensureActive() }

        val source = scope.guard(original)
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Source does not exist: $source")
        }
        val name = source.fileName?.toString() ?: throw IOException("Source has no filename")
        val isFolder = Files.readAttributes(
            source,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS
        ).isDirectory

        val uuid = generateSequence { UUID.randomUUID().toString() }
            .onEach { checkCancel() }
            .first { !Files.exists(trashRoot.resolve(it), LinkOption.NOFOLLOW_LINKS) }
        val entryDir = trashRoot.resolve(uuid)
        val contentDir = entryDir.resolve(CONTENT_DIR)
        Files.createDirectories(contentDir)
        onProgress(name)

        moveAcrossFs(
            source = source,
            target = contentDir.resolve(name),
            checkCancel = checkCancel
        )

        writeMeta(entryDir, TrashMeta(
            originalPath = source.normalize().toAbsolutePath().toString(),
            deletedAt = System.currentTimeMillis(),
            name = name,
            isFolder = isFolder
        ))
        uuid
    }

    suspend fun list(): List<TrashItem> = withContext(Dispatchers.IO) {
        Files.newDirectoryStream(trashRoot).use { stream ->
            stream.mapNotNull { entryDir ->
                if (Files.isDirectory(entryDir, LinkOption.NOFOLLOW_LINKS)) readItem(entryDir) else null
            }
        }
    }

    suspend fun restore(
        item: TrashItem,
        resolution: ConflictResolution,
        onProgress: (name: String?) -> Unit
    ): RestoreResult = withContext(Dispatchers.IO) {
        val checkCancel = { coroutineContext.ensureActive() }
        if (item.meta.corrupted) {
            return@withContext RestoreResult.Failed(item, "Corrupted trash entry, cannot restore")
        }
        val original = runCatching {
            scope.guard(Paths.get(item.meta.originalPath)).let { it.parent ?: it }
        }.getOrNull() ?: return@withContext RestoreResult.Failed(item, "Original path out of scope")
        if (!Files.isDirectory(original)) Files.createDirectories(original)

        val name = item.meta.name
        onProgress(name)
        val initialTarget = original.resolve(name)
        val target = when {
            !Files.exists(initialTarget, LinkOption.NOFOLLOW_LINKS) -> initialTarget
            resolution == ConflictResolution.SKIP -> return@withContext RestoreResult.Skipped(item, initialTarget)
            resolution == ConflictResolution.OVERWRITE -> initialTarget.also { fileOps.deleteRecursive(it, checkCancel) }
            else -> original.resolve(fileOps.nextKeepBothName(original, name))
        }
        try {
            moveAcrossFs(item.contentDir.resolve(name), target, checkCancel)

            fileOps.deleteRecursive(item.contentDir.parent ?: trashRoot, checkCancel)
            RestoreResult.Ok(item, target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FmLog.warn(TAG, "Restore failed: ${item.uuid}", e)
            RestoreResult.Failed(item, e.message ?: "Restore failed")
        }
    }

    suspend fun purge(
        item: TrashItem,
        onProgress: (name: String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val checkCancel = { coroutineContext.ensureActive() }
        onProgress(item.meta.name)
        val entryDir = item.contentDir.parent ?: return@withContext

        fileOps.deleteRecursive(item.contentDir, checkCancel)
        fileOps.deleteRecursive(entryDir, checkCancel)
    }

    suspend fun clear(
        onProgress: (current: Int, total: Int, name: String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val checkCancel = { coroutineContext.ensureActive() }
        val items = Files.newDirectoryStream(trashRoot).use { it.toList() }
        items.forEachIndexed { i, entry ->
            checkCancel()
            onProgress(i, items.size, entry.fileName?.toString())
            fileOps.deleteRecursive(entry, checkCancel)
        }
    }

    private fun readItem(entryDir: Path): TrashItem? {
        val uuid = entryDir.fileName?.toString() ?: return null

        val contentDir = entryDir.resolve(CONTENT_DIR)
        val meta = readMeta(entryDir)
        if (meta != null && Files.isDirectory(contentDir, LinkOption.NOFOLLOW_LINKS) && !isDirEmpty(contentDir)) {
            return TrashItem(uuid, meta, contentDir, computeSize(contentDir))
        }

        val fallback = meta ?: TrashMeta(
            originalPath = entryDir.toString(),
            deletedAt = runCatching { Files.getLastModifiedTime(entryDir).toMillis() }.getOrDefault(0L),
            name = pickFirstChildName(contentDir) ?: uuid,
            isFolder = false
        )
        return TrashItem(uuid, fallback.copy(corrupted = true), contentDir, 0L)
    }

    private fun pickFirstChildName(contentDir: Path): String? = runCatching {
        Files.newDirectoryStream(contentDir).use { stream ->
            stream.firstOrNull()?.fileName?.toString()
        }
    }.getOrNull()

    private fun isDirEmpty(dir: Path): Boolean = runCatching {
        Files.newDirectoryStream(dir).use { stream -> !stream.iterator().hasNext() }
    }.getOrDefault(true)

    private fun computeSize(contentDir: Path): Long {
        var total = 0L
        Files.walkFileTree(contentDir, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes) = FileVisitResult.CONTINUE.also {
                if (attrs.isRegularFile) total += attrs.size()
            }
        })
        return total
    }

    private fun writeMeta(entryDir: Path, meta: TrashMeta) {
        Files.write(entryDir.resolve(META_FILE), meta.toJson().toString().toByteArray(Charsets.UTF_8))
    }

    private fun readMeta(entryDir: Path): TrashMeta? {
        val file = entryDir.resolve(META_FILE)
        if (!Files.exists(file)) return null
        return TrashMeta.fromJson(Files.readAllBytes(file).toString(Charsets.UTF_8))
    }

    private suspend fun moveAcrossFs(
        source: Path,
        target: Path,
        checkCancel: () -> Unit
    ) = withContext(Dispatchers.IO) {

        if (
            runCatching {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
            }.isSuccess
        ) return@withContext

        Files.createDirectories(target.parent ?: trashRoot)
        fileOps.copyTree(source, target, checkCancel, applyPermissions = false)
        fileOps.deleteRecursive(source, checkCancel)
    }
}

sealed interface RestoreResult {
    data class Ok(val item: TrashItem, val target: Path) : RestoreResult
    data class Skipped(val item: TrashItem, val target: Path) : RestoreResult
    data class Failed(val item: TrashItem, val reason: String) : RestoreResult
}
