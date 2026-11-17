package com.cmc.launcher.ui.screens.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavBackStack
import com.cmc.launcher.setting.enums.isLauncherInDarkTheme
import com.cmc.launcher.ui.base.BaseScreen
import com.cmc.launcher.ui.code_editor.EditorState
import com.cmc.launcher.ui.code_editor.SoraEditor
import com.cmc.launcher.ui.code_editor.TextMateRegistry
import com.cmc.launcher.ui.code_editor.scheme.SchemeIDEADark
import com.cmc.launcher.ui.code_editor.scheme.SchemeIDEALight
import com.cmc.launcher.ui.screens.NormalNavKey
import com.cmc.launcher.ui.screens.TitledNavKey
import com.cmc.launcher.ui.screens.navigateTo
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.viewmodel.ScreenBackStackViewModel
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

fun NavBackStack<TitledNavKey>.navigateToLogView(
    logPath: String,
) = this.navigateTo(
    screenKey = NormalNavKey.LogView(logPath = logPath),
    useClassEquality = true
)

private const val MAX_LOG_VIEW_SIZE: Long = 8L * 1024 * 1024

private fun readLog(file: File): String {
    val size = file.length()
    if (size <= MAX_LOG_VIEW_SIZE) return file.readText()
    RandomAccessFile(file, "r").use { raf ->
        raf.seek(size - MAX_LOG_VIEW_SIZE)
        val bytes = ByteArray(MAX_LOG_VIEW_SIZE.toInt())
        raf.readFully(bytes)
        val text = String(bytes, Charsets.UTF_8)

            .trimStart('\uFFFD')

        val firstNewline = text.indexOf('\n')
        return if (firstNewline >= 0 && firstNewline < text.length - 1) {
            text.substring(firstNewline + 1)
        } else {
            text
        }
    }
}

@Composable
fun LogViewScreen(
    key: NormalNavKey.LogView,
    backStackViewModel: ScreenBackStackViewModel
) {
    val isDark = isLauncherInDarkTheme()
    val context = LocalContext.current

    var editorState by remember { mutableStateOf<EditorState>(EditorState.Loading) }

    LaunchedEffect(key) {
        editorState = EditorState.Loading
        val content = withContext(Dispatchers.IO) {
            runCatching {
                readLog(File(key.logPath))
            }.getOrElse { e ->
                Logger.warning("ViewLog", "Unable to read log file!", e)
                e.message
            }
        }
        editorState = EditorState.Success(Content(content))
    }

    BaseScreen(
        screenKey = key,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) { isVisible ->
        if (isVisible)  {
            val fallbackScheme = remember(isDark) {
                if (isDark) SchemeIDEADark() else SchemeIDEALight()
            }
            var language by remember { mutableStateOf<Language?>(null) }
            var scheme by remember { mutableStateOf<EditorColorScheme?>(null) }
            LaunchedEffect(isDark) {
                language = TextMateRegistry.languageFor("text.log", context)
                scheme = TextMateRegistry.colorScheme(isDark, context)
            }

            SoraEditor(
                state = editorState,
                scheme = scheme ?: fallbackScheme,
                language = language,
                isReadOnly = true,
                onSaveClick = {}
            )
        }
    }
}
