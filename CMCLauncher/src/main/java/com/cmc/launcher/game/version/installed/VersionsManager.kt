package com.cmc.launcher.game.version.installed

import com.cmc.launcher.game.path.getGameHome
import com.cmc.launcher.game.path.getVersionsHome
import com.cmc.launcher.game.version.installed.utils.parseJsonToVersionInfo
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "VersionsManager"

object VersionsManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private val listeners: MutableList<suspend () -> Unit> = CopyOnWriteArrayList()

    fun registerListener(listener: suspend () -> Unit) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: suspend () -> Unit) {
        listeners.remove(listener)
    }

    private val _versions = MutableStateFlow<List<Version>>(emptyList())

    val versions = _versions.asStateFlow()

    var gameInfo: CurrentGameInfo? = null
        private set

    private val _currentVersion = MutableStateFlow<Version?>(null)
    val currentVersion = _currentVersion.asStateFlow()

    private var currentJob: Job? = null

    private val _isRefreshing = MutableStateFlow(false)

    val isRefreshing = _isRefreshing.asStateFlow()

    fun isVersionExists(versionName: String, checkJson: Boolean = false): Boolean {
        val folder = File(getVersionsHome(), versionName)

        return if (checkJson) File(folder, "${folder.name}.json").exists()
        else folder.exists()
    }

    fun refresh(tag: String, trySetVersion: String? = null) {
        currentJob?.cancel()
        currentJob = scope.launch {
            mutex.withLock {
                _isRefreshing.update { true }
                Logger.debug(TAG, "Initiated by $tag: starting to refresh the version list.")

                val gameHome = getGameHome()

                if (trySetVersion != null) {
                    saveCurrentVersion(trySetVersion, refresh = false)
                    Logger.debug(TAG, "Has attempted to save the current version: $trySetVersion")
                }

                _versions.update { emptyList() }

                val newVersions = mutableListOf<Version>()
                File(getVersionsHome(gameHome)).listFiles()?.forEach { versionFile ->
                    runCatching {
                        processVersionFile(gameHome, versionFile)
                    }.getOrNull()?.let {
                        newVersions.add(it)
                    }
                }

                _versions.update { newVersions.sortedWith(VersionComparator) }

                gameInfo = refreshCurrentInfo(gameHome)
                Logger.debug(TAG, "Version list refreshed, refreshing the current version now.")
                refreshCurrentVersion()

                listeners.forEach { listener ->
                    runCatching { listener() }.onFailure { e ->
                        Logger.error(TAG, "A version refresh listener threw an exception.", e)
                    }
                }

                VersionViewModelStores.clearStale(newVersions)

                _isRefreshing.update { false }
            }
        }
    }

    suspend fun waitForRefresh() {
        mutex.withLock {}
    }

    private fun processVersionFile(gameHome: String, versionFile: File): Version? {
        val version = loadVersion(gameHome, versionFile.name) ?: return null
        Logger.info(TAG,
            "Identified and added version: ${version.getVersionName()}, " +
                    "Path: (${version.getVersionPath()}), " +
                    "Info: ${version.getVersionInfo()?.getInfoString()}"
        )
        return version
    }

    fun loadVersion(gameHome: String, versionName: String): Version? {
        val versionFile = File(getVersionsHome(gameHome), versionName)
        if (!versionFile.exists() || !versionFile.isDirectory) return null

        var isVersion = false

        val jsonFile = File(versionFile, "${versionFile.name}.json")
        val versionInfo = if (jsonFile.exists() && jsonFile.isFile) {
            parseJsonToVersionInfo(jsonFile)?.also {

                isVersion = true
            }
        } else {
            null
        }

        val versionConfig = VersionConfig.parseConfig(versionFile)

        return Version(
            versionFile.name,
            gameHome,
            versionConfig,
            versionInfo,
            isVersion,
            versionInfo.getVersionType()
        )
    }

    private fun refreshCurrentVersion() {
        val version = run {
            val currentList = _versions.value
            if (currentList.isEmpty()) return@run null

            fun getVersionByFirst(): Version? {
                return currentList.find { it.isValid() }?.apply {

                    saveCurrentVersion(getVersionName(), refresh = false)
                }
            }

            runCatching {
                val versionString = gameInfo!!.version
                currentList.getVersion(versionString) ?: run {
                    Logger.debug(TAG, "Stored version $versionString not found, using the first available version instead.")
                    getVersionByFirst()
                }
            }.onFailure { e ->
                Logger.warning(TAG, "The current version information has not been initialized yet.", e)
            }.getOrElse {
                getVersionByFirst()
            }
        }.also { version ->
            Logger.debug(TAG, "The current version is: ${version?.getVersionName()}")
        }

        _currentVersion.update { version }
    }

    private fun List<Version>.getVersion(name: String?): Version? {
        name?.let { versionName ->
            return find { it.getVersionName() == versionName }?.takeIf { it.isValid() }
        }
        return null
    }

    fun saveVersion(version: Version, refresh: Boolean = true): Boolean {
        if (!version.isValid()) return false
        saveCurrentVersion(version.getVersionName(), refresh)
        return true
    }

    fun saveCurrentVersion(versionName: String, refresh: Boolean = true) {
        runCatching {
            gameInfo!!.apply {
                version = versionName
                saveCurrentInfo(getGameHome())
            }
            if (refresh) {
                Logger.debug(TAG, "Current game info file saved, refreshing the current version now.")
                refreshCurrentVersion()
            }
        }.onFailure { e ->
            Logger.error(TAG, "An exception occurred while saving the currently selected version information.", e)
        }
    }

    fun renameVersion(
        version: Version,
        name: String,
        refresh: Boolean = true
    ) {
        val currentVersionName = _currentVersion.value?.getVersionName()

        val saveToCurrent = version.getVersionName() == currentVersionName

        val versionFolder = version.getVersionPath()
        val renameFolder = File(version.getVersionsFolder(), name)

        FileUtils.deleteQuietly(renameFolder)

        val originalName = versionFolder.name

        versionFolder.renameTo(renameFolder)

        val versionJsonFile = File(renameFolder, "$originalName.json")
        val versionJarFile = File(renameFolder, "$originalName.jar")
        val renameJsonFile = File(renameFolder, "$name.json")
        val renameJarFile = File(renameFolder, "$name.jar")

        versionJsonFile.renameTo(renameJsonFile)
        versionJarFile.renameTo(renameJarFile)

        FileUtils.deleteQuietly(versionFolder)

        if (saveToCurrent) {

            saveCurrentVersion(name, refresh = false)
        }

        if (refresh) refresh("VersionsManager.renameVersion")
    }

    fun copyVersion(version: Version, name: String, copyAllFile: Boolean) {
        val versionsFolder = version.getVersionsFolder()
        val newVersion = File(versionsFolder, name)

        val originalName = version.getVersionName()

        val newJsonFile = File(newVersion, "$name.json")
        val newJarFile = File(newVersion, "$name.jar")

        val originalVersionFolder = version.getVersionPath()
        if (copyAllFile) {

            FileUtils.copyDirectory(originalVersionFolder, newVersion)

            val jsonFile = File(newVersion, "$originalName.json")
            val jarFile = File(newVersion, "$originalName.jar")
            if (jsonFile.exists()) jsonFile.renameTo(newJsonFile)
            if (jarFile.exists()) jarFile.renameTo(newJarFile)
        } else {

            val originalJsonFile = File(originalVersionFolder, "$originalName.json")
            val originalJarFile = File(originalVersionFolder, "$originalName.jar")
            newVersion.mkdirs()

            if (originalJsonFile.exists()) originalJsonFile.copyTo(newJsonFile)

            if (originalJarFile.exists()) originalJarFile.copyTo(newJarFile)
        }

        version.getVersionConfig().copy().let { config ->
            config.setVersionPath(newVersion)
            config.isolationType = SettingState.ENABLE
            config.saveWithThrowable()
        }

        refresh("VersionsManager.copyVersion")
    }

    fun deleteVersion(version: Version) {
        FileUtils.deleteQuietly(version.getVersionPath())
        refresh("VersionsManager.deleteVersion")
    }
}
