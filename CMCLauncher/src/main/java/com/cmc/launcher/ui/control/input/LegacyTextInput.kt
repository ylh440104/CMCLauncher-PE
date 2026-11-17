package com.cmc.launcher.ui.control.input

import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.unit.IntRect
import androidx.core.content.getSystemService
import com.cmc.launcher.game.input.CharacterSenderStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.text.forEach

@Deprecated("因兼容性问题，现已改用输入栏UI代理输入法输入，此处代码已不再使用，仅作参考")
@Composable
fun Modifier.textInputHandler(
    mode: TextInputMode,
    sender: CharacterSenderStrategy,
    onCloseInputMethod: () -> Unit = {}
): Modifier {
    OnKeyboardClosed {
        if (mode == TextInputMode.ENABLE) {
            onCloseInputMethod()
        }
    }
    val textMode by rememberUpdatedState(mode)
    val onCloseInputMethod1 by rememberUpdatedState(onCloseInputMethod)
    return this then TextInputModifier(sender, textMode, onCloseInputMethod1)
}

private data class TextInputModifier(
    private val sender: CharacterSenderStrategy,
    private val textMode: TextInputMode,
    private val onCloseInputMethod: () -> Unit = {}
) : ModifierNodeElement<TextInputNode>() {
    override fun create() = TextInputNode(sender, textMode, onCloseInputMethod)
    override fun update(node: TextInputNode) {
        node.update(sender, textMode, onCloseInputMethod)
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "simulatorTextInputCore"
    }
}

private class TextInputNode(
    private var sender: CharacterSenderStrategy,
    private var textInputMode: TextInputMode,
    private var onCloseInputMethod: () -> Unit
) : Modifier.Node(), PlatformTextInputModifierNode {
    private var session: Job? = null
    private val fakeCursorRect = IntRect(100, 500, 100, 550)

    override fun onAttach() {
        if (textInputMode == TextInputMode.ENABLE) {
            session = coroutineScope.launch {
                try {
                    establishTextInputSession {
                        val inputMethodManager = view.context.getSystemService<InputMethodManager>()
                            ?: error("InputMethodManager not supported")

                        val inputMethodIdentifier = Settings.Secure.getString(
                            view.context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
                        )

                        val connection = InputConnectionImpl(view, inputMethodManager, inputMethodIdentifier)

                        inputMethodManager.updateCursorAnchorInfo(
                            view,
                            CursorAnchorInfo.Builder().apply {
                                setSelectionRange(0, 0)
                                setInsertionMarkerLocation(
                                    fakeCursorRect.left.toFloat(),
                                    fakeCursorRect.top.toFloat(),
                                    fakeCursorRect.right.toFloat(),
                                    fakeCursorRect.bottom.toFloat(),
                                    CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION
                                )
                                setMatrix(view.matrix)
                            }.build()
                        )

                        startInputMethod { info ->
                            info.inputType = InputType.TYPE_CLASS_TEXT or
                                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                                    InputType.TYPE_TEXT_VARIATION_NORMAL
                            info.imeOptions = EditorInfo.IME_ACTION_DONE or

                                    EditorInfo.IME_FLAG_NO_FULLSCREEN or

                                    EditorInfo.IME_FLAG_NO_EXTRACT_UI

                            info.packageName = view.context.packageName
                            info.fieldId = view.id

                            info.initialSelStart = 0
                            info.initialSelEnd = 0

                            connection
                        }
                    }
                } catch (_: CancellationException) {
                }
            }
        }
    }

    private fun stopInput() {
        session?.cancel()
        session = null
    }

    fun update(
        sender: CharacterSenderStrategy,
        textInputMode: TextInputMode,
        onCloseInputMethod: () -> Unit
    ) {
        this.sender = sender
        this.onCloseInputMethod = onCloseInputMethod
        if (this.textInputMode != textInputMode) {
            this.textInputMode = textInputMode
            stopInput()
            if (textInputMode == TextInputMode.ENABLE) {
                onAttach()
            }
        } else {
            this.textInputMode = textInputMode
        }
    }

    private inner class InputConnectionImpl(
        private val view: View,
        private val imm: InputMethodManager,
        private val inputMethodIdentifier: String
    ) : InputConnection {
        private val textBuffer = StringBuilder()
        private var cursorPosition = 0
        private var composingStart = -1
        private var composingEnd = -1

        private var inBatchEdit = false
        private var pendingBackspaceCount = 0
        private var pendingTextToSend = StringBuilder()

        private val isMicrosoftSwiftKey: Boolean
            get() = inputMethodIdentifier.contains("com.microsoft.swiftkey") ||
                    inputMethodIdentifier.contains("com.touchtype.swiftkey") ||
                    inputMethodIdentifier.contains("swiftkey")

        private fun sendText(text: String) {
            text.forEach { char -> sender.sendChar(char) }
            if (isMicrosoftSwiftKey) {

                cursorPosition = 0
                textBuffer.clear()
            }
        }

        private fun flushPendingText() {
            repeat(pendingBackspaceCount) { sender.sendBackspace() }
            pendingBackspaceCount = 0

            if (pendingTextToSend.isNotEmpty()) {
                sendText(pendingTextToSend.toString())
                pendingTextToSend.clear()
            }
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            var composingLength = 0

            if (composingStart in 0..<composingEnd) {
                val safeStart = composingStart.coerceIn(0, textBuffer.length)
                val safeEnd = composingEnd.coerceIn(0, textBuffer.length)
                composingLength = safeEnd - safeStart
                if (safeStart < safeEnd) {
                    textBuffer.delete(safeStart, safeEnd)
                    cursorPosition = safeStart
                }
                composingStart = -1
                composingEnd = -1
            }

            textBuffer.insert(cursorPosition, text)
            cursorPosition += text.length

            val newText = text.toString()

            if (isMicrosoftSwiftKey) {
                sendText(newText)
            } else {
                if (inBatchEdit) {
                    if (composingLength > 0) {
                        pendingBackspaceCount += composingLength
                    }
                    pendingTextToSend.append(newText)
                } else {
                    if (composingLength > 0) {
                        repeat(composingLength) { sender.sendBackspace() }
                    }
                    if (newText.isNotEmpty()) {
                        sendText(newText)
                    }
                }
            }

            updateInputMethodState()
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_ENTER -> {
                            sender.sendEnter()
                            onCloseInputMethod()
                        }
                        KeyEvent.KEYCODE_DEL -> {
                            if (cursorPosition > 0) {
                                textBuffer.deleteCharAt(cursorPosition - 1)
                                cursorPosition--
                                updateInputMethodState()
                            }
                            sender.sendBackspace()
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> sender.sendLeft()
                        KeyEvent.KEYCODE_DPAD_RIGHT -> sender.sendRight()
                        KeyEvent.KEYCODE_DPAD_UP -> sender.sendUp()
                        KeyEvent.KEYCODE_DPAD_DOWN -> sender.sendDown()
                        else -> {
                            if (event.unicodeChar != 0) {
                                val char = event.unicodeChar.toChar()
                                textBuffer.insert(cursorPosition, char)
                                cursorPosition++
                                updateInputMethodState()
                                sender.sendChar(char)
                            } else {
                                sender.sendOther(event)
                            }
                        }
                    }
                }
            }
            return true
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean {
            if (start in 0..textBuffer.length && end in 0..textBuffer.length && start <= end) {
                composingStart = start
                composingEnd = end
                updateInputMethodState()
                return true
            }
            return false
        }

        override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence {
            val start = max(0, cursorPosition - length)
            return textBuffer.substring(start, cursorPosition)
        }

        override fun getTextAfterCursor(length: Int, flags: Int): CharSequence {
            val end = min(textBuffer.length, cursorPosition + length)
            return textBuffer.substring(cursorPosition, end)
        }

        override fun getSelectedText(p0: Int): CharSequence? = null

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {

            if (composingStart in 0..<composingEnd) {
                val safeStart = composingStart.coerceIn(0, textBuffer.length)
                val safeEnd = composingEnd.coerceIn(0, textBuffer.length)
                if (isMicrosoftSwiftKey || safeStart < safeEnd) {
                    textBuffer.delete(safeStart, safeEnd)
                    cursorPosition = safeStart
                }
            } else {

                if (cursorPosition < textBuffer.length) {
                    textBuffer.delete(cursorPosition, textBuffer.length)
                }
                composingStart = cursorPosition
            }

            textBuffer.insert(cursorPosition, text)
            composingStart = cursorPosition
            composingEnd = composingStart + text.length
            cursorPosition = composingEnd

            updateInputMethodState()
            return true
        }

        override fun finishComposingText(): Boolean {
            if (composingStart in 0..<composingEnd) {

                val safeStart = composingStart.coerceIn(0, textBuffer.length)
                val safeEnd = composingEnd.coerceIn(0, textBuffer.length)
                if (safeStart < safeEnd) {
                    val composedText = textBuffer.substring(safeStart, safeEnd)
                    if (isMicrosoftSwiftKey) {
                        sendText(composedText)
                    } else {
                        if (inBatchEdit) {
                            pendingTextToSend.append(composedText)
                        } else {
                            sendText(composedText)
                        }
                    }
                }

                composingStart = -1
                composingEnd = -1
            }

            updateInputMethodState()
            return true
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            if (start in 0..textBuffer.length && end in 0..textBuffer.length) {
                cursorPosition = end
                updateInputMethodState()
                return true
            }
            return false
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val deleteStart = max(0, cursorPosition - beforeLength)
            val deleteEnd = cursorPosition
            if (deleteStart < deleteEnd) {
                textBuffer.delete(deleteStart, deleteEnd)
                cursorPosition = deleteStart
                if (isMicrosoftSwiftKey || !inBatchEdit) {
                    repeat(beforeLength) { sender.sendBackspace() }
                } else {
                    pendingBackspaceCount += beforeLength
                }
            }
            updateInputMethodState()
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
            val deleteStart = max(0, cursorPosition - beforeLength)
            val deleteEnd = cursorPosition
            if (deleteStart < deleteEnd) {
                textBuffer.delete(deleteStart, deleteEnd)
                cursorPosition = deleteStart
                if (isMicrosoftSwiftKey) {
                    repeat(beforeLength) { sender.sendBackspace() }
                }
                updateInputMethodState()
            }
            return true
        }

        override fun beginBatchEdit(): Boolean {
            if (!isMicrosoftSwiftKey) {
                inBatchEdit = true

                pendingBackspaceCount = 0
                pendingTextToSend.clear()
            }
            return true
        }

        override fun endBatchEdit(): Boolean {
            if (!isMicrosoftSwiftKey) {
                inBatchEdit = false

                flushPendingText()
            }
            return true
        }

        override fun clearMetaKeyStates(p0: Int): Boolean = true
        override fun closeConnection() {}
        override fun commitCompletion(p0: CompletionInfo?): Boolean = false
        override fun commitContent(p0: InputContentInfo, p1: Int, p2: Bundle?): Boolean = false
        override fun commitCorrection(p0: CorrectionInfo?): Boolean = false

        override fun performEditorAction(editorAction: Int): Boolean {

            sender.sendEnter()
            onCloseInputMethod()
            return true
        }

        override fun performContextMenuAction(p0: Int): Boolean = false
        override fun performPrivateCommand(p0: String?, p1: Bundle?): Boolean = false
        override fun reportFullscreenMode(p0: Boolean): Boolean = true

        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean {
            if (cursorUpdateMode and InputConnection.CURSOR_UPDATE_IMMEDIATE != 0) {
                updateCursorAnchorInfo()
                return true
            }
            return false
        }

        override fun getCursorCapsMode(p0: Int): Int = 0

        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
            return ExtractedText().apply {
                text = textBuffer
                startOffset = 0
                partialStartOffset = -1
                partialEndOffset = -1
                selectionStart = cursorPosition
                selectionEnd = cursorPosition
            }
        }

        override fun getHandler(): Handler? = null

        private fun updateCursorAnchorInfo() {
            imm.updateCursorAnchorInfo(
                view,
                CursorAnchorInfo.Builder().apply {
                    setSelectionRange(cursorPosition, cursorPosition)

                    if (composingStart in 0..<composingEnd) {
                        val safeStart = composingStart.coerceIn(0, textBuffer.length)
                        val safeEnd = composingEnd.coerceIn(0, textBuffer.length)
                        if (safeStart < safeEnd) {
                            setComposingText(safeStart, textBuffer.substring(safeStart, safeEnd))
                        }
                    }
                    setInsertionMarkerLocation(
                        fakeCursorRect.left.toFloat(),
                        fakeCursorRect.top.toFloat(),
                        fakeCursorRect.right.toFloat(),
                        fakeCursorRect.bottom.toFloat(),
                        CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION
                    )
                    setMatrix(view.matrix)
                }.build()
            )
        }

        private fun updateInputMethodState() {
            imm.updateSelection(
                view,
                cursorPosition, cursorPosition,
                composingStart, composingEnd
            )
            updateCursorAnchorInfo()
        }
    }
}
