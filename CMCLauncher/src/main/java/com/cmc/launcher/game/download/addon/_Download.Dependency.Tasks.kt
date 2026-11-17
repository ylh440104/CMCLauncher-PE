package com.cmc.launcher.game.download.addon

import com.cmc.launcher.R
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.coroutine.TaskSystem
import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.download.addon.platform.PlatformDependencyType
import com.cmc.launcher.game.download.addon.platform.PlatformDisplayLabel
import com.cmc.launcher.game.download.addon.platform.PlatformProject
import com.cmc.launcher.game.download.addon.platform.PlatformVersion
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeModLoader
import com.cmc.launcher.game.download.addon.platform.getProjectByVersion
import com.cmc.launcher.game.download.addon.platform.getVersionById
import com.cmc.launcher.game.download.addon.platform.getVersions
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthModLoaderCategory
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.installed.VersionFolders
import com.cmc.launcher.game.version.mod.matchInstalledMods
import com.cmc.launcher.game.version.mod.scanModFingerprints
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.ui.screens.content.download.addon.elements.initAll
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.viewmodel.ErrorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "DownloadDependency"

private const val MAX_DEPENDENCY_PROJECTS = 64

private const val DEPENDENCY_PARALLELISM = 4

class DependencyRequest(
    val platform: Platform,
    val projectId: String,
    val versionId: String?,
    val classes: PlatformClasses,
    val projectTitle: String
)

fun downloadDependenciesForVersions(
    requests: List<DependencyRequest>,
    versions: List<Version>,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    if (requests.isEmpty() || versions.isEmpty()) return

    val distinctRequests = requests.distinctBy { "${it.platform.name}/${it.projectId}/${it.versionId.orEmpty()}" }
    val taskId = distinctRequests
        .map { "${it.projectId}@${it.versionId.orEmpty()}" }
        .sorted()
        .joinToString(",")
        .hashCode()

    TaskSystem.submitTask(
        Task.runTask(
            id = "dependency/${distinctRequests.first().platform.name}/$taskId",
            task = { task ->
                task.updateMessage(
                    androidText(R.string.download_addon_loading_dep_project, distinctRequests.first().projectId)
                )

                val context = DependencyContext(
                    task = task,
                    semaphore = Semaphore(DEPENDENCY_PARALLELISM),
                    processed = ConcurrentHashMap<String, MutableSet<String>>(),
                    projectCache = ConcurrentHashMap<String, PlatformProject>(),
                    budget = AtomicInteger(MAX_DEPENDENCY_PROJECTS),
                    downloadGroups = ConcurrentHashMap<String, DownloadGroup>(),
                    failures = DependencyFailures()
                )

                coroutineScope {
                    distinctRequests.forEach { request ->
                        async { expand(request, versions, context) }
                    }
                }

                context.publishDownloads(submitError)
                context.failures.submit(submitError)
            }
        )
    )
}

private class DependencyContext(
    val task: Task,
    val semaphore: Semaphore,

    val processed: ConcurrentHashMap<String, MutableSet<String>>,

    val projectCache: ConcurrentHashMap<String, PlatformProject>,

    val budget: AtomicInteger,

    val downloadGroups: ConcurrentHashMap<String, DownloadGroup>,
    val failures: DependencyFailures
) {

    val installedMemo = ConcurrentHashMap<String, Set<String>>()
    val installedMutex = Mutex()

    fun claimTargets(key: String, targets: List<Version>): List<Version> {
        val claimed = processed[key] ?: run {
            if (budget.getAndDecrement() <= 0) {
                Logger.warning(TAG, "The dependency limit of $MAX_DEPENDENCY_PROJECTS has been reached, stopping the expansion.")
                return emptyList()
            }
            processed.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }
        }
        return targets.filter { claimed.add(it.getVersionName()) }
    }
}

private class DownloadGroup(
    val version: PlatformVersion,
    val folder: String
) {
    private val targets = mutableListOf<Version>()

    fun addTargets(versions: List<Version>) = synchronized(targets) {
        versions.forEach { if (it !in targets) targets.add(it) }
    }

    fun targetList(): List<Version> = synchronized(targets) { targets.toList() }
}

private class DependencyFailures {
    private val byProject = ConcurrentHashMap<String, MutableList<String>>()

    fun record(title: String, versionName: String) {
        val names = byProject.computeIfAbsent(title) { mutableListOf() }
        synchronized(names) {
            if (!names.contains(versionName)) names.add(versionName)
        }
    }

    fun submit(submitError: (ErrorViewModel.ThrowableMessage) -> Unit) {
        val snapshot = byProject.mapValues { it.value.toList() }.toSortedMap()
        if (snapshot.isEmpty()) return

        snapshot.forEach { (title, versionNames) ->
            Logger.warning(TAG, "No compatible version found for the dependency: $title, ${versionNames.joinToString()}")
        }

        val texts = snapshot.keys.flatMapIndexed { index, title ->
            buildList {
                if (index > 0) add(androidText("\n"))
                add(androidText(R.string.download_addon_dependency_no_compatible_version, title))
            }
        }
        submitError(
            ErrorViewModel.ThrowableMessage(
                title = androidText(R.string.download_addon_install_failed),
                message = androidText(*texts.toTypedArray())
            )
        )
    }
}

private fun projectKey(
    platform: Platform,
    projectId: String
): String = "${platform.name}/$projectId"

private suspend fun <T> resolveOrNull(
    description: String,
    block: suspend () -> T
): T? {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.warning(TAG, description, e)
        null
    }
}

private suspend fun expand(
    request: DependencyRequest,
    targets: List<Version>,
    context: DependencyContext
) {

    val pendingTargets = context.claimTargets(projectKey(request.platform, request.projectId), targets)
    if (pendingTargets.isEmpty()) return

    try {
        val folder = request.classes.versionFolder
        if (folder == VersionFolders.NONE) {

            pendingTargets.forEach {
                context.failures.record(request.projectTitle, it.getVersionName())
            }
            return
        }

        val installTargets = if (request.classes == PlatformClasses.MOD) {
            pendingTargets.filterNot { context.isInstalled(request, it) }
        } else {
            pendingTargets
        }
        if (installTargets.isEmpty()) return

        val resolved = context.semaphore.withPermit {
            context.resolve(request, installTargets)
        }
        resolved.forEach { (version, group) ->
            context.registerDownload(request, version, group)
        }

        resolved.forEach { (version, group) ->
            val dependencies = resolveOrNull("Failed to read the dependencies of: ${request.projectTitle}") {
                version.platformDependencies()
            } ?: return@forEach
            val required = dependencies.filter { it.type == PlatformDependencyType.REQUIRED }
            if (required.isEmpty()) return@forEach

            coroutineScope {
                required.forEach { dependency ->
                    val child = context.expandChild(dependency, request) ?: return@forEach
                    async { expand(child, group, context) }
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.warning(TAG, "An error occurred while installing the dependency: ${request.projectTitle}", e)
        pendingTargets.forEach {
            context.failures.record(request.projectTitle, it.getVersionName())
        }
    }
}

private suspend fun DependencyContext.resolve(
    request: DependencyRequest,
    targets: List<Version>
): List<Pair<PlatformVersion, List<Version>>> {
    task.updateMessage(
        androidText(R.string.download_addon_loading_dep_project, request.projectId)
    )

    if (request.versionId != null) {

        val version = resolveOrNull("Failed to retrieve the dependency version: ${request.versionId}") {
            getVersionById(
                versionId = request.versionId,
                platform = request.platform,
                printLog = false
            )
        }
        if (version == null || !version.initFile(request.projectId)) {
            targets.forEach {
                failures.record(request.projectTitle, it.getVersionName())
            }
            return emptyList()
        }
        return listOf(version to targets)
    }

    val allVersions = resolveOrNull("Failed to retrieve the dependency versions: ${request.projectId}") {
        getVersions(
            projectID = request.projectId,
            platform = request.platform
        ).initAll(request.projectId)
    }
    if (allVersions == null) {
        targets.forEach {
            failures.record(request.projectTitle, it.getVersionName())
        }
        return emptyList()
    }

    return targets
        .groupBy { target -> allVersions.firstOrNull { it.isCompatibleWith(target, request.classes) } }
        .mapNotNull { (version, group) ->
            if (version == null) {
                group.forEach {
                    failures.record(request.projectTitle, it.getVersionName())
                }
                null
            } else {
                version to group
            }
        }
}

private suspend fun DependencyContext.expandChild(
    dependency: PlatformVersion.PlatformDependency,
    parent: DependencyRequest
): DependencyRequest? = semaphore.withPermit {
    val projectId = dependency.projectId ?: run {
        val versionId = dependency.versionId ?: return@withPermit null
        resolveOrNull("Failed to resolve the project of the dependency version: $versionId") {
            getVersionById(versionId, dependency.platform, printLog = false).platformProjectId()
        } ?: return@withPermit null
    }

    val (classes, title) = resolveProject(dependency.platform, projectId, parent.classes)
    if (classes.versionFolder == VersionFolders.NONE) return@withPermit null

    DependencyRequest(
        platform = dependency.platform,
        projectId = projectId,
        versionId = dependency.versionId,
        classes = classes,
        projectTitle = title
    )
}

private suspend fun DependencyContext.resolveProject(
    platform: Platform,
    projectId: String,
    fallbackClasses: PlatformClasses
): Pair<PlatformClasses, String> {
    projectCache[projectId]?.let { project ->
        return project.platformClasses(fallbackClasses) to project.platformTitle()
    }

    val project = resolveOrNull("Failed to retrieve the dependency project information: $projectId") {
        getProjectByVersion(projectId, platform, printLog = false)
    } ?: return fallbackClasses to projectId

    projectCache[projectId] = project
    return project.platformClasses(fallbackClasses) to project.platformTitle()
}

private fun DependencyContext.registerDownload(
    request: DependencyRequest,
    version: PlatformVersion,
    targets: List<Version>
) {
    val key = "${version.platform().name}/${version.platformId()}"
    downloadGroups.computeIfAbsent(key) {
        DownloadGroup(version, request.classes.versionFolder.folderName)
    }.addTargets(targets)
}

private fun DependencyContext.publishDownloads(
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    downloadGroups.toSortedMap().values.forEach { group ->
        val targets = group.targetList()
        if (targets.isEmpty()) return@forEach
        downloadSingleForVersions(
            version = group.version,
            versions = targets,
            folder = group.folder,
            submitError = submitError
        )
    }
}

private suspend fun DependencyContext.isInstalled(
    request: DependencyRequest,
    target: Version
): Boolean {
    val key = "${request.platform.name}/${target.getVersionName()}"
    installedMemo[key]?.let { return request.projectId in it }

    val projects = installedMutex.withLock {
        installedMemo[key] ?: resolveOrNull("Failed to check whether the dependency is installed locally: ${request.projectTitle}") {
            val modsDir = VersionFolders.MOD.getDir(target.getGameDir())
            matchInstalledMods(
                fingerprints = scanModFingerprints(modsDir),
                platform = request.platform
            ).byProject.keys.toSet()
        }?.also { installedMemo[key] = it }
    }
    return projects?.contains(request.projectId) == true
}

private fun PlatformVersion.isCompatibleWith(
    target: Version,
    classes: PlatformClasses
): Boolean {
    val info = target.getVersionInfo() ?: return true
    if (info.minecraftVersion !in platformGameVersion()) return false

    if (classes != PlatformClasses.MOD) return true

    val targetLoaders = info.loaderInfos.mapNotNull { it.loader.toPlatformLoader(platform()) }
    if (targetLoaders.isEmpty()) return true
    val versionLoaders = platformLoaders()
    return versionLoaders.isEmpty() || targetLoaders.any { it in versionLoaders }
}

private fun ModLoader.toPlatformLoader(platform: Platform): PlatformDisplayLabel? {
    if (displayName.isEmpty()) return null
    return when (platform) {
        Platform.MODRINTH -> ModrinthModLoaderCategory.entries.find {
            it.getDisplayName().equals(displayName, true)
        }
        Platform.CURSEFORGE -> CurseForgeModLoader.entries.find {
            it.getDisplayName().equals(displayName, true)
        }
    }
}
