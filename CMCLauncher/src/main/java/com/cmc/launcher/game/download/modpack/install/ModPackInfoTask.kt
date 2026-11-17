package com.cmc.launcher.game.download.modpack.install

import android.content.Context
import com.cmc.launcher.R
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.coroutine.TaskFlowExecutor
import com.cmc.launcher.coroutine.TaskLogOutput
import com.cmc.launcher.coroutine.TitledTask
import com.cmc.launcher.coroutine.addTask
import com.cmc.launcher.coroutine.buildPhase
import com.cmc.launcher.game.download.game.GameDownloadInfo
import com.cmc.launcher.game.download.game.GameInstaller
import com.cmc.launcher.game.download.modpack.platform.AbstractPack
import com.cmc.launcher.game.download.modpack.platform.PackPlatform
import com.cmc.launcher.game.version.installed.VersionConfig
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.file.copyDirectoryContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

abstract class ModPackInfoTask(
    protected val root: File,
    platform: PackPlatform
) : AbstractPack(platform = platform) {

    protected lateinit var modpackInfo: ModPackInfo

    protected lateinit var targetVersionName: String

    protected lateinit var gameDownloadInfo: GameDownloadInfo

    protected abstract suspend fun readInfo(
        task: Task,
        versionFolder: File,
        root: File
    ): ModPackInfo

    override fun getFinalClientName(): String {
        return targetVersionName
    }

    override fun buildTaskPhases(
        context: Context,
        scope: CoroutineScope,
        versionFolder: File,
        waitForVersionName: suspend (name: String) -> String,
        addPhases: (List<TaskFlowExecutor.TaskPhase>) -> Unit,
        onClearTemp: suspend () -> Unit,
        logOutputHolder: MutableStateFlow<TaskLogOutput?>
    ): List<TaskFlowExecutor.TaskPhase> {
        return listOf(
            buildPhase {

                addTask(
                    id = "ImportModpack.ExtractFiles",
                    title = androidText(R.string.import_modpack_task_extract_files_and_schedule_download),
                    icon = R.drawable.ic_build_outlined
                ) { task ->
                    modpackInfo = readInfo(task, versionFolder, root)
                }

                addTask(
                    id = "ImportModpack.WaitUserForVersionName",
                    title = androidText(R.string.download_install_input_version_name),
                    icon = R.drawable.ic_edit_outlined
                ) { task ->
                    task.updateProgress(-1f)
                    targetVersionName = waitForVersionName(modpackInfo.name)
                }

                addTask(
                    id = "ImportModpack.DownloadMods",
                    dispatcher = Dispatchers.IO,
                    title = androidText(R.string.download_modpack_download)
                ) { task ->
                    val downloadTask = ModDownloader(modpackInfo.files)
                    downloadTask.startDownload(task)
                }

                addTask(
                    id = "ImportModpack.RetrieveLoader",
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
                        logOutputHolder = logOutputHolder
                    )
                    addPhases(
                        gameInstaller.getTaskPhase(
                            createIsolation = false,
                            onInstalled = { targetClientDir ->

                                val finalTask = TitledTask(
                                    title = androidText(R.string.download_modpack_final_move),
                                    runningIcon = R.drawable.ic_build_outlined,
                                    task = createFinalInstallTask(
                                        targetClientDir = targetClientDir,
                                        tempVersionsDir = versionFolder,
                                        onClearTemp = onClearTemp
                                    )
                                )

                                addPhases(
                                    listOf(
                                        buildPhase { add(finalTask) }
                                    )
                                )
                            }
                        )
                    )
                }
            }
        )
    }

    private fun createFinalInstallTask(
        targetClientDir: File,
        tempVersionsDir: File,
        onClearTemp: suspend () -> Unit
    ) = Task.runTask(
        id = "ImportModpack.FinalInstall",
        dispatcher = Dispatchers.IO,
        task = { task ->
            task.updateProgress(-1f)

            copyDirectoryContents(
                tempVersionsDir,
                targetClientDir
            ) { percentage ->
                task.updateProgress(percentage = percentage)
            }

            VersionConfig.createIsolationForModPack(targetClientDir).apply {
                this.versionSummary = modpackInfo.summary ?: ""
                this.ramAllocation = modpackInfo.ram ?: -1
            }.save()

            task.updateProgress(-1f)
            task.updateMessage(androidText(R.string.download_install_clear_temp))
            onClearTemp()
        }
    )
}
