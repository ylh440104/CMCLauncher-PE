package com.cmc.launcher.filemanager.viewmodel.controllers

import android.content.Context
import com.cmc.launcher.R
import com.cmc.launcher.filemanager.events.FileManagerEvent
import com.cmc.launcher.filemanager.logic.FileManagerLogic
import com.cmc.launcher.filemanager.logic.FilenameValidator
import com.cmc.launcher.filemanager.logic.FmResult
import com.cmc.launcher.filemanager.logic.entry.FmEntry
import com.cmc.launcher.filemanager.os.FmLog
import com.cmc.launcher.filemanager.viewmodel.FmSnackbar
import com.cmc.launcher.filemanager.viewmodel.FmStateStore
import com.cmc.launcher.filemanager.viewmodel.entryPathKey
import com.cmc.launcher.utils.file.shareFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.LinkOption

private const val TAG = "FmDelete"

class EntryController(
    private val context: Context,
    private val logic: FileManagerLogic,
    private val store: FmStateStore,
    private val browse: BrowseController,
    private val coroutineScope: CoroutineScope
) {
    fun showShare(entry: FmEntry) {
        store.dismissDialog()

        if (!entry.isFile) return
        coroutineScope.launch(Dispatchers.IO) {
            val file = entry.path.toFile()
            if (!file.exists()) {
                store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_error_share_failed)))
                browse.refreshCurrentDir()
                return@launch
            }
            runCatching {
                shareFile(context, file)
            }.onFailure {
                FmLog.warn(TAG, "Share failed: ${entry.path}", it)
                store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_error_share_failed)))
                browse.refreshCurrentDir()
            }
        }
    }

    fun submitCreate(name: String, isFolder: Boolean, onDone: (Boolean) -> Unit) {
        if (name.isBlank()) {
            onDone(false)
            return
        }

        val err = FilenameValidator.verify(name)
        if (err != null) {
            store.emitError(store.filenameErrorText(err))
            return
        }

        val conflict = store.stateValue().visibleEntries.any {
            it.name.equals(name, ignoreCase = false)
        }
        if (conflict) {
            store.emitError(store.stringResolver(R.string.fm_name_conflict))
            return
        }
        if (isFolder) {
            createFolder(name, onDone)
        } else {
            createFile(name, onDone)
        }
        store.dismissDialog()
    }

    fun submitRename(entry: FmEntry, newName: String, onSuccess: () -> Unit) {
        rename(entry, newName) {
            store.dismissDialog()
            onSuccess()
        }
    }

    fun validateRename(entry: FmEntry, newName: String): String? {
        if (newName.isBlank()) return store.stringResolver(R.string.generic_cannot_empty)
        val err = FilenameValidator.verify(newName)
        if (err != null) return store.filenameErrorText(err)

        val sibling = store.history.currentPath.resolve(newName).normalize().toAbsolutePath()
        if (sibling != entry.path && Files.exists(sibling, LinkOption.NOFOLLOW_LINKS)) {
            return store.stringResolver(R.string.fm_name_conflict)
        }
        return null
    }

    fun rename(entry: FmEntry, newName: String, onSuccess: () -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val newPath = logic.rename(entry.path, newName)
                browse.notifyFileChanged(
                    FileManagerEvent(
                        FileManagerEvent.Type.RENAME,
                        listOfNotNull(entry.path.parent?.toString())
                    )
                )
                store.updateState {
                    it.copy(locateHighlightPath = newPath)
                }
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                FmLog.warn(TAG, "rename failed", e)
                browse.refreshCurrentDir()
                withContext(Dispatchers.Main) {
                    store.emitError(store.operationErrorText(e, R.string.fm_error_rename_failed))
                }
            }
        }
    }

    fun createFolder(name: String, onDone: (Boolean) -> Unit) {
        createEntry(name, isFolder = true, onDone)
    }

    fun createFile(name: String, onDone: (Boolean) -> Unit) {
        createEntry(name, isFolder = false, onDone)
    }

    private fun createEntry(name: String, isFolder: Boolean, onDone: (Boolean) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val target = store.history.currentPath
            try {
                val created = if (isFolder) {
                    logic.createFolder(target, name)
                } else {
                    logic.createFile(target, name)
                }
                browse.notifyFileChanged(
                    FileManagerEvent(
                        FileManagerEvent.Type.CREATE,
                        listOf(target.toString())
                    )
                )
                store.updateState {
                    it.copy(locateHighlightPath = created)
                }
                withContext(Dispatchers.Main) {
                    onDone(true)
                }
            } catch (e: Exception) {
                FmLog.warn(TAG, "createEntry failed", e)
                browse.refreshCurrentDir()
                withContext(Dispatchers.Main) {
                    store.emitError(store.operationErrorText(e, R.string.fm_error_create_failed))
                    onDone(false)
                }
            }
        }
    }

    fun deleteSelected(toTrash: Boolean) {
        val entries = store.selectedEntries()
        val targets = entries.map { it.path }
        if (targets.isEmpty()) return

        store.stagedSingleDeleteKey = null
        val dirTargets = entries.filter { it.isDirectory }.map { it.path }.toSet()
        coroutineScope.launch(Dispatchers.IO) {
            when (val r = logic.delete(targets, toTrash)) {
                is FmResult.Ok -> {
                    r.value.results
                        .filter { it.success && it.path in dirTargets }
                        .forEach { browse.pruneHistory(it.path) }
                    browse.notifyFileChanged(
                        FileManagerEvent(
                            FileManagerEvent.Type.DELETE,
                            targets.mapNotNull { it.parent?.toString() }.distinct()
                        )
                    )
                }
                is FmResult.Failed -> {
                    store.emitSnackbar(FmSnackbar(r.error.message ?: store.stringResolver(R.string.fm_error_delete_failed)))
                    browse.refreshCurrentDir()
                }
                FmResult.Rejected -> store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_task_busy)))
                FmResult.Cancelled -> {}
            }
        }
    }

    fun stageSingleDelete(entry: FmEntry) {
        val key = entryPathKey(entry)
        store.stagedSingleDeleteKey = key
        store.selection += key
    }

    fun cancelStagedDelete() {
        store.stagedSingleDeleteKey?.let { store.selection -= it }
        store.stagedSingleDeleteKey = null
    }
}
