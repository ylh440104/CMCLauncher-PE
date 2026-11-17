package com.cmc.launcher.game.version.export

import android.content.Context
import com.cmc.launcher.coroutine.TitledTask
import com.cmc.launcher.game.version.installed.Version
import java.io.File

abstract class AbstractExporter(
    val type: PackType
) {

    abstract fun MutableList<TitledTask>.buildTasks(
        context: Context,
        version: Version,
        info: ExportInfo,
        tempPath: File
    )

    abstract val fileSuffix: String

    protected fun generateTargetRoot(
        file: File,
        rootPath: String,
        targetPath: String
    ): File {
        return File(targetPath, relativePath(file, rootPath))
    }

    protected fun relativePath(
        file: File,
        rootPath: String
    ): String {
        return file.absolutePath
            .removePrefix(rootPath)
            .removePrefix("/")
    }
}
