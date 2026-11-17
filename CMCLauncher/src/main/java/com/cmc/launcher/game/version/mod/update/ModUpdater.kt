package com.cmc.launcher.game.version.mod.update

import com.cmc.launcher.R
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.coroutine.TaskFlowExecutor
import com.cmc.launcher.coroutine.TitledTask
import com.cmc.launcher.coroutine.addTask
import com.cmc.launcher.coroutine.buildPhase
import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.download.addon.platform.getProjectByVersion
import com.cmc.launcher.game.version.mod.DISABLED_MOD_SUFFIX
import com.cmc.launcher.game.version.mod.ModProject
import com.cmc.launcher.game.version.mod.RemoteMod
import com.cmc.launcher.game.version.mod.isDisabled
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "ModUpdater"

class ModUpdater(
    private val mods: List<RemoteMod>,
    private val modsDir: File,
    private val minecraft: String,
    private val modLoader: ModLoader,
    scope: CoroutineScope,
    private val waitForUserConfirm: suspend (List<ModManifest>) -> List<SelectableModManifest>
) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val tasksFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    val dataList: MutableList<ModData> = mutableListOf()

    val allModsUpdate: MutableList<ModManifest> = mutableListOf()

    val finalModsUpdate: MutableList<ModManifest> = mutableListOf()

    fun updateAll(
        isRunning: () -> Unit = {},
        onUpdated: () -> Unit,
        onNoModUpdates: () -> Unit,
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
            onComplete = onUpdated,
            onError = { th ->
                if (th is ModUpdateCancelledException) {

                    onCancelled()
                    return@executePhasesAsync
                }
                if (th is NoModUpdatesAvailableException) {

                    onNoModUpdates()
                    return@executePhasesAsync
                }
                onError(th)
            }
        )
    }

    private suspend fun getTaskPhases() = withContext(Dispatchers.IO) {
        dataList.clear()
        allModsUpdate.clear()
        finalModsUpdate.clear()
        val tempModUpdaterDir = PathManager.DIR_CACHE_MOD_UPDATER

        listOf(
            buildPhase {

                addTask(
                    id = "ModUpdater.ClearTemp",
                    title = androidText(R.string.download_install_clear_temp),
                    icon = R.drawable.ic_auto_delete_outlined
                ) {
                    clearTempModUpdaterDir()

                    tempModUpdaterDir.createDirAndLog()
                }

                addTask(
                    id = "ModUpdater.Filter",
                    title = androidText(R.string.mods_update_task_filter),
                    icon = R.drawable.ic_filter_alt_outlined
                ) { task ->
                    val totalSize = mods.size
                    val needLoad = mutableListOf<RemoteMod>()
                    val readyData = mutableListOf<ModData>()

                    mods.forEachIndexed { index, mod ->
                        val file = mod.localMod.file

                        task.updateProgress((index + 1f) / totalSize)
                        task.updateMessage(androidText(file.nameWithoutExtension))

                        if (!mod.localMod.checkRemote) return@forEachIndexed

                        val modFile = mod.remoteFile
                        val project = mod.projectInfo

                        if (modFile != null && project != null) {
                            readyData += ModData(
                                file = file,
                                modFile = modFile,
                                project = project,
                                mcMod = mod.mcMod
                            )
                        } else {
                            needLoad += mod
                        }
                    }

                    val loadedData = if (needLoad.isNotEmpty()) {
                        needLoad.map { mod ->
                            async(Dispatchers.IO) {
                                loadModData(task, mod)
                            }
                        }.awaitAll()
                            .filterNotNull()
                            .also {
                                task.updateMessage(null)
                            }
                    } else {
                        emptyList()
                    }

                    dataList.addAll(readyData)
                    dataList.addAll(loadedData)
                }

                addTask(
                    id = "ModUpdater.CheckUpdate",
                    title = androidText(R.string.mods_update_task_check_update),
                    icon = R.drawable.ic_list_alt_check_outlined
                ) { task ->

                    val semaphore = Semaphore(5)
                    val completedCount = AtomicInteger(0)
                    val totalSize = dataList.size

                    val updateResults = dataList.map { data ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {

                                val version = data.checkUpdate(minecraft, modLoader)

                                val currentCompleted = completedCount.incrementAndGet()
                                task.updateProgress(currentCompleted.toFloat() / totalSize)
                                task.updateMessage(androidText(data.project.title))

                                if (version != null) data to version else null
                            }
                        }
                    }.awaitAll().filterNotNull()

                    updateResults.forEach { (data, version) ->
                        allModsUpdate.add(ModManifest(data, version))
                    }

                    if (allModsUpdate.isEmpty()) {

                        throw NoModUpdatesAvailableException()
                    }
                }

                addTask(
                    id = "ModUpdater.WaitForUser",
                    title = androidText(R.string.mods_update_task_wait_for_user),
                    icon = R.drawable.ic_schedule_outlined
                ) {
                    val finalList = waitForUserConfirm(allModsUpdate).toFinalList()
                    if (finalList.isEmpty()) {

                        throw ModUpdateCancelledException()
                    }
                    allModsUpdate.clear()
                    finalModsUpdate.addAll(finalList)
                }

                addTask(
                    id = "ModUpdater.UpdateMod",
                    title = androidText(R.string.mods_update_task_download)
                ) { task ->
                    val updater = ModVersionUpdater(
                        mods = finalModsUpdate.allNews(),
                        targetDir = tempModUpdaterDir
                    )
                    updater.startDownload(task)
                }

                addTask(
                    id = " ModUpdater.ReplaceMod",
                    title = androidText(R.string.mods_update_task_replace),
                    icon = R.drawable.ic_build_outlined
                ) { task ->
                    val totalCount = finalModsUpdate.size
                    finalModsUpdate.forEachIndexed { index, entry ->
                        val oldMod = entry.data
                        val newVersion = entry.new

                        val oldFile = oldMod.file
                        val newFileName = newVersion.platformFileName()
                        val cacheFile = File(tempModUpdaterDir, newFileName)

                        task.updateProgress((index + 1).toFloat() / totalCount)
                        task.updateMessage(androidText(oldFile.name))

                        if (modsDir.exists() && oldFile.exists() && cacheFile.exists()) {
                            FileUtils.deleteQuietly(oldFile)
                            val targetName = if (oldFile.isDisabled()) "$newFileName$DISABLED_MOD_SUFFIX" else newFileName
                            val newFile = File(modsDir, targetName)
                            cacheFile.copyTo(target = newFile, overwrite = true)
                        }
                    }
                }

                addTask(
                    id = "ModUpdater.ClearTempEnds",
                    title = androidText(R.string.download_install_clear_temp),
                    icon = R.drawable.ic_auto_delete_outlined
                ) {
                    clearTempModUpdaterDir()
                }
            }
        )
    }

    private suspend fun loadModData(
        task: Task,
        mod: RemoteMod
    ): ModData? {
        val file = mod.localMod.file

        val modFile = mod.remoteFile ?: runCatching {
            task.updateMessage(androidText(
                R.string.mods_update_task_loading, file.name
            ))
            mod.loadRemoteFile()
        }.onFailure {
            Logger.warning(TAG, "Failed to load remote mod version", it)
        }.getOrNull() ?: return null

        val project = mod.projectInfo ?: runCatching {
            task.updateMessage(androidText(
                R.string.mods_update_task_loading, file.name
            ))

            val project = getProjectByVersion(modFile.projectId, modFile.platform)
            ModProject(
                id = project.platformId(),
                platform = project.platform(),
                iconUrl = project.platformIconUrl(),
                title = project.platformTitle(),
                slug = project.platformSlug()
            )
        }.onFailure {
            Logger.warning(TAG, "Failed to load remote project", it)
        }.getOrNull() ?: return null

        return ModData(
            file = file,
            modFile = modFile,
            project = project,
            mcMod = mod.mcMod
        )
    }

    fun cancel() {
        taskExecutor.cancel()
    }

    private suspend fun clearTempModUpdaterDir() = withContext(Dispatchers.IO) {
        PathManager.DIR_CACHE_MOD_UPDATER.takeIf { it.exists() }?.let { folder ->
            FileUtils.deleteQuietly(folder)
            Logger.info(TAG, "Temporary mod updater directory cleared.")
        }
    }

    private fun File.createDirAndLog(): File {
        this.mkdirs()
        Logger.debug(TAG, "Created directory: $this")
        return this
    }
}
