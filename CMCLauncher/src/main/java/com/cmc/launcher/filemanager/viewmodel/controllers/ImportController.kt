package com.cmc.launcher.filemanager.viewmodel.controllers

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.cmc.launcher.R
import com.cmc.launcher.filemanager.logic.FileManagerLogic
import com.cmc.launcher.filemanager.logic.ops.ConflictResolution
import com.cmc.launcher.filemanager.logic.ops.FilePermissions
import com.cmc.launcher.filemanager.logic.ops.PasteMode
import com.cmc.launcher.filemanager.logic.ops.PasteRequest
import com.cmc.launcher.filemanager.logic.task.RunResult
import com.cmc.launcher.filemanager.logic.task.TaskKind
import com.cmc.launcher.filemanager.logic.task.TaskManager
import com.cmc.launcher.filemanager.os.FmLog
import com.cmc.launcher.filemanager.viewmodel.DialogIntent
import com.cmc.launcher.filemanager.viewmodel.FmSnackbar
import com.cmc.launcher.filemanager.viewmodel.FmStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

private const val TAG = "FmImport"

class ImportController(
    private val context: Context,
    private val logic: FileManagerLogic,
    private val taskManager: TaskManager,
    private val store: FmStateStore,
    private val paste: PasteController,
    private val browse: BrowseController,
    private val coroutineScope: CoroutineScope
) {

    fun onImportFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importFromUris(uris, multiFile = true)
    }

    fun onImportDir(treeUri: Uri) {
        importFromUris(listOf(treeUri), multiFile = false)
    }

    private fun importFromUris(uris: List<Uri>, multiFile: Boolean) {
        coroutineScope.launch(Dispatchers.IO) {
            doImport(uris, multiFile)
        }
    }

    private suspend fun doImport(uris: List<Uri>, multiFile: Boolean) {

        val tempDir = logic.tempWorkspace.importTempDir()
        try {

            val downloadResult: RunResult<List<Path>> = taskManager.run(TaskKind.IMPORT) {
                downloadUris(uris, tempDir, multiFile) { d, t -> report(completed = d, total = t) }
            }
            val sources = when (downloadResult) {
                is RunResult.Ok -> downloadResult.value
                is RunResult.Failed -> throw downloadResult.error
                RunResult.Cancelled -> {
                    logic.tempWorkspace.delete(tempDir)
                    store.dismissDialog()
                    return
                }
                is RunResult.Rejected -> {
                    store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_task_busy)))
                    logic.tempWorkspace.delete(tempDir)
                    store.dismissDialog()
                    return
                }
            }
            if (sources.isEmpty()) {
                store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_import_empty)))
                logic.tempWorkspace.delete(tempDir)
                store.dismissDialog()
                return
            }

            store.dismissDialog()
            val target = store.history.currentPath
            when (val req = logic.buildPasteRequest(sources, target, PasteMode.COPY)) {
                is PasteRequest.Ready -> {
                    paste.executeImportPaste(req.sources, req.targetDir, req.mode,
                        req.sources.map { ConflictResolution.SKIP }, tempDir)
                }
                is PasteRequest.ResolveRequest -> {

                    val firstConflict = req.conflicts.indexOfFirst { it != null }
                    if (firstConflict < 0) {
                        paste.executeImportPaste(req.sources, req.targetDir, req.mode,
                            req.sources.map { ConflictResolution.SKIP }, tempDir)
                        return
                    }
                    store.pendingImportTempDir = tempDir
                    store.updateState {
                        it.copy(
                            dialogIntent = DialogIntent.PasteConflict(
                                request = req,
                                decidedResolutions = emptyList(),
                                currentIndex = firstConflict
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            FmLog.error(TAG, "Import failed: ${e.message}", e)
            logic.tempWorkspace.delete(tempDir)
            val detail = e.message?.takeIf { it.isNotBlank() }
            store.emitSnackbar(FmSnackbar(detail ?: store.stringResolver(R.string.import_modpack_failed_title)))
            store.dismissDialog()
            browse.refreshCurrentDir()
        }
    }

    private suspend fun downloadUris(
        uris: List<Uri>,
        tempDir: Path,
        multiFile: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Path> = withContext(Dispatchers.IO) {
        if (multiFile) {
            val total = uris.size
            val result = mutableListOf<Path>()
            var done = 0
            for (uri in uris) {
                val name = displayNameOf(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: continue
                val dest = tempDir.resolve(name)
                if (downloadUri(uri, dest)) result.add(dest)
                done++
                onProgress(done, total)
            }
            return@withContext result
        }
        val treeUri = uris.firstOrNull() ?: return@withContext emptyList()

        val dirName = treeDisplayNameOf(treeUri) ?: "imported"
        val rootDest = tempDir.resolve(dirName)
        Files.createDirectories(rootDest)

        val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
        if (treeDoc == null) {
            FmLog.warn(TAG, "Import dir: DocumentFile.fromTreeUri returned null for $treeUri")
            return@withContext emptyList()
        }

        val total = countFiles(treeDoc)
        FmLog.info(TAG, "Import dir: '$dirName' total files=$total")
        if (total == 0) {
            FmLog.warn(TAG, "Import dir: no files counted under $treeUri")
        }
        val counter = Counter()
        importDocTree(treeDoc, rootDest, counter, total, onProgress)
        FmLog.info(TAG, "Import dir: downloaded ${counter.value}/$total files to $rootDest")

        listOf(rootDest)
    }

    private fun countFiles(dir: DocumentFile): Int {
        val children = runCatching { dir.listFiles() }.getOrElse { e ->
            FmLog.warn(TAG, "Import dir: countFiles listFiles() failed for ${dir.uri}", e)
            null
        } ?: return 0
        var count = 0
        for (child in children) {
            count += if (child.isDirectory) countFiles(child) else 1
        }
        return count
    }

    private fun importDocTree(
        dir: DocumentFile,
        destDir: Path,
        counter: Counter,
        total: Int,
        onProgress: (Int, Int) -> Unit
    ) {
        val children = runCatching { dir.listFiles() }.getOrElse { e ->
            FmLog.warn(TAG, "Import dir: listFiles() failed for ${dir.uri}", e)
            null
        }
        if (children == null) return
        for (child in children) {
            runCatching {
                val name = child.name ?: return@runCatching
                val dest = destDir.resolve(name)
                if (child.isDirectory) {
                    Files.createDirectories(dest)
                    importDocTree(child, dest, counter, total, onProgress)
                } else {
                    if (downloadUri(child.uri, dest)) {
                        counter.increment()
                        onProgress(counter.value, total)
                    }
                }
            }.onFailure { e ->
                FmLog.warn(TAG, "Import dir: failed on child ${child.uri}", e)
            }
        }
    }

    private fun treeDisplayNameOf(treeUri: Uri): String? {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            displayNameOf(documentUri)
        }.getOrElse { e ->
            FmLog.warn(TAG, "Import dir: failed to resolve tree display name for $treeUri", e)
            null
        }
    }

    private fun downloadUri(uri: Uri, dest: Path): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                Files.createDirectories(dest.parent)
                Files.newOutputStream(dest).use { output -> input.copyTo(output) }
                FilePermissions.apply(dest)
            } ?: run {
                FmLog.warn(TAG, "Import: openInputStream returned null for $uri")
                return false
            }
            true
        }.getOrElse { e ->
            FmLog.warn(TAG, "Import: failed to download URI $uri -> $dest", e)
            false
        }
    }

    private fun displayNameOf(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private class Counter {
        var value = 0
        fun increment(): Int = ++value
    }
}
