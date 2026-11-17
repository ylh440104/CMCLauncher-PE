package com.cmc.launcher.game.version.mod

import android.util.Log
import com.cmc.launcher.utils.file.UnpackZipException
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val TAG = "AllModReader"

const val READER_PARALLELISM = 8

class AllModReader(val modsDir: File) {
    val resultsMutex = Mutex()

    private fun <T> scanFiles(
        pack: (LocalMod) -> T
    ): List<ReadTask<T>> {
        val files = modsDir.listFiles()?.filter { !it.isDirectory } ?: return emptyList()
        return files.map { file ->
            ReadTask(file, pack)
        }
    }

    suspend fun readAllForRemote(): List<RemoteMod> = withContext(Dispatchers.IO) {

        val results = readAllMods { RemoteMod(localMod = it) }
        return@withContext results.sortedWith(
            compareBy(
                { mod: RemoteMod -> mod.localMod.file.name.lowercase(Locale.ROOT) },
                { mod: RemoteMod -> mod.localMod.file.name }
            )
        )
    }

    suspend fun readAllLocals(): List<LocalMod> = withContext(Dispatchers.IO) {
        readAllMods { it }
    }

    private suspend fun <T> readAllMods(
        pack: (LocalMod) -> T
    ): List<T> = withContext(Dispatchers.IO) {

        val tasks = scanFiles(pack)

        buildList {
            val taskChannel = Channel<ReadTask<T>>(Channel.UNLIMITED)

            val workers = List(READER_PARALLELISM) {
                launch(Dispatchers.IO) {
                    for (task in taskChannel) {
                        val mod = task.execute()
                        resultsMutex.withLock {
                            add(mod)
                        }
                    }
                }
            }

            tasks.forEach { taskChannel.send(it) }
            taskChannel.close()

            workers.joinAll()
        }
    }

    private class ReadTask<T>(
        private val file: File,
        private val pack: (LocalMod) -> T
    ) {
        suspend fun execute(): T {
            try {
                currentCoroutineContext().ensureActive()

                val extension = if (file.isDisabled()) {
                    File(file.nameWithoutExtension).extension
                } else {
                    file.extension
                }

                return MOD_READERS[extension]?.firstNotNullOfOrNull { reader ->
                    runCatching {
                        pack(
                            reader.fromLocal(file)
                        )
                    }.onFailure { e ->
                        if (e !is UnpackZipException) Log.d(TAG, "Exception encountered while parsing the mod", e)
                    }.getOrNull()

                } ?: throw IllegalArgumentException("No matching reader for extension: $extension")
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> throw e
                    else -> {
                        Logger.warning(TAG, "Failed to read mod: $file", e)
                        return pack(
                            createNotMod(file)
                        )
                    }
                }
            }
        }
    }
}
