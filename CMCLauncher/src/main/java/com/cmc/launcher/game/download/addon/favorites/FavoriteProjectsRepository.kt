package com.cmc.launcher.game.download.addon.favorites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.download.addon.platform.PlatformProject
import com.cmc.launcher.game.download.addon.platform.PlatformSearchData
import com.cmc.launcher.game.download.addon.platform.getProjectByVersion
import com.cmc.launcher.utils.logging.Logger
import io.ktor.client.plugins.ClientRequestException
import io.ktor.server.plugins.NotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class FavoriteKey(
    val platform: Platform,
    val projectId: String
)

data class FavoriteEntry(
    val platform: Platform,
    val project: FavoriteProject,
    val remote: PlatformProject? = null,
    val invalid: Boolean = false
)

object FavoriteProjectsRepository {
    private const val TAG = "FavoriteProjectsRepository"
    private const val REFRESH_CONCURRENCY = 8

    val projects = mutableStateMapOf<FavoriteKey, FavoriteEntry>()

    var initialized by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val mutex = Mutex()

    fun isFavorite(platform: Platform, projectId: String): Boolean {
        if (!initialized) scope.launch { ensureLoaded() }
        return projects.containsKey(FavoriteKey(platform, projectId))
    }

    fun favorite(data: PlatformSearchData, classes: PlatformClasses) {
        scope.launch { saveFavorite(data.platform(), data.toFavoriteProject(classes)) }
    }

    fun favorite(project: PlatformProject, defaultClasses: PlatformClasses) {
        scope.launch { saveFavorite(project.platform(), project.toFavoriteProject(defaultClasses)) }
    }

    fun unfavorite(platform: Platform, projectId: String) {
        scope.launch { removeFavorite(platform, projectId) }
    }

    fun toggle(data: PlatformSearchData, classes: PlatformClasses) {
        scope.launch {
            val platform = data.platform()
            val projectId = data.platformId()
            if (checkFavorite(platform, projectId)) {
                removeFavorite(platform, projectId)
            } else {
                saveFavorite(platform, data.toFavoriteProject(classes))
            }
        }
    }

    fun toggle(project: PlatformProject, defaultClasses: PlatformClasses) {
        scope.launch {
            val platform = project.platform()
            val projectId = project.platformId()
            if (checkFavorite(platform, projectId)) {
                removeFavorite(platform, projectId)
            } else {
                saveFavorite(platform, project.toFavoriteProject(defaultClasses))
            }
        }
    }

    suspend fun ensureLoaded() {
        if (initialized) return
        mutex.withLock {
            if (!initialized) reloadLocked()
        }
    }

    suspend fun reload() = mutex.withLock {
        reloadLocked()
    }

    suspend fun refreshRemote() = coroutineScope {
        val pending = projects.values.toList()
        if (pending.isEmpty()) return@coroutineScope

        val semaphore = Semaphore(REFRESH_CONCURRENCY)
        pending.map { entry ->
            async {
                semaphore.withPermit { refreshEntry(entry) }
            }
        }.awaitAll()
    }

    private suspend fun reloadLocked() {
        val latest = withContext(Dispatchers.IO) { readAll() }
        if (!initialized) {
            initialized = true
            projects.clear()
            projects.putAll(latest)
        } else {
            latest.forEach { (key, entry) ->
                if (!projects.containsKey(key)) projects[key] = entry
            }
            (projects.keys - latest.keys).forEach(projects::remove)
        }
    }

    private suspend fun checkFavorite(platform: Platform, projectId: String): Boolean {
        ensureLoaded()
        return projects.containsKey(FavoriteKey(platform, projectId))
    }

    private suspend fun saveFavorite(platform: Platform, project: FavoriteProject) {
        mutex.withLock {
            if (!initialized) reloadLocked()
            withContext(Dispatchers.IO) {
                favoritesMMKV(platform).encode(project.projectId, project)
            }
            projects[FavoriteKey(platform, project.projectId)] = FavoriteEntry(platform, project)
        }
    }

    private suspend fun removeFavorite(platform: Platform, projectId: String) {
        mutex.withLock {
            if (!initialized) reloadLocked()
            withContext(Dispatchers.IO) {
                favoritesMMKV(platform).remove(projectId)
            }
            projects.remove(FavoriteKey(platform, projectId))
        }
    }

    private suspend fun refreshEntry(entry: FavoriteEntry) {
        val key = FavoriteKey(entry.platform, entry.project.projectId)
        try {
            val remote = getProjectByVersion(
                projectId = entry.project.projectId,
                platform = entry.platform,
                printLog = false
            )

            mutex.withLock {
                projects[key]?.let { current ->
                    if (!remote.platformAvailable()) {

                        markInvalid(key, current)
                    } else {
                        val merged = mergeCache(current.project, remote)
                        projects[key] = current.copy(project = merged, remote = remote, invalid = false)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e.isProjectNotFound()) {

                mutex.withLock {
                    projects[key]?.let { current -> markInvalid(key, current) }
                }
            } else {
                Logger.warning(TAG, "Failed to refresh favorite project: ${key.platform}/${key.projectId}", e)
            }
        }
    }

    private fun markInvalid(key: FavoriteKey, current: FavoriteEntry) {
        if (current.invalid) return
        projects[key] = current.copy(invalid = true)
    }

    private fun Throwable.isProjectNotFound(): Boolean =
        this is NotFoundException || (this is ClientRequestException && response.status.value == 404)

    private suspend fun mergeCache(cached: FavoriteProject, remote: PlatformProject): FavoriteProject {
        val classes = remote.platformClasses(cached.classes)
        val iconUrl = remote.platformIconUrl()
        val title = remote.platformTitle()
        val description = remote.platformSummary()
        val authors = remote.platformAuthors()

        val unchanged = classes == cached.classes &&
                iconUrl == cached.iconUrl &&
                title == cached.title &&
                description == cached.description &&
                authors == cached.authors
        if (unchanged) return cached

        val merged = FavoriteProject(
            projectId = cached.projectId,
            iconUrl = iconUrl,
            title = title,
            description = description,
            authors = authors,
            classes = classes,
            followTime = cached.followTime
        )
        withContext(Dispatchers.IO) {
            favoritesMMKV(remote.platform()).encode(merged.projectId, merged)
        }
        return merged
    }

    private fun readAll(): Map<FavoriteKey, FavoriteEntry> {
        val result = mutableMapOf<FavoriteKey, FavoriteEntry>()
        Platform.entries.forEach { platform ->
            val mmkv = favoritesMMKV(platform)
            mmkv.allKeys()?.forEach { key ->
                runCatching {
                    mmkv.decodeParcelable(key, FavoriteProject::class.java)
                }.getOrNull()?.let { project ->
                    result[FavoriteKey(platform, project.projectId)] = FavoriteEntry(platform, project)
                }
            }
        }
        return result
    }
}

fun PlatformSearchData.toFavoriteProject(classes: PlatformClasses): FavoriteProject = FavoriteProject(
    projectId = platformId(),
    iconUrl = platformIconUrl(),
    title = platformTitle(),
    description = platformDescription(),
    authors = platformAuthors(),
    classes = classes,
    followTime = System.currentTimeMillis()
)

fun PlatformProject.toFavoriteProject(defaultClasses: PlatformClasses): FavoriteProject = FavoriteProject(
    projectId = platformId(),
    iconUrl = platformIconUrl(),
    title = platformTitle(),
    description = platformSummary(),
    authors = platformAuthors(),
    classes = platformClasses(defaultClasses),
    followTime = System.currentTimeMillis()
)
