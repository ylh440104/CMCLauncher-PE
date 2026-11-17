package com.cmc.launcher.filemanager.ui

import android.app.ActivityManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.cmc.launcher.R
import com.cmc.launcher.filemanager.FileManagerLauncher
import com.cmc.launcher.filemanager.os.FmLog
import com.cmc.launcher.filemanager.viewmodel.FileManagerViewModel
import com.cmc.launcher.setting.loadAllSettings
import com.cmc.launcher.ui.theme.CMCLauncherTheme
import dagger.hilt.android.AndroidEntryPoint
import java.nio.file.Paths

sealed interface FileManagerInitResult {
    data object Pending : FileManagerInitResult

    data object Ok : FileManagerInitResult
    data class Failed(val message: String) : FileManagerInitResult
}

@AndroidEntryPoint
class FileManagerActivity : ComponentActivity() {
    private var initResult by mutableStateOf<FileManagerInitResult>(FileManagerInitResult.Pending)

    private var rootPathStr: String? = null

    private var currentPathStr: String? = null

    private var _vm by mutableStateOf<FileManagerViewModel?>(null)

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras(super.defaultViewModelCreationExtras).apply {
            if (initResult !is FileManagerInitResult.Ok) return@apply
            val bundle = Bundle().apply {
                putString(FileManagerViewModel.KEY_ROOT_PATH, rootPathStr)
                currentPathStr?.let { putString(FileManagerViewModel.KEY_CURRENT_PATH, it) }
            }
            set(DEFAULT_ARGS_KEY, bundle)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        loadAllSettings(this, true)
        initializeFromIntent()

        setContent {
            CMCLauncherTheme {
                FileManagerRootScreen(
                    initResult = initResult,
                    vm = _vm,
                    onExit = { finish() },
                    onToggleOrientation = { toggleOrientation() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadAllSettings(this, true)
        initializeFromIntent()
    }

    private fun initializeFromIntent() {

        val logsDir = intent.getStringExtra(FileManagerLauncher.EXTRA_LOGS_DIR)?.let { Paths.get(it) }
        FmLog.init(lifecycleScope, logsDir)

        initResult = parseIntent()

        viewModelStore.clear()
        _vm = if (initResult is FileManagerInitResult.Ok) {
            ViewModelProvider(this)[FileManagerViewModel::class.java]
        } else {
            null
        }
    }

    private fun parseIntent(): FileManagerInitResult {
        val root = intent.getStringExtra(FileManagerLauncher.EXTRA_ROOT_PATH)
        if (root.isNullOrBlank()) {
            return FileManagerInitResult.Failed("Missing required extra: ROOT_PATH")
        }
        rootPathStr = root
        currentPathStr = intent.getStringExtra(FileManagerLauncher.EXTRA_CURRENT_PATH)
        return FileManagerInitResult.Ok
    }

    override fun onResume() {
        super.onResume()
        loadAllSettings(this, true)
        applySystemUiByOrientation()
        @Suppress("DEPRECATION")
        setTaskDescription(
            ActivityManager.TaskDescription(getString(R.string.fm_activity_name))
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemUiByOrientation()
    }

    override fun onDestroy() {
        FmLog.close()
        super.onDestroy()
    }

    private fun applySystemUiByOrientation() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (isLandscape) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun toggleOrientation() {
        val current = resources.configuration.orientation
        requestedOrientation = if (current == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    companion object {
        private const val TAG = "FileManagerActivity"
    }
}
