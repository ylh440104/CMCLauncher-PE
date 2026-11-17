package com.cmc.launcher.game.path

import android.content.Context
import com.cmc.launcher.database.AppDatabase
import com.cmc.launcher.game.version.installed.VersionsManager
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.setting.AllSettings.currentGamePathId
import com.cmc.launcher.utils.canHandlePermission
import com.cmc.launcher.utils.hasStoragePermission
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.exists

private const val TAG = "GamePathManager"

object GamePathManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private val defaultGamePath = File(PathManager.DIR_FILES_EXTERNAL, ".minecraft").absolutePath

    const val DEFAULT_ID = "default"

    private val _gamePathData = MutableStateFlow<List<GamePath>>(listOf())
    val gamePathData = _gamePathData.asStateFlow()

    private val _currentPath = MutableStateFlow(defaultGamePath)

    val currentPath = _currentPath.asStateFlow()

    fun getDefaultPath(): String = defaultGamePath

    fun getUserPath(): String = File(_currentPath.value).parentFile!!.absolutePath

    private lateinit var database: AppDatabase
    private lateinit var gamePathDao: GamePathDao

    fun initialize(context: Context) {
        database = AppDatabase.getInstance(context)
        gamePathDao = database.gamePathDao()
    }

    fun reloadPath() {
        scope.launch {
            mutex.withLock {
                _gamePathData.update { emptyList() }

                val newValue = mutableListOf<GamePath>()

                newValue.add(0, GamePath(DEFAULT_ID, "", defaultGamePath))

                run parseConfig@{

                    val paths = gamePathDao.getAllPaths()
                    newValue.addAll(paths.sortedBy { it.title })
                }

                _gamePathData.update { newValue }

                if (canHandlePermission &&  !hasStoragePermission) {
                    _currentPath.update { defaultGamePath }
                    saveDefaultPath(false)
                } else {
                    refreshCurrentPath(false)
                }

                VersionsManager.refresh("GamePathManager.reloadPath")
                Logger.info(TAG, "Loaded ${_gamePathData.value.size} game paths")
            }
        }
    }

    suspend fun waitForRefresh() {
        mutex.withLock {}
    }

    private fun String.createNoMediaFile() {
        runCatching {
            val parent = Paths.get(this)
            Files.createDirectories(parent)
            val noMediaFile = parent.resolve(".nomedia")
            if (!noMediaFile.exists()) Files.createFile(noMediaFile)
        }.onFailure { e ->
            Logger.error(TAG, "Failed to create .nomedia file in $this", e)
        }
    }

    fun containsId(id: String): Boolean = _gamePathData.value.any { it.id == id }

    fun containsPath(path: String): Boolean = _gamePathData.value.any { it.path == path }

    fun modifyTitle(path: GamePath, modifiedTitle: String) {
        if (!containsId(path.id)) throw IllegalArgumentException("Item with ID ${path.id} not found, unable to rename.")
        path.title = modifiedTitle
        savePath(path)
    }

    fun addNewPath(title: String, path: String) {
        if (containsPath(path)) throw IllegalArgumentException("The path conflicts with an existing item!")
        savePath(
            GamePath(id = generateUUID(), title = title, path = path)
        )
    }

    fun removePath(path: GamePath) {
        if (!containsId(path.id)) return
        deletePath(path)
    }

    fun saveDefaultPath(reloadVersions: Boolean = true) {
        saveCurrentPathUncheck(DEFAULT_ID, reloadVersions)
    }

    fun saveCurrentPath(id: String, reloadVersions: Boolean = true) {
        if (canHandlePermission && !hasStoragePermission) throw IllegalStateException("Storage permissions are not granted")
        if (!containsId(id)) throw IllegalArgumentException("No match found!")
        saveCurrentPathUncheck(id, reloadVersions)
    }

    private fun saveCurrentPathUncheck(id: String, reloadVersions: Boolean) {
        if (currentGamePathId.getValue() == id) return
        currentGamePathId.save(id)
        refreshCurrentPath(reloadVersions)
    }

    private fun refreshCurrentPath(reloadVersions: Boolean) {
        val id = currentGamePathId.getValue()
        _gamePathData.value.find { it.id == id }?.let { item ->
            if (_currentPath.value == item.path) return
            val path = item.path
            _currentPath.update { path }
            path.createNoMediaFile()
            if (reloadVersions) {
                VersionsManager.refresh("GamePathManager.refreshCurrentPath")
            }
        } ?: saveCurrentPath(DEFAULT_ID, reloadVersions)
    }

    private fun generateUUID(): String {
        val uuid = UUID.randomUUID().toString()
        return if (containsId(uuid)) generateUUID()
        else uuid
    }

    private fun savePath(path: GamePath) {
        scope.launch {
            runCatching {
                gamePathDao.savePath(path)
                Logger.info(TAG, "Saved game path: ${path.path}")
            }.onFailure { e ->
                Logger.error(TAG, "Failed to save game path config!", e)
            }
            reloadPath()
        }
    }

    private fun deletePath(path: GamePath) {
        scope.launch {
            gamePathDao.deletePath(path)
            reloadPath()
        }
    }
}
