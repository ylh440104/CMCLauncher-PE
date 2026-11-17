package com.cmc.launcher.game.download.modpack.platform.mcbbs

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
import com.cmc.launcher.game.download.modpack.install.retrieveLoader
import com.cmc.launcher.game.download.modpack.platform.AbstractPack
import com.cmc.launcher.game.download.modpack.platform.PackPlatform
import com.cmc.launcher.game.version.installed.VersionConfig
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.file.copyDirectoryContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class MCBBSPack(
    private val root: File,
    private val manifest: MCBBSManifest
): AbstractPack(platform = PackPlatform.MCBBS) {

    private lateinit var targetVersionName: String

    private lateinit var gameDownloadInfo: GameDownloadInfo

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
                    id = "ImportModpack.WaitUserForVersionName",
                    title = androidText(R.string.download_install_input_version_name),
                    icon = R.drawable.ic_edit_outlined
                ) { task ->
                    task.updateProgress(-1f)
                    targetVersionName = waitForVersionName(manifest.name)
                }

                addTask(
                    id = "ImportModpack.ExtractFiles",
                    title = androidText(R.string.import_modpack_task_extract_files),
                    icon = R.drawable.ic_build_outlined
                ) { task ->
                    task.updateProgress(-1f)
                    copyDirectoryContents(
                        from = File(root, "overrides"),
                        to = versionFolder,
                        onProgress = { progress ->
                            task.updateProgress(progress)
                        }
                    )
                }

                addTask(
                    id = "ImportModpack.RetrieveLoader",
                    title = androidText(R.string.download_modpack_get_loaders),
                    icon = R.drawable.ic_build_outlined
                ) {
                    val gameVersion = manifest.getMinecraftVersion()!!

                    gameDownloadInfo = GameDownloadInfo(
                        gameVersion = gameVersion,
                        customVersionName = targetVersionName
                    )

                    manifest.addons.forEach { addon ->
                        with(manifest) { addon.retrieveLoader() }?.let { pair ->
                            pair.retrieveLoader(
                                gameVersion = gameVersion,
                                gameInfo = gameDownloadInfo,
                                pasteGameInfo = { info ->
                                    gameDownloadInfo = info
                                }
                            )
                        }
                    }

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

                manifest.launchInfo.launchArguments?.joinToString(" ")?.let { arg ->
                    this.gameArgs = arg
                }

                manifest.launchInfo.javaArguments?.joinToString(" ")?.let { arg ->
                    this.jvmArgs = arg
                }

                this.ramAllocation = manifest.launchInfo.minMemory
                this.versionSummary = manifest.description
            }.save()

            task.updateProgress(-1f)
            task.updateMessage(androidText(R.string.download_install_clear_temp))
            onClearTemp()
        }
    )
}
