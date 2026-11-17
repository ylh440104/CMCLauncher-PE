package com.cmc.launcher.filemanager.viewmodel.controllers

import com.cmc.launcher.R
import com.cmc.launcher.filemanager.events.FileManagerEvent
import com.cmc.launcher.filemanager.logic.FileManagerLogic
import com.cmc.launcher.filemanager.logic.FmResult
import com.cmc.launcher.filemanager.logic.ops.ConflictResolution
import com.cmc.launcher.filemanager.logic.ops.PasteMode
import com.cmc.launcher.filemanager.logic.ops.PasteRequest
import com.cmc.launcher.filemanager.logic.ops.PasteSummary
import com.cmc.launcher.filemanager.viewmodel.DialogIntent
import com.cmc.launcher.filemanager.viewmodel.FmSnackbar
import com.cmc.launcher.filemanager.viewmodel.FmStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Path

class PasteController(
    private val logic: FileManagerLogic,
    private val store: FmStateStore,
    private val browse: BrowseController,
    private val coroutineScope: CoroutineScope
) {

    fun requestPaste() {
        val cb = store.clipboard ?: return
        val target = store.history.currentPath
        val mode = if (cb.isCut) PasteMode.MOVE else PasteMode.COPY
        coroutineScope.launch(Dispatchers.IO) {
            val request = runCatching {
                logic.buildPasteRequest(cb.sources, target, mode)
            }.getOrElse {
                store.emitSnackbar(FmSnackbar(it.message ?: store.stringResolver(R.string.fm_error_paste_invalid)))
                browse.refreshCurrentDir()
                return@launch
            }
            when (request) {
                is PasteRequest.Ready -> {
                    executePasteInternal(request.sources, request.targetDir, request.mode,
                        resolutions = request.sources.map { ConflictResolution.SKIP }
                    ) {  }
                }
                is PasteRequest.ResolveRequest -> {

                    val firstConflict = request.conflicts.indexOfFirst { it != null }
                    if (firstConflict < 0) {
                        executePasteInternal(request.sources, request.targetDir, request.mode,
                            resolutions = request.sources.map { ConflictResolution.SKIP }
                        ) { }
                        return@launch
                    }
                    store.updateState {
                        it.copy(
                            dialogIntent = DialogIntent.PasteConflict(
                                request = request,
                                decidedResolutions = emptyList(),
                                currentIndex = firstConflict
                            )
                        )
                    }
                }
            }
        }
    }

    fun resolvePasteConflict(resolution: ConflictResolution) {
        val cur = store.stateValue().dialogIntent as? DialogIntent.PasteConflict ?: return
        val req = cur.request
        val total = req.conflicts.size

        val updated = cur.decidedResolutions.toMutableList()
        while (updated.size < total) updated += ConflictResolution.SKIP
        updated[cur.currentIndex] = resolution

        var next = cur.currentIndex + 1
        while (next < total && req.conflicts[next] == null) next++
        if (next < total) {
            store.updateState {
                it.copy(dialogIntent = DialogIntent.PasteConflict(req, updated, next))
            }
        } else {

            store.dismissDialog()
            coroutineScope.launch(Dispatchers.IO) {

                val tempDir = store.pendingImportTempDir
                if (tempDir != null) {
                    executeImportPaste(req.sources, req.targetDir, req.mode, updated, tempDir)
                } else {
                    executePasteInternal(req.sources, req.targetDir, req.mode, updated) { }
                }
            }
        }
    }

    suspend fun executePasteInternal(
        sources: List<Path>,
        targetDir: Path,
        mode: PasteMode,
        resolutions: List<ConflictResolution>,
        onResult: suspend (PasteSummary?) -> Unit
    ) {
        when (val r = logic.executePaste(sources, targetDir, mode, resolutions)) {
            is FmResult.Ok -> {

                store.setClipboard(null)
                browse.notifyFileChanged(
                    FileManagerEvent(
                        FileManagerEvent.Type.COPY_PASTE,
                        listOf(targetDir.toString())
                    )
                )
                onResult(r.value)
            }
            is FmResult.Failed -> {
                store.emitSnackbar(FmSnackbar(r.error.message ?: store.stringResolver(R.string.fm_error_paste_failed)))
                browse.refreshCurrentDir()
                onResult(null)
            }
            FmResult.Cancelled -> {

                onResult(null)
            }
            FmResult.Rejected -> {
                store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_task_busy)))
                onResult(null)
            }
        }
    }

    suspend fun executeImportPaste(
        sources: List<Path>,
        targetDir: Path,
        mode: PasteMode,
        resolutions: List<ConflictResolution>,
        tempDir: Path
    ) {
        executePasteInternal(sources, targetDir, mode, resolutions) {
            finishImportPaste(targetDir.toString(), tempDir)
        }
    }

    private suspend fun finishImportPaste(targetPath: String, tempDir: Path) {
        logic.tempWorkspace.delete(tempDir)
        store.pendingImportTempDir = null
        browse.notifyFileChanged(
            FileManagerEvent(FileManagerEvent.Type.IMPORT, listOf(targetPath))
        )
    }
}
