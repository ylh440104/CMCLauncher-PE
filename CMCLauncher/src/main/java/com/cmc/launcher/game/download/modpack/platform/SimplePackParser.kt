package com.cmc.launcher.game.download.modpack.platform

import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.file.locateRealRoot
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SimplePackParser"

abstract class SimplePackParser<E: PackManifest>(
    val indexFilePath: String,
    protected val manifestClass: Class<E>,
    private val extraProcess: (suspend (rootFolder: File) -> Boolean)? = null,
    private val buildPack: (rootFolder: File, manifest: E) -> AbstractPack
) : PackParser {

    override suspend fun parse(packFolder: File): AbstractPack? {
        val root = locateRealRoot(packFolder)

        val indexFile = File(root, indexFilePath)
        return withContext(Dispatchers.IO) {
            if (!indexFile.exists()) {
                Logger.debug(TAG, "${getIdentifier()} parser -> manifest file does not exist $indexFile")
                return@withContext null
            }

            val rawString = indexFile.readText()
            val manifest = GSON.fromJson(rawString, manifestClass)

            if (extraProcess?.invoke(root) == false) {

                return@withContext null
            }

            buildPack(root, manifest)
        }
    }
}
