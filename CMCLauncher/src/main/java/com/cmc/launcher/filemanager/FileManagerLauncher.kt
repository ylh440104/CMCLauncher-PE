package com.cmc.launcher.filemanager

import android.content.Context
import android.content.Intent
import com.cmc.launcher.filemanager.ui.FileManagerActivity

object FileManagerLauncher {

    const val EXTRA_ROOT_PATH = "fm.extra.ROOT_PATH"

    const val EXTRA_CURRENT_PATH = "fm.extra.CURRENT_PATH"

    const val EXTRA_LOGS_DIR = "fm.extra.LOGS_DIR"

    fun launch(
        context: Context,
        rootPath: String,
        currentPath: String? = null,
        logsDir: String? = null
    ) {
        require(rootPath.isNotBlank()) { "rootPath must not be blank" }

        val intent = Intent(context, FileManagerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_ROOT_PATH, rootPath)
            currentPath?.let { putExtra(EXTRA_CURRENT_PATH, it) }
            logsDir?.let { putExtra(EXTRA_LOGS_DIR, it) }
        }
        context.startActivity(intent)
    }
}
