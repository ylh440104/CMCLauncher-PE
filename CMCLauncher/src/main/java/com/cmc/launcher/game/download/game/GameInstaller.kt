package com.cmc.launcher.game.download.game

import android.content.Context
import android.content.Intent
import com.cmc.launcher.R
import com.cmc.launcher.context.GlobalContext
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.coroutine.TaskFlowExecutor
import com.cmc.launcher.coroutine.TaskLogOutput
import com.cmc.launcher.coroutine.TitledTask
import com.cmc.launcher.coroutine.addTask
import com.cmc.launcher.coroutine.buildPhase
import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.addons.modloader.fabriclike.FabricLikeVersion
import com.cmc.launcher.game.addons.modloader.forgelike.ForgeLikeVersion
import com.cmc.launcher.game.addons.modloader.forgelike.neoforge.NeoForgeVersion
import com.cmc.launcher.game.addons.modloader.modlike.ModVersion
import com.cmc.launcher.game.download.addon.platform.mcim.mapMCIMMirrorUrls
import com.cmc.launcher.game.download.game.fabric.getFabricLikeCompleterTask
import com.cmc.launcher.game.download.game.fabric.getFabricLikeDownloadTask
import com.cmc.launcher.game.download.game.forge.getForgeLikeAnalyseTask
import com.cmc.launcher.game.download.game.forge.getForgeLikeDownloadTask
import com.cmc.launcher.game.download.game.forge.getForgeLikeInstallTask
import com.cmc.launcher.game.download.game.forge.isNeoForge
import com.cmc.launcher.game.download.game.forge.targetTempForgeLikeInstaller
import com.cmc.launcher.game.download.game.optifine.getOptiFineDownloadTask
import com.cmc.launcher.game.download.game.optifine.getOptiFineInstallTask
import com.cmc.launcher.game.download.game.optifine.getOptiFineModsDownloadTask
import com.cmc.launcher.game.download.game.optifine.isMinecraft17Plus
import com.cmc.launcher.game.download.game.optifine.targetTempOptiFineInstaller
import com.cmc.launcher.game.download.jvm_server.JVMSocketServer
import com.cmc.launcher.game.download.jvm_server.JvmService
import com.cmc.launcher.game.path.getGameHome
import com.cmc.launcher.game.path.getVersionsHome
import com.cmc.launcher.game.version.download.BaseMinecraftDownloader
import com.cmc.launcher.game.version.download.MinecraftDownloader
import com.cmc.launcher.game.version.installed.VersionConfig
import com.cmc.launcher.game.version.installed.VersionFolders
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.ui.AndroidStringText
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.file.copyDirectoryContents
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.network.downloadFileFromSources
import com.cmc.launcher.utils.network.withSpeedReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File

private const val TAG = "GameInstaller"

private class GameAlreadyInstalledException : RuntimeException()

class GameInstaller(
    private val context: Context,
    private val info: GameDownloadInfo,
    private val scope: CoroutineScope,
    private val logOutputHolder: MutableStateFlow<TaskLogOutput?> = MutableStateFlow(null),
    private val targetGameFolder: File = File(getGameHome())
) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val tasksFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    val logOutput: StateFlow<TaskLogOutput?> = logOutputHolder.asStateFlow()

    private val downloader = BaseMinecraftDownloader(targetGameFolder.absolutePath)

    private var targetClientDir: File? = null

    fun installGame(
        isRunning: () -> Unit = {},
        onInstalled: (version: String) -> Unit,
        onError: (th: Throwable) -> Unit,
        onGameAlreadyInstalled: () -> Unit
    ) {
        if (taskExecutor.isRunning()) {

            isRunning()
            return
        }

        taskExecutor.executePhasesAsync(
            onStart = {
                val tasks = try {
                    getTaskPhase()
                } catch (_: GameAlreadyInstalledException) {
                    onGameAlreadyInstalled()
                    return@executePhasesAsync
                }
                taskExecutor.addPhases(tasks)
            },
            onComplete = {
                onInstalled(info.customVersionName)
            },
            onError = {
                onError(it)
            }
        )
    }

    fun modifyVersion(
        isRunning: () -> Unit = {},
        onModified: () -> Unit,
        onError: (th: Throwable) -> Unit
    ) {
        if (taskExecutor.isRunning()) {

            isRunning()
            return
        }

        taskExecutor.executePhasesAsync(
            onStart = {
                val tasks = getModifyTaskPhase()
                taskExecutor.addPhases(tasks)
            },
            onComplete = {
                onModified()
            },
            onError = {
                onError(it)
            }
        )
    }

    private class InstallationPathConfig(
        val targetClientDir: File,
        val tempGameDir: File,
        val tempMinecraftDir: File,
        val tempGameVersionsDir: File,
        val tempClientDir: File,
        val tempModsDir: File,
        val optifineDir: File?,
        val forgeDir: File?,
        val neoforgeDir: File?,
        val fabricDir: File?,
        val legacyFabricDir: File?,
        val quiltDir: File?
    )

    private fun createPathConfig(checkVersionExists: Boolean = true): InstallationPathConfig {

        val targetClientDir1 = File(getVersionsHome(targetGameFolder.absolutePath), info.customVersionName)
        targetClientDir = targetClientDir1
        val targetVersionJson = File(targetClientDir1, "${info.customVersionName}.json")

        if (checkVersionExists && targetVersionJson.exists()) {
            Logger.debug(TAG, "The game has already been installed!")
            throw GameAlreadyInstalledException()
        }

        val tempGameDir = PathManager.DIR_CACHE_GAME_DOWNLOADER
        val tempMinecraftDir = File(tempGameDir, ".minecraft")
        val tempGameVersionsDir = File(tempMinecraftDir, "versions")
        val tempClientDir = File(tempGameVersionsDir, info.gameVersion)

        val optifineDir = info.optifine?.let { File(tempGameVersionsDir, it.version) }
        val forgeDir = info.forge?.let { File(tempGameVersionsDir, "forge-${it.versionName}") }
        val neoforgeDir = info.neoforge?.let { File(tempGameVersionsDir, "neoforge-${it.versionName}") }
        val fabricDir = info.fabric?.let { File(tempGameVersionsDir, "fabric-loader-${it.version}-${info.gameVersion}") }
        val legacyFabricDir = info.legacyFabric?.let { File(tempGameVersionsDir, "legacy-fabric-loader-${it.version}-${info.gameVersion}") }
        val quiltDir = info.quilt?.let { File(tempGameVersionsDir, "quilt-loader-${it.version}-${info.gameVersion}") }

        val tempModsDir = File(tempGameDir, ".temp_mods")

        return InstallationPathConfig(
            targetClientDir = targetClientDir1,
            tempGameDir = tempGameDir,
            tempMinecraftDir = tempMinecraftDir,
            tempGameVersionsDir = tempGameVersionsDir,
            tempClientDir = tempClientDir,
            tempModsDir = tempModsDir,
            optifineDir = optifineDir,
            forgeDir = forgeDir,
            neoforgeDir = neoforgeDir,
            fabricDir = fabricDir,
            legacyFabricDir = legacyFabricDir,
            quiltDir = quiltDir
        )
    }

    suspend fun getTaskPhase(
        createIsolation: Boolean = true,
        onInstalled: suspend (targetClientDir: File) -> Unit = {},
    ): List<TaskFlowExecutor.TaskPhase> = withContext(Dispatchers.IO) {
        val pathConfig = createPathConfig()

        listOf(
            buildPhase {

                addTask(
                    id = "Download.Game.ClearTemp",
                    title = androidText(R.string.download_install_clear_temp),
                    icon = R.drawable.ic_auto_delete_outlined,
                ) {
                    clearTempGameDir()

                    pathConfig.tempClientDir.createDirAndLog()
                    pathConfig.optifineDir?.createDirAndLog()
                    pathConfig.forgeDir?.createDirAndLog()
                    pathConfig.neoforgeDir?.createDirAndLog()
                    pathConfig.fabricDir?.createDirAndLog()
                    pathConfig.legacyFabricDir?.createDirAndLog()
                    pathConfig.quiltDir?.createDirAndLog()
                    pathConfig.tempModsDir.createDirAndLog()
                }

                addTask(
                    title = androidText(R.string.download_game_install_vanilla, info.gameVersion),
                    task = createMinecraftDownloadTask(info.gameVersion, pathConfig.tempGameVersionsDir)
                )

                addLoaderTasks(
                    tempGameDir = pathConfig.tempGameDir,
                    tempMinecraftDir = pathConfig.tempMinecraftDir,
                    forgeDir = pathConfig.forgeDir,
                    neoforgeDir = pathConfig.neoforgeDir,
                    fabricDir = pathConfig.fabricDir,
                    legacyFabricDir = pathConfig.legacyFabricDir,
                    quiltDir = pathConfig.quiltDir,
                    tempModsDir = pathConfig.tempModsDir
                )

                addTask(
                    title = androidText(R.string.download_game_install_game_files_progress),
                    icon = R.drawable.ic_build_outlined,

                    task = if (
                        pathConfig.optifineDir != null ||
                        pathConfig.forgeDir != null ||
                        pathConfig.neoforgeDir != null ||
                        pathConfig.fabricDir != null ||
                        pathConfig.legacyFabricDir != null ||
                        pathConfig.quiltDir != null ||
                        pathConfig.tempModsDir.listFiles()?.isNotEmpty() == true
                    ) {
                        createGameInstalledTask(
                            tempMinecraftDir = pathConfig.tempMinecraftDir,
                            targetMinecraftDir = targetGameFolder,
                            targetClientDir = pathConfig.targetClientDir,
                            tempClientDir = pathConfig.tempClientDir,
                            tempModsDir = pathConfig.tempModsDir,
                            createIsolation = createIsolation,
                            optiFineFolder = pathConfig.optifineDir,
                            forgeFolder = pathConfig.forgeDir,
                            neoForgeFolder = pathConfig.neoforgeDir,
                            fabricFolder = pathConfig.fabricDir,
                            legacyFabricFolder = pathConfig.legacyFabricDir,
                            quiltFolder = pathConfig.quiltDir,
                            onComplete = {
                                onInstalled(pathConfig.targetClientDir)
                                targetClientDir = null
                            }
                        )
                    } else {

                        createVanillaFilesCopyTask(
                            tempMinecraftDir = pathConfig.tempMinecraftDir,
                            onComplete = {
                                onInstalled(pathConfig.targetClientDir)
                                targetClientDir = null
                            }
                        )
                    }
                )
            }
        )
    }

    private suspend fun getModifyTaskPhase(): List<TaskFlowExecutor.TaskPhase> = withContext(Dispatchers.IO) {
        val pathConfig = createPathConfig(checkVersionExists = false)
        val tempOutputDir = File(PathManager.DIR_CACHE_GAME_DOWNLOADER, "modified/${info.customVersionName}")

        listOf(
            buildPhase {

                addTask(
                    id = "ModifyVersion.ClearTemp",
                    title = androidText(R.string.download_install_clear_temp),
                    icon = R.drawable.ic_auto_delete_outlined,
                ) {
                    clearTempGameDir()

                    pathConfig.tempClientDir.createDirAndLog()
                    pathConfig.optifineDir?.createDirAndLog()
                    pathConfig.forgeDir?.createDirAndLog()
                    pathConfig.neoforgeDir?.createDirAndLog()
                    pathConfig.fabricDir?.createDirAndLog()
                    pathConfig.legacyFabricDir?.createDirAndLog()
                    pathConfig.quiltDir?.createDirAndLog()
                    pathConfig.tempModsDir.createDirAndLog()
                }

                addTask(
                    title = androidText(R.string.download_game_install_vanilla, info.gameVersion),
                    task = createMinecraftDownloadTask(info.gameVersion, pathConfig.tempGameVersionsDir)
                )

                addLoaderTasks(
                    tempGameDir = pathConfig.tempGameDir,
                    tempMinecraftDir = pathConfig.tempMinecraftDir,
                    forgeDir = pathConfig.forgeDir,
                    neoforgeDir = pathConfig.neoforgeDir,
                    fabricDir = pathConfig.fabricDir,
                    legacyFabricDir = pathConfig.legacyFabricDir,
                    quiltDir = pathConfig.quiltDir,
                    tempModsDir = pathConfig.tempModsDir
                )

                addTask(
                    title = androidText(R.string.download_game_install_game_files_progress),
                    icon = R.drawable.ic_build_outlined,
                    task = createVersionModifiedTask(
                        pathConfig = pathConfig,
                        tempOutputDir = tempOutputDir,
                        onComplete = {
                            targetClientDir = null
                        }
                    )
                )
            }
        )
    }

    private fun MutableList<TitledTask>.addLoaderTasks(
        tempGameDir: File,
        tempMinecraftDir: File,
        forgeDir: File?,
        neoforgeDir: File?,
        fabricDir: File?,
        legacyFabricDir: File?,
        quiltDir: File?,
        tempModsDir: File
    ) {

        info.optifine?.let { optifineVersion ->

            val hasFabricLike = fabricDir != null || legacyFabricDir != null || quiltDir != null

            if (!hasFabricLike) {
                val targetInstaller: File = targetTempOptiFineInstaller(tempGameDir)

                addTask(
                    title = androidText(
                        R.string.download_game_install_base_download_file,
                        ModLoader.OPTIFINE.displayName,
                        info.optifine.displayName
                    ),
                    task = getOptiFineDownloadTask(
                        targetTempInstaller = targetInstaller,
                        optifine = optifineVersion
                    )
                )

                addTask(
                    title = androidText(
                        R.string.download_game_install_base_install,
                        ModLoader.OPTIFINE.displayName
                    ),
                    icon = R.drawable.ic_build_outlined,
                    task = getOptiFineInstallTask(
                        downloader = downloader,
                        tempGameDir = tempGameDir,
                        tempMinecraftDir = tempMinecraftDir,
                        tempInstallerJar = targetInstaller,
                        optifineVersion = optifineVersion,
                        hasBootstrapForge = (forgeDir != null || neoforgeDir != null) && isMinecraft17Plus(info.gameVersion),
                        logOutputHolder = logOutputHolder
                    )
                )
            } else {

                addTask(
                    title = androidText(
                        R.string.download_game_install_base_download_file,
                        ModLoader.OPTIFINE.displayName,
                        info.optifine.displayName
                    ),
                    task = getOptiFineModsDownloadTask(
                        optifine = optifineVersion,
                        tempModsDir = tempModsDir
                    )
                )
            }
        }

        info.forge?.let { forgeVersion ->
            createForgeLikeTask(
                forgeLikeVersion = forgeVersion,
                tempGameDir = tempGameDir,
                tempMinecraftDir = tempMinecraftDir,
                tempFolderName = forgeDir!!.name,
                addTask = { title, icon, task ->
                    addTask(title = title, icon = icon, task = task)
                }
            )
        }

        info.neoforge?.let { neoforgeVersion ->
            createForgeLikeTask(
                forgeLikeVersion = neoforgeVersion,
                tempGameDir = tempGameDir,
                tempMinecraftDir = tempMinecraftDir,
                tempFolderName = neoforgeDir!!.name,
                addTask = { title, icon, task ->
                    addTask(title = title, icon = icon, task = task)
                }
            )
        }

        fun addFabricLike(
            version: FabricLikeVersion,
            dirName: String
        ) {
            createFabricLikeTask(
                fabricLikeVersion = version,
                tempMinecraftDir = tempMinecraftDir,
                tempFolderName = dirName,
                addTask = { title, icon, task ->
                    addTask(title = title, icon = icon, task = task)
                }
            )
        }

        fun addMod(
            mod: ModVersion,
            modName: String,
            modVer: String,
        ) {
            addTask(
                title = androidText(
                    R.string.download_game_install_base_download_file,
                    modName, modVer
                ),
                task = createModLikeDownloadTask(
                    tempModsDir = tempModsDir,
                    modVersion = mod
                )
            )
        }

        info.fabric?.let { fabricVersion ->
            addFabricLike(fabricVersion, fabricDir!!.name)
        }
        info.fabricAPI?.let { apiVersion ->
            addMod(
                mod = apiVersion,
                modName = ModLoader.FABRIC_API.displayName,
                modVer = apiVersion.displayName
            )
        }

        info.legacyFabric?.let { fabricVersion ->
            addFabricLike(fabricVersion, legacyFabricDir!!.name)
        }
        info.legacyFabricAPI?.let { apiVersion ->
            addMod(
                mod = apiVersion,
                modName = ModLoader.LEGACY_FABRIC_API.displayName,
                modVer = apiVersion.displayName
            )
        }

        info.quilt?.let { quiltVersion ->
            addFabricLike(quiltVersion, quiltDir!!.name)
        }
        info.quiltAPI?.let { apiVersion ->
            addMod(
                mod = apiVersion,
                modName = ModLoader.QUILT_API.displayName,
                modVer = apiVersion.displayName
            )
        }
    }

    fun cancelInstall(
        clearTarget: Boolean = true
    ) {
        taskExecutor.cancel()

        if (clearTarget) {
            clearTargetClient()
        }

        CoroutineScope(Dispatchers.Main).launch {

            val intent = Intent(GlobalContext.applicationContext, JvmService::class.java)
            GlobalContext.applicationContext.stopService(intent)
            JVMSocketServer.stop()
        }
    }

    private suspend fun clearTempGameDir() = withContext(Dispatchers.IO) {
        PathManager.DIR_CACHE_GAME_DOWNLOADER.takeIf { it.exists() }?.let { folder ->
            FileUtils.deleteQuietly(folder)
            Logger.info(TAG, "Temporary game directory cleared.")
        }
    }

    private fun clearTargetClient() {
        val dirToDelete = targetClientDir
        targetClientDir = null

        CoroutineScope(Dispatchers.IO).launch {

            dirToDelete?.let {

                FileUtils.deleteQuietly(it)
                Logger.info(TAG, "Successfully deleted version directory: ${it.name} at path: ${it.absolutePath}")
            }
        }
    }

    private fun createMinecraftDownloadTask(
        tempClientName: String,
        tempVersionsDir: File
    ): Task {
        val mcDownloader = MinecraftDownloader(
            context = context,
            version = info.gameVersion,
            customName = info.customVersionName,
            downloader = downloader,
            onThrowable = { throw it }
        )

        return mcDownloader.getDownloadTask(tempClientName, tempVersionsDir)
    }

    private fun createForgeLikeTask(
        forgeLikeVersion: ForgeLikeVersion,
        loaderVersion: String = forgeLikeVersion.versionName,
        tempGameDir: File,
        tempMinecraftDir: File,
        tempFolderName: String,
        addTask: (title: AndroidStringText, icon: Int?, task: Task) -> Unit
    ) {

        val (processedInherit, processedLoaderVersion) =
            if (
                !forgeLikeVersion.isNeoForge && loaderVersion.startsWith("1.") && loaderVersion.contains("-")
            ) {
                loaderVersion.substringBefore("-") to loaderVersion.substringAfter("-")
            } else {
                forgeLikeVersion.inherit to loaderVersion
            }

        val tempInstaller = targetTempForgeLikeInstaller(tempGameDir)

        addTask(
            androidText(
                R.string.download_game_install_base_download_file,
                forgeLikeVersion.loaderName,
                processedLoaderVersion
            ),
            null,
            getForgeLikeDownloadTask(tempInstaller, forgeLikeVersion)
        )

        val isNew = forgeLikeVersion is NeoForgeVersion || !forgeLikeVersion.isLegacy

        if (isNew) {
            addTask(
                androidText(
                    R.string.download_game_install_forgelike_analyse,
                    forgeLikeVersion.loaderName
                ),
                R.drawable.ic_build_outlined,
                getForgeLikeAnalyseTask(
                    downloader = downloader,
                    targetTempInstaller = tempInstaller,
                    removeFromDownload = "${forgeLikeVersion.loaderName.lowercase()}-$processedInherit-$loaderVersion",
                    tempMinecraftFolder = tempMinecraftDir,
                    sourceInherit = info.gameVersion,
                    processedInherit = processedInherit,
                )
            )
        }

        addTask(
            androidText(
                R.string.download_game_install_base_install,
                forgeLikeVersion.loaderName
            ),
            R.drawable.ic_build_outlined,
            getForgeLikeInstallTask(
                isNew = isNew,
                downloader = downloader,
                loaderName = forgeLikeVersion.loaderName,
                tempFolderName = tempFolderName,
                tempInstaller = tempInstaller,
                tempGameFolder = tempGameDir,
                tempMinecraftDir = tempMinecraftDir,
                inherit = processedInherit,
                logOutputHolder = logOutputHolder
            )
        )
    }

    private fun createFabricLikeTask(
        fabricLikeVersion: FabricLikeVersion,
        tempMinecraftDir: File,
        tempFolderName: String,
        addTask: (title: AndroidStringText, icon: Int?, task: Task) -> Unit
    ) {
        val tempVersionJson = File(tempMinecraftDir, "versions/$tempFolderName/$tempFolderName.json")

        addTask(
            androidText(
                R.string.download_game_install_base_download_file,
                fabricLikeVersion.loaderName,
                fabricLikeVersion.version
            ),
            null,
            getFabricLikeDownloadTask(
                fabricLikeVersion = fabricLikeVersion,
                tempVersionJson = tempVersionJson
            )
        )

        addTask(
            androidText(
                R.string.download_game_install_forgelike_analyse,
                fabricLikeVersion.loaderName
            ),
            null,
            getFabricLikeCompleterTask(
                downloader = downloader,
                tempMinecraftDir = tempMinecraftDir,
                tempVersionJson = tempVersionJson
            )
        )
    }

    private fun createModLikeDownloadTask(
        tempModsDir: File,
        modVersion: ModVersion
    ) = Task.runTask(
        id = "Download.Mods",
        task = { task ->
            withSpeedReport(
                onSpeedReport = { bytes ->
                    task.updateSpeed(bytes)
                },
                onClear = {
                    task.clearSpeed()
                }
            ) { report ->
                downloadFileFromSources(
                    urls = modVersion.file.url.mapMCIMMirrorUrls(),
                    sha1 = modVersion.file.hashes.sha1,
                    outputFile = File(tempModsDir, modVersion.file.fileName),
                    sizeCallback = report
                )
            }
        }
    )

    private fun createGameInstalledTask(
        tempMinecraftDir: File,
        targetMinecraftDir: File,
        targetClientDir: File,
        tempClientDir: File,
        tempModsDir: File,
        createIsolation: Boolean = true,
        optiFineFolder: File? = null,
        forgeFolder: File? = null,
        neoForgeFolder: File? = null,
        fabricFolder: File? = null,
        legacyFabricFolder: File? = null,
        quiltFolder: File? = null,
        onComplete: suspend () -> Unit = {}
    ) = Task.runTask(
        id = GAME_JSON_MERGER_ID,
        dispatcher = Dispatchers.IO,
        task = { task ->

            task.updateProgress(0.1f)
            mergeGameJson(
                info = info,
                outputFolder = targetClientDir,
                clientFolder = tempClientDir,
                optiFineFolder = optiFineFolder,
                forgeFolder = forgeFolder,
                neoForgeFolder = neoForgeFolder,
                fabricFolder = fabricFolder,
                legacyFabricFolder = legacyFabricFolder,
                quiltFolder = quiltFolder
            )

            copyDirectoryContents(
                File(tempMinecraftDir, "libraries"),
                File(targetMinecraftDir, "libraries"),
                onProgress = { percentage ->
                    task.updateProgress(percentage)
                }
            )

            copyVanillaFiles(
                sourceGameFolder = tempMinecraftDir,
                sourceVersion = info.gameVersion,
                destinationGameFolder = targetGameFolder,
                targetVersion = info.customVersionName
            )

            tempModsDir.listFiles()?.let {
                val targetModsDir = VersionFolders.MOD.getDir(targetClientDir)
                it.forEach { modFile ->
                    val targetMod = File(targetModsDir, modFile.name)
                    if (!targetMod.exists()) {

                        modFile.copyTo(targetMod)
                    }
                }
                if (createIsolation) {

                    VersionConfig.createIsolation(targetClientDir).save()
                }
            }

            task.updateProgress(-1f)
            task.updateMessage(androidText(R.string.download_install_clear_temp))
            clearTempGameDir()

            onComplete()
        }
    )

    private fun createVersionModifiedTask(
        pathConfig: InstallationPathConfig,
        tempOutputDir: File,
        onComplete: suspend () -> Unit = {}
    ) = Task.runTask(
        id = GAME_JSON_MERGER_ID,
        dispatcher = Dispatchers.IO,
        task = { task ->
            val targetClientDir = pathConfig.targetClientDir

            task.updateProgress(0.1f)
            mergeGameJson(
                info = info,
                outputFolder = tempOutputDir,
                clientFolder = pathConfig.tempClientDir,
                optiFineFolder = pathConfig.optifineDir,
                forgeFolder = pathConfig.forgeDir,
                neoForgeFolder = pathConfig.neoforgeDir,
                fabricFolder = pathConfig.fabricDir,
                legacyFabricFolder = pathConfig.legacyFabricDir,
                quiltFolder = pathConfig.quiltDir
            )

            copyDirectoryContents(
                File(pathConfig.tempMinecraftDir, "libraries"),
                File(targetGameFolder, "libraries"),
                onProgress = { percentage ->
                    task.updateProgress(percentage)
                }
            )

            pathConfig.tempModsDir.listFiles()?.forEach { modFile ->
                val targetModsDir = VersionFolders.MOD.getDir(targetClientDir)
                val targetMod = File(targetModsDir, modFile.name)
                if (!targetMod.exists()) {
                    modFile.copyTo(targetMod)
                }
            }

            task.updateProgress(-1f)
            swapVersionFiles(targetClientDir, tempOutputDir)

            task.updateMessage(androidText(R.string.download_install_clear_temp))
            clearTempGameDir()

            onComplete()
        }
    )

    private fun swapVersionFiles(targetClientDir: File, tempOutputDir: File) {
        val targetJson = File(targetClientDir, "${info.customVersionName}.json")
        val targetJar = File(targetClientDir, "${info.customVersionName}.jar")
        val newJson = File(tempOutputDir, "${info.customVersionName}.json")
        val newJar = File(tempOutputDir, "${info.customVersionName}.jar")
        val backupJson = File(PathManager.DIR_CACHE, "modify_${info.customVersionName}_json")
        val backupJar = File(PathManager.DIR_CACHE, "modify_${info.customVersionName}_jar")

        runCatching {
            targetJson.takeIf { it.exists() }?.let {
                backupJson.delete()
                it.copyTo(backupJson)
            }
            targetJar.takeIf { it.exists() }?.let {
                backupJar.delete()
                it.copyTo(backupJar)
            }
        }.onFailure {
            FileUtils.deleteQuietly(backupJson)
            FileUtils.deleteQuietly(backupJar)
            throw it
        }

        try {
            FileUtils.deleteQuietly(targetJson)
            FileUtils.deleteQuietly(targetJar)
            FileUtils.moveFile(newJson, targetJson)
            FileUtils.moveFile(newJar, targetJar)
        } catch (e: Exception) {

            runCatching {
                if (backupJson.exists()) {
                    FileUtils.deleteQuietly(targetJson)
                    FileUtils.moveFile(backupJson, targetJson)
                }
                if (backupJar.exists()) {
                    FileUtils.deleteQuietly(targetJar)
                    FileUtils.moveFile(backupJar, targetJar)
                }
            }.onFailure { restoreError ->
                Logger.error(TAG, "Failed to revert version files: ${restoreError.message}", restoreError)
            }
            throw e
        }

        FileUtils.deleteQuietly(backupJson)
        FileUtils.deleteQuietly(backupJar)
    }

    private fun createVanillaFilesCopyTask(
        tempMinecraftDir: File,
        onComplete: suspend () -> Unit = {}
    ): Task {
        return Task.runTask(
            id = "VanillaFilesCopy",
            task = { task ->

                copyVanillaFiles(
                    sourceGameFolder = tempMinecraftDir,
                    sourceVersion = info.gameVersion,
                    destinationGameFolder = targetGameFolder,
                    targetVersion = info.customVersionName
                )

                task.updateProgress(-1f)
                task.updateMessage(androidText(R.string.download_install_clear_temp))
                clearTempGameDir()

                onComplete()
            }
        )
    }

    private fun File.createDirAndLog(): File {
        this.mkdirs()
        Logger.debug(TAG, "Created directory: $this")
        return this
    }
}
