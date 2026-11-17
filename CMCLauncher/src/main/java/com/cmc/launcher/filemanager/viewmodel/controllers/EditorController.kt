package com.cmc.launcher.filemanager.viewmodel.controllers

import com.cmc.launcher.R
import com.cmc.launcher.filemanager.logic.editor.MAX_EDIT_SIZE
import com.cmc.launcher.filemanager.os.FmLog
import com.cmc.launcher.filemanager.viewmodel.EditorUiState
import com.cmc.launcher.filemanager.viewmodel.FmSnackbar
import com.cmc.launcher.filemanager.viewmodel.FmStateStore
import com.cmc.launcher.ui.code_editor.EditorState
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class EditorController(
    private val store: FmStateStore,
    private val browse: BrowseController,
    private val coroutineScope: CoroutineScope
) {

    private var charset: Charset = StandardCharsets.UTF_8

    private var saveJob: Job? = null

    fun open(path: Path) {
        store.updateEditorUi { EditorUiState(path = path) }
        coroutineScope.launch(Dispatchers.IO) {
            val result = runCatching { loadFile(path) }
            result.onSuccess { content ->
                store.updateEditorUi {
                    it.copy(
                        state = EditorState.Success(content),
                        writable = Files.isWritable(path),
                        error = null
                    )
                }
            }.onFailure { e ->
                if (e is CancellationException) return@launch
                FmLog.error(TAG, "Open editor file failed: ${path}", e)
                store.updateEditorUi {
                    it.copy(error = store.fileOpErrorText(e, R.string.fm_editor_open_failed))
                }
            }
        }
    }

    fun onTextChanged() {
        store.updateEditorUi { it.copy(dirty = true) }
    }

    fun save(onDone: (Boolean) -> Unit = {}) {
        if (store.editorUiValue().saving) return
        val state = store.editorUiValue()
        val path = state.path ?: return
        val content = (state.state as? EditorState.Success)?.content ?: return
        store.updateEditorUi { it.copy(saving = true) }
        saveJob = coroutineScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        Files.write(path, content.toString().toByteArray(charset))
                    }.isSuccess
                }

                ensureActive()
                if (!success) {
                    FmLog.error(TAG, "Save editor file failed: $path")
                }
                store.updateEditorUi { it.copy(dirty = false) }
                if (success) {
                    store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.generic_saved), long = false))

                    browse.refreshDir()
                } else {
                    store.emitSnackbar(FmSnackbar(store.stringResolver(R.string.fm_editor_save_failed)))
                }
                onDone(success)
            } catch (e: CancellationException) {

                onDone(false)
                throw e
            } finally {
                store.updateEditorUi { it.copy(saving = false) }
            }
        }
    }

    fun cancelSave() {
        saveJob?.cancel()
        saveJob = null
        store.updateEditorUi { it.copy(saving = false) }
    }

    fun requestExitConfirm() {
        store.updateEditorUi { it.copy(exitConfirm = true) }
    }

    fun cancelExitConfirm() {
        store.updateEditorUi { it.copy(exitConfirm = false) }
    }

    fun hasDirty(): Boolean = store.editorUiValue().dirty

    private fun loadFile(path: Path): Content {
        val size = Files.size(path)
        if (size > MAX_EDIT_SIZE || !hasEnoughMemoryFor(size)) {
            throw EditorFileTooLargeException(
                store.stringResolver(
                    if (size > MAX_EDIT_SIZE) R.string.fm_editor_file_too_large
                    else R.string.fm_editor_out_of_memory
                )
            )
        }
        val bytes = Files.readAllBytes(path)
        val text = decode(bytes)
        return Content(text)
    }

    private fun hasEnoughMemoryFor(fileSize: Long): Boolean {
        val runtime = Runtime.getRuntime()
        val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val estimated = fileSize * MEMORY_MULTIPLIER + MEMORY_OVERHEAD
        return estimated <= available * MEMORY_SAFE_RATIO
    }

    private fun decode(bytes: ByteArray): String {
        val bom = detectBom(bytes)
        val contentBytes = bytes.copyOfRange(bom.length, bytes.size)
        if (bom.charset != null) {
            charset = bom.charset
            return String(contentBytes, bom.charset)
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            charset = StandardCharsets.UTF_8
            decoder.decode(ByteBuffer.wrap(contentBytes)).toString()
        } catch (e: CharacterCodingException) {
            charset = Charset.forName("GBK")
            String(contentBytes, charset)
        }
    }

    private fun detectBom(bytes: ByteArray): BomResult = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            BomResult(3, StandardCharsets.UTF_8)

        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            BomResult(2, StandardCharsets.UTF_16LE)

        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            BomResult(2, StandardCharsets.UTF_16BE)

        else -> BomResult(0, null)
    }

    private data class BomResult(
        val length: Int,
        val charset: Charset?
    )

    private class EditorFileTooLargeException(message: String) : Exception(message)

    companion object {
        private const val TAG = "EditorController"

        private const val MEMORY_MULTIPLIER: Long = 6

        private const val MEMORY_OVERHEAD: Long = 16L * 1024 * 1024

        private const val MEMORY_SAFE_RATIO = 0.75
    }
}
