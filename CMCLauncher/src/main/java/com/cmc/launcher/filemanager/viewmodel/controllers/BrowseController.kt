package com.cmc.launcher.filemanager.viewmodel.controllers

import com.cmc.launcher.R
import com.cmc.launcher.filemanager.config.FmConfig
import com.cmc.launcher.filemanager.events.FileManagerEvent
import com.cmc.launcher.filemanager.events.FileManagerEventBus
import com.cmc.launcher.filemanager.logic.AccessScope
import com.cmc.launcher.filemanager.logic.FileManagerLogic
import com.cmc.launcher.filemanager.logic.FmResult
import com.cmc.launcher.filemanager.logic.entry.FmEntry
import com.cmc.launcher.filemanager.logic.entry.FmListResult
import com.cmc.launcher.filemanager.os.FmLog
import com.cmc.launcher.filemanager.viewmodel.FmSnackbar
import com.cmc.launcher.filemanager.viewmodel.FmStateStore
import com.cmc.launcher.filemanager.viewmodel.RawList
import com.cmc.launcher.filemanager.viewmodel.SortConfig
import com.cmc.launcher.filemanager.viewmodel.applyVisibility
import com.cmc.launcher.filemanager.viewmodel.entryPathKey
import com.cmc.launcher.filemanager.viewmodel.persist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "FmBrowse"

private const val BUSY_RETRY_DELAY_MS = 150L

class BrowseController(
    private val logic: FileManagerLogic,
    private val scope: AccessScope,
    private val store: FmStateStore,
    private val coroutineScope: CoroutineScope
) {
    private val refreshLock = Any()

    private var refreshJob: Job? = null

    private var pendingTarget: Path? = null

    fun refreshDir(target: Path? = null) {
        val t = target ?: store.history.currentPath
        synchronized(refreshLock) {
            pendingTarget = t
            store.updateState {
                it.copy(currentDir = t, refreshing = true)
            }
            if (refreshJob?.isActive == true) return
            refreshJob = coroutineScope.launch(Dispatchers.IO) { refreshPipeline() }
        }
    }

    fun refreshCurrentDir() = refreshDir(store.history.currentPath)

    private suspend fun refreshPipeline() {
        while (true) {
            val target = takePendingTarget()
            if (target == null) {
                val shouldExit = synchronized(refreshLock) {
                    if (pendingTarget == null) {
                        refreshJob = null
                        true
                    } else {
                        false
                    }
                }
                if (shouldExit) {
                    store.updateState {
                        it.copy(refreshing = false)
                    }
                    return
                }
                continue
            }
            try {
                refreshOnce(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                FmLog.warn(TAG, "refresh pipeline error for $target", e)
                store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_error_browse_failed)))
            }
        }
    }

    private fun takePendingTarget(): Path? = synchronized(refreshLock) {
        val t = pendingTarget
        pendingTarget = null
        t
    }

    private suspend fun refreshOnce(target: Path) {
        FmLog.info(TAG, "refreshList target=$target")

        val result = browseAwaitingMutex(target) ?: return

        if (target != store.history.currentPath) {
            FmLog.info(TAG, "browse result stale, drop: $target")
            return
        }

        when (result) {
            is FmResult.Ok -> {

                if (result.value.currentDir == store.history.currentPath) {
                    FmLog.info(TAG, "browse ok: entries=${result.value.entries.size}, current=${result.value.currentDir}")
                    updateList(result.value)
                } else {
                    FmLog.info(TAG, "browse result stale at apply, drop: ${result.value.currentDir}")
                }
            }
            is FmResult.Failed -> {
                FmLog.warn(TAG, "browse failed", result.error)
                store.emitSnackbar(FmSnackbar(result.error.message ?: store.stringResolver(R.string.fm_error_browse_failed)))

                val lastGood = store.stateValue().rawList?.currentDir
                if (lastGood != null && target != lastGood) {
                    FmLog.info(TAG, "browse failed for navigation target, revert to: $lastGood")
                    store.updateState { it.copy(currentDir = lastGood) }
                }
            }
            FmResult.Cancelled -> {
                FmLog.warn(TAG, "browse cancelled")
            }
            FmResult.Rejected -> {
                FmLog.warn(TAG, "browse rejected (busy)")
                store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_task_busy)))
            }
        }
    }

    private suspend fun browseAwaitingMutex(target: Path): FmResult<FmListResult>? {
        var result = logic.browse(target)
        while (result is FmResult.Rejected && currentCoroutineContext().isActive) {
            if (target != store.history.currentPath) {
                FmLog.info(TAG, "browse abandoned (target changed): $target")
                return null
            }
            delay(BUSY_RETRY_DELAY_MS.milliseconds)
            result = logic.browse(target)
        }
        return result
    }

    fun navigateTo(path: Path) {
        val safe = logic.validateTarget(path) ?: run {
            store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_error_invalid_target)))
            return
        }
        store.clearSelectionAndExitMulti()
        store.history.navigate(safe)
        syncNavState()
        refreshDir(safe)
    }

    fun enterDirectory(entry: FmEntry) {
        if (!entry.isDirectory) return
        navigateTo(entry.path)
    }

    fun back(): Boolean {
        val p = store.history.back() ?: return false
        store.clearSelectionAndExitMulti()
        syncNavState()
        refreshDir(p)
        return true
    }

    fun forward(): Boolean {
        val p = store.history.forward() ?: return false
        store.clearSelectionAndExitMulti()
        syncNavState()
        refreshDir(p)
        return true
    }

    fun goParent(): Boolean {
        val parent = store.history.currentPath.parent ?: return false
        if (!scope.isUnder(parent)) return false
        navigateTo(parent)
        return true
    }

    fun jumpTo(targetInput: String) {
        val candidate = Paths.get(targetInput).normalize().toAbsolutePath()
        val safe = logic.validateTarget(candidate)
        if (safe == null) {
            store.emitError(store.stringResolver(R.string.fm_error_out_of_scope))
            return
        }
        navigateTo(safe)
    }

    fun submitJump(targetInput: String): Boolean {
        val candidate = try {
            Paths.get(targetInput).normalize().toAbsolutePath()
        } catch (_: Exception) {
            store.emitError(store.stringResolver(R.string.fm_jump_invalid))
            return false
        }
        if (logic.validateTarget(candidate) == null) {
            store.emitError(store.stringResolver(R.string.fm_jump_invalid))
            return false
        }
        jumpTo(candidate.toString())
        return true
    }

    fun navigateToSearchHit(hitPath: Path) {
        val target = hitPath.parent ?: store.history.currentPath
        val safe = logic.validateTarget(target) ?: return
        store.clearSelectionAndExitMulti()
        store.history.navigate(safe)
        syncNavState()
        store.updateState { it.copy(locateHighlightPath = hitPath) }
        store.dismissDialog()
        refreshDir(safe)
    }

    fun notifyFileChanged(event: FileManagerEvent) {
        FileManagerEventBus.dispatch(event)
        refreshCurrentDir()
    }

    fun pruneHistory(deleted: Path) {
        store.history.pruneDeleted(deleted)
        syncNavState()
    }

    fun setSortConfig(config: SortConfig) {
        config.persist()
        val newVisible = applyVisibility(store.stateValue().rawList?.entries ?: emptyList(), config, store.stateValue().showHidden)
        store.updateState { it.copy(sortConfig = config, visibleEntries = newVisible) }
        recountVisible(newVisible)
        reconcileSelectionWith(newVisible)
    }

    fun toggleHidden() {
        val show = !store.stateValue().showHidden
        FmConfig.setShowHidden(show)
        val newVisible = applyVisibility(store.stateValue().rawList?.entries ?: emptyList(), store.stateValue().sortConfig, show)
        store.updateState { it.copy(showHidden = show, visibleEntries = newVisible) }
        recountVisible(newVisible)
        reconcileSelectionWith(newVisible)
    }

    private fun syncNavState() {
        store.updateState {
            it.copy(
                canNavigateBack = store.history.canBack,
                canNavigateForward = store.history.canForward
            )
        }
    }

    private fun updateList(result: FmListResult) {
        val raw = RawList.of(result)
        val visible = applyVisibility(raw.entries, store.stateValue().sortConfig, store.stateValue().showHidden)

        val present = visible.mapTo(mutableSetOf()) { entryPathKey(it) }
        val newSelection = store.selection.intersect(present)
        store.setSelection(newSelection, newSelection.isNotEmpty())
        store.updateState {
            it.copy(
                rawList = raw,
                visibleEntries = visible
            )
        }
        recountVisible(visible)
    }

    private fun recountVisible(visible: List<FmEntry>) {
        val folders = visible.count { it.isDirectory }
        val files = visible.count { it.isFile }
        store.updateState {
            it.copy(folderCount = folders, fileCount = files)
        }
    }

    private fun reconcileSelectionWith(visible: List<FmEntry>) {
        val present = visible.mapTo(mutableSetOf()) { entryPathKey(it) }
        if (store.selection.any { it !in present }) {
            val newSelection = store.selection.intersect(present)
            store.setSelection(newSelection, newSelection.isNotEmpty())
        }
    }
}
