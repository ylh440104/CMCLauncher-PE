package com.cmc.launcher.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.cmc.launcher.R
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.ui.base.BaseAppCompatActivity
import com.cmc.launcher.ui.screens.content.elements.Background
import com.cmc.launcher.ui.screens.main.control_editor.ControlEditor
import com.cmc.launcher.ui.theme.CMCLauncherTheme
import com.cmc.launcher.ui.theme.backgroundColor
import com.cmc.launcher.ui.theme.onBackgroundColor
import com.cmc.launcher.viewmodel.BackgroundViewModel
import com.cmc.launcher.viewmodel.EditorViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

private const val BUNDLE_CONTROL = "BUNDLE_CONTROL"

@AndroidEntryPoint
class ControlEditorActivity : BaseAppCompatActivity() {
    override fun isIgnoreNotch(): Boolean = AllSettings.gameFullScreen.getValue()

    override fun getTaskDescriptionTitle(): String = getString(R.string.control_manage_info_edit)

    private val editorViewModel: EditorViewModel by viewModels()

    private val backgroundViewModel: BackgroundViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val controlPath: String = intent.extras?.getString(BUNDLE_CONTROL) ?: return runFinish()

        val controlFile: File = File(controlPath).takeIf { it.isFile && it.exists() } ?: return runFinish()

        val layout: ControlLayout = runCatching {
            loadLayoutFromFile(controlFile)
        }.getOrNull() ?: return runFinish()

        editorViewModel.initLayout(layout)

        editorViewModel.styleCabinet.load()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                editorViewModel.onBackPressed(context = this@ControlEditorActivity) {
                    this@ControlEditorActivity.finish()
                }
            }
        })

        setContent {
            CMCLauncherTheme(
                backgroundViewModel = backgroundViewModel
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor(),
                    contentColor = onBackgroundColor()
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Background(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = backgroundViewModel,
                            allowVideo = false
                        )

                        ControlEditor(
                            viewModel = editorViewModel,
                            targetFile = controlFile,
                            exit = {

                                finish()
                            },
                            menuExit = {

                                editorViewModel.showExitEditorDialog(
                                    context = this@ControlEditorActivity,
                                    onExit = {
                                        this@ControlEditorActivity.finish()
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

fun startEditorActivity(context: Context, file: File) {
    val intent = Intent(context, ControlEditorActivity::class.java).apply {
        putExtra(BUNDLE_CONTROL, file.absolutePath)
    }
    context.startActivity(intent)
}
