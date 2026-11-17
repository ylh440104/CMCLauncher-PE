package com.cmc.launcher.game.download.modpack.install

import android.content.Context
import android.net.Uri
import com.cmc.launcher.R
import com.cmc.launcher.context.copyLocalFile
import com.cmc.launcher.coroutine.TaskFlowExecutor
import com.cmc.launcher.coroutine.TaskLogOutput
import com.cmc.launcher.coroutine.TitledTask
import com.cmc.launcher.coroutine.addTask
import com.cmc.launcher.coroutine.buildPhase
import com.cmc.launcher.game.download.modpack.platform.ALL_PACK_PARSER
import com.cmc.launcher.game.download.modpack.platform.AbstractPack
import com.cmc.launcher.game.path.GamePathManager
import com.cmc.launcher.game.version.installed.VersionFolders
import com.cmc.launcher.game.version.installed.VersionsManager
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.file.extractFromZip
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.network.isUsingMobileData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import org.apache.commons.compress.archivers.zip.ZipFile as ApacheZipFile
import java.util.zip.ZipFile as JDKZipFile

private const val TAG = "ModpackImporter"

class ModpackImporter(
    private val context: Context,
    private val uri: Uri,
    private val scope: CoroutineScope,
    private val waitForVersionName: suspend (String) -> String,
    private val waitForConfirmMobileData: suspend () -> Boolean,
) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val taskFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    private val _logOutput = MutableStateFlow<TaskLogOutput?>(null)

    val logOutput: StateFlow<TaskLogOutput?> = _logOutput.asStateFlow()

    private lateinit var modpack: AbstractPack

    fun startImport(
        isRunning: () -> Unit = {},
        onFinished: (version: String) -> Unit,
        onCancelled: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (taskExecutor.isRunning()) {
            isRunning()
            return
        }

        taskExecutor.executePhasesAsync(
            onStart = {
                val tasks = getTaskPhases()
                taskExecutor.addPhases(tasks)
            },
            onComplete = {
                onFinished(modpack.getFinalClientName())
            },
            onError = { e ->
                if (e is UsingMobileDataException) {

                    onCancelled()
                    return@executePhasesAsync
                }
                onError(e)
            }
        )
    }

    private suspend fun getTaskPhases() = withContext(Dispatchers.IO) {

        val tempModPackDir = PathManager.DIR_CACHE_MODPACK_DOWNLOADER
        val tempVersionsDir = File(tempModPackDir, "fkVersion")

        val installerDir = File(tempModPackDir, "installer")
        val installerFile = File(installerDir, ".temp_installer.zip")

        val packDir = File(installerDir, ".temp_pack")

        listOf(
            buildPhase {

                addTask(
                    id = "ImportModpack.Cleanup",
                    title = androidText(R.string.download_install_clear_temp),
                    icon = R.drawable.ic_auto_delete_outlined
                ) { _ ->
                    GamePathManager.waitForRefresh()
                    VersionsManager.waitForRefresh()
                    clearTempModPackDir()

                    tempModPackDir.createDirAndLog()
                    tempVersionsDir.createDirAndLog()
                    installerDir.createDirAndLog()
                    packDir.createDirAndLog()
                    VersionFolders.MOD.getDir(tempVersionsDir).createDirAndLog()
                }

                addTask(
                    id = "ImportModpack.ImportFile",
                    title = androidText(R.string.import_modpack_task_unpack),
                    dispatcher = Dispatchers.IO,
                    icon = R.drawable.ic_unarchive_outlined
                ) { task ->
                    task.updateProgress(-1f)
                    context.copyLocalFile(uri, installerFile)

                    try {
                        JDKZipFile(installerFile).use { zip ->
                            zip.extractFromZip("", packDir)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Logger.warning(TAG, "JDK ZipFile failed to unpack, fallback to Apache ZipFile.", e)
                        try {
                            ApacheZipFile.builder().setFile(installerFile).get().use { zip ->
                                zip.extractFromZip("", packDir)
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e

                            Logger.error(TAG, "Unable to extract the installer file. Is it really a compressed archive?", e)
                            throw PackNotSupportedException(
                                reason = UnsupportedPackReason.CorruptedArchive
                            )
                        }
                    }

                    if (isUsingMobileData(context)) {
                        val use = waitForConfirmMobileData()
                        if (!use) {

                            throw UsingMobileDataException()
                        }
                    }
                }

                addTask(
                    id = "ImportModpack.ParsePack",
                    title = androidText(R.string.import_modpack_task_parse),
                    icon = R.drawable.ic_build_outlined
                ) { task ->
                    task.updateProgress(-1f)

                    modpack = run {

                        for (parser in ALL_PACK_PARSER) {
                            ensureActive()

                            val result = runCatching {
                                parser.parse(packFolder = packDir)
                            }.onFailure { th ->
                                Logger.debug(TAG, "${parser.getIdentifier()} parser does not recognize this format", th)
                            }.getOrNull()

                            if (result != null) {

                                Logger.info(TAG, "Successfully detected the modpack format: ${result.platform.identifier}")
                                return@run result
                            } else {
                                Logger.debug(TAG, "Skipped the ${parser.getIdentifier()} parser")
                            }
                        }

                        null
                    } ?: throw PackNotSupportedException(UnsupportedPackReason.UnsupportedFormat)

                    taskExecutor.addPhases(
                        modpack.buildTaskPhases(
                            context = context,
                            scope = scope,
                            versionFolder = tempVersionsDir,
                            waitForVersionName = waitForVersionName,
                            addPhases = { phases ->
                                taskExecutor.addPhases(phases)
                            },
                            onClearTemp = {
                                clearTempModPackDir()
                            },
                            logOutputHolder = _logOutput
                        )
                    )
                }
            }
        )
    }

    private suspend fun clearTempModPackDir() = withContext(Dispatchers.IO) {
        PathManager.DIR_CACHE_MODPACK_DOWNLOADER.takeIf { it.exists() }?.let { folder ->
            FileUtils.deleteQuietly(folder)
            Logger.info(TAG, "Temporary modpack directory cleared.")
        }
    }

    fun cancel() {
        taskExecutor.cancel()
    }

    private fun File.createDirAndLog(): File {
        this.mkdirs()
        Logger.debug(TAG, "Created directory: $this")
        return this
    }
}
