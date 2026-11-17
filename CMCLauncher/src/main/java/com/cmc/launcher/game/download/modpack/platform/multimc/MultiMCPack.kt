package com.cmc.launcher.game.download.modpack.platform.multimc

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
import com.cmc.launcher.game.version.installed.getVersionIconFile
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.file.copyDirectoryContents
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

private const val TAG = "MultiMCPack"

open class MultiMCPack(
    private val root: File,
    private val manifest: MultiMCManifest
): AbstractPack(platform = PackPlatform.MultiMC) {

    private var configuration: MultiMCConfiguration? = null

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
                    id = "ImportModpack.ParseMMCCfg",
                    title = androidText(R.string.import_modpack_task_parse),
                    icon = R.drawable.ic_build_outlined
                ) { task ->
                    task.updateProgress(-1f)

                    configuration = loadMMCConfigFromPack(root)?.also { configuration ->
                        Logger.debug(TAG, "Successfully read the MultiMC instance configuration: $configuration")
                    }

                    val minecraftDir = File(root, ".minecraft")
                    if (minecraftDir.exists() && minecraftDir.isDirectory) {
                        task.updateMessage(androidText(R.string.import_modpack_task_extract_files))
                        copyDirectoryContents(
                            from = minecraftDir,
                            to = versionFolder,
                            onProgress = { progress ->
                                task.updateProgress(progress)
                            }
                        )

                        task.updateProgress(-1f)
                        val iconKey = configuration?.iconKey ?: "icon"
                        val iconFile = File(minecraftDir, "$iconKey.png").takeIf { file ->
                            file.exists() && file.isFile
                        } ?: File(minecraftDir, "icon.png")

                        if (iconFile.exists() && iconFile.isFile) {
                            iconFile.copyTo(getVersionIconFile(versionFolder))

                            iconFile.delete()
                        }
                    }
                }

                addTask(
                    id = "ImportModpack.WaitUserForVersionName",
                    title = androidText(R.string.download_install_input_version_name),
                    icon = R.drawable.ic_edit_outlined
                ) { task ->
                    task.updateProgress(-1f)
                    targetVersionName = waitForVersionName(configuration?.name ?: "")
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

                    manifest.components.forEach { component ->
                        with(manifest) { component.retrieveLoader() }?.let { pair ->
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

                configuration?.jvmArgs?.let { this.jvmArgs = it }

                configuration?.joinServerOnLaunch?.let { this.serverIp = it }
            }.save()

            task.updateProgress(-1f)
            task.updateMessage(androidText(R.string.download_install_clear_temp))
            onClearTemp()
        }
    )
}
