package com.cmc.launcher.game.download.modpack.install

import android.content.Context
import com.cmc.launcher.R
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.coroutine.TaskFlowExecutor
import com.cmc.launcher.coroutine.TaskLogOutput
import com.cmc.launcher.coroutine.TitledTask
import com.cmc.launcher.coroutine.addTask
import com.cmc.launcher.coroutine.buildPhase
import com.cmc.launcher.game.download.addon.platform.PlatformVersion
import com.cmc.launcher.game.download.addon.platform.mcim.mapMCIMMirrorUrls
import com.cmc.launcher.game.download.game.GameDownloadInfo
import com.cmc.launcher.game.download.game.GameInstaller
import com.cmc.launcher.game.version.installed.VersionConfig
import com.cmc.launcher.game.version.installed.VersionFolders
import com.cmc.launcher.game.version.installed.getVersionIconFile
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.file.copyDirectoryContents
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.network.downloadFile
import com.cmc.launcher.utils.network.downloadFileFromSources
import com.cmc.launcher.utils.network.isUsingMobileData
import com.cmc.launcher.utils.network.withSpeedReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File

private const val TAG = "ModPackInstaller"

class ModPackInstaller(
    private val context: Context,
    private val version: PlatformVersion,
    private val iconUrl: String?,
    private val scope: CoroutineScope,
    private val waitForVersionName: suspend (ModPackInfo) -> String,
    private val waitForConfirmMobileData: suspend () -> Boolean,
) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val tasksFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    private val _logOutput = MutableStateFlow<TaskLogOutput?>(null)

    val logOutput: StateFlow<TaskLogOutput?> = _logOutput.asStateFlow()

    private lateinit var modpackInfo: ModPackInfo

    private lateinit var targetVersionName: String

    private lateinit var gameDownloadInfo: GameDownloadInfo

    fun installModPack(
        isRunning: () -> Unit = {},
        onInstalled: (version: String) -> Unit,
        onCancelled: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (taskExecutor.isRunning()) {

            isRunning()
            return
        }

        taskExecutor.executePhasesAsync(
            onStart = {
                val tasks = getTaskPhase()
                taskExecutor.addPhases(tasks)
            },
            onComplete = {
                onInstalled(targetVersionName)
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

    private suspend fun getTaskPhase() = withContext(Dispatchers.IO) {

        val tempModPackDir = PathManager.DIR_CACHE_MODPACK_DOWNLOADER
        val tempVersionsDir = File(tempModPackDir, "fkVersion")

        val installerFile = File(tempModPackDir, "installer.zip")

        val tempIconFile = File(tempModPackDir, "icon.png")

        listOf(
            buildPhase {

                addTask(
                    id = "Download.ModPack.ClearTemp",
                    title = androidText(R.string.download_install_clear_temp),
                    icon = R.drawable.ic_auto_delete_outlined
                ) { _ ->
                    clearTempModPackDir()

                    tempModPackDir.createDirAndLog()
                    tempVersionsDir.createDirAndLog()
                    VersionFolders.MOD.getDir(tempVersionsDir).createDirAndLog()

                    if (isUsingMobileData(context)) {
                        val use = waitForConfirmMobileData()
                        if (!use) {

                            throw UsingMobileDataException()
                        }
                    }
                }

                addTask(
                    id = "Download.ModPack.Installer",
                    title = androidText(R.string.download_game_install_base_download_file2, version.platformDisplayName())
                ) { task ->
                    val totalFileSize = version.platformFileSize().toDouble()
                    var downloadedSize = 0L
                    fun updateProgress() {
                        task.updateProgress((downloadedSize.toDouble() / totalFileSize).toFloat())
                    }
                    withSpeedReport(
                        onSpeedReport = { bytes ->
                            task.updateSpeed(bytes)
                        },
                        onClear = {
                            task.clearSpeed()
                        }
                    ) { report ->
                        downloadFileFromSources(
                            urls = version
                                .platformDownloadUrl()
                                .mapMCIMMirrorUrls(),
                            sha1 = version.platformSha1(),
                            outputFile = installerFile,
                            sizeCallback = { size ->
                                downloadedSize += size
                                updateProgress()
                                report(size)
                            }
                        )
                    }

                    task.updateProgress(-1f)
                    task.updateMessage(null)
                    iconUrl?.let { iconUrl ->
                        downloadFile(
                            url = iconUrl,
                            outputFile = tempIconFile
                        )
                    }
                }

                addTask(
                    id = "Parse.ModPack",
                    title = androidText(R.string.download_modpack_install_parse),
                    icon = R.drawable.ic_build_outlined
                ) { task ->
                    modpackInfo = parserModPack(
                        file = installerFile,
                        platform = version.platform(),
                        targetFolder = tempVersionsDir,
                        task = task
                    )
                }

                addTask(
                    id = "Download.ModPack.WaitUserForVersionName",
                    title = androidText(R.string.download_install_input_version_name),
                    icon = R.drawable.ic_edit_outlined
                ) { task ->
                    task.updateProgress(-1f)
                    targetVersionName = waitForVersionName(modpackInfo)
                }

                addTask(
                    id = "Download.ModPack.Mods",
                    dispatcher = Dispatchers.IO,
                    title = androidText(R.string.download_modpack_download)
                ) { task ->
                    val downloadTask = ModDownloader(modpackInfo.files)
                    downloadTask.startDownload(task)
                }

                addTask(
                    id = "ModPack.Retrieve.Loader",
                    title = androidText(R.string.download_modpack_get_loaders),
                    icon = R.drawable.ic_build_outlined
                ) { _ ->

                    gameDownloadInfo = modpackInfo.retrieveLoaderTask(
                        targetVersionName = targetVersionName
                    )

                    val gameInstaller = GameInstaller(
                        context = context,
                        info = gameDownloadInfo,
                        scope = scope,
                        logOutputHolder = _logOutput
                    )
                    taskExecutor.addPhases(
                        phases = gameInstaller.getTaskPhase(
                            createIsolation = false,
                            onInstalled = { targetClientDir ->

                                val finalTask = TitledTask(
                                    title = androidText(R.string.download_modpack_final_move),
                                    runningIcon = R.drawable.ic_build_outlined,
                                    task = createFinalInstallTask(
                                        targetClientDir = targetClientDir,
                                        tempVersionsDir = tempVersionsDir,
                                        tempIconFile = tempIconFile
                                    )
                                )

                                taskExecutor.addPhase(
                                    buildPhase { add(finalTask) }
                                )
                            }
                        )
                    )
                }
            }
        )
    }

    fun cancelInstall() {
        taskExecutor.cancel()
    }

    private suspend fun clearTempModPackDir() = withContext(Dispatchers.IO) {
        PathManager.DIR_CACHE_MODPACK_DOWNLOADER.takeIf { it.exists() }?.let { folder ->
            FileUtils.deleteQuietly(folder)
            Logger.info(TAG, "Temporary modpack directory cleared.")
        }
    }

    private fun createFinalInstallTask(
        targetClientDir: File,
        tempVersionsDir: File,
        tempIconFile: File
    ) = Task.runTask(
        id = "ModPack.Final.Install",
        dispatcher = Dispatchers.IO,
        task = { task ->
            task.updateProgress(-1f)

            copyDirectoryContents(
                tempVersionsDir,
                targetClientDir
            ) { percentage ->
                task.updateProgress(percentage = percentage)
            }

            if (tempIconFile.exists() && tempIconFile.isFile) {
                val iconFile = getVersionIconFile(targetClientDir)
                if (iconFile.exists()) FileUtils.deleteQuietly(iconFile)
                tempIconFile.copyTo(iconFile)
            }

            VersionConfig.createIsolationForModPack(targetClientDir).apply {
                this.versionSummary = modpackInfo.summary ?: ""
                this.ramAllocation = modpackInfo.ram ?: -1
            }.save()

            task.updateProgress(-1f)
            task.updateMessage(androidText(R.string.download_install_clear_temp))
            clearTempModPackDir()
        }
    )

    private fun File.createDirAndLog(): File {
        this.mkdirs()
        Logger.debug(TAG, "Created directory: $this")
        return this
    }
}
