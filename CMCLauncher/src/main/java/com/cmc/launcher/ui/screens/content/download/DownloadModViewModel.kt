package com.cmc.launcher.ui.screens.content.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformVersion
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.installed.VersionFolders
import com.cmc.launcher.game.version.mod.InstalledMod
import com.cmc.launcher.game.version.mod.ModFingerprints
import com.cmc.launcher.game.version.mod.matchInstalledMods
import com.cmc.launcher.game.version.mod.scanModFingerprints
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "DownloadModViewModel"

class DownloadModViewModel : ViewModel() {

    var currentPlatform: Platform = AllSettings.searchModPlatform.getValue()
        private set

    var matching by mutableStateOf(false)
        private set

    var installedByProject by mutableStateOf<Map<String, InstalledMod>>(emptyMap())
        private set

    private var installedByVersion by mutableStateOf<Map<String, InstalledMod>>(emptyMap())

    private var scanJob: Job? = null

    private var scannedVersionName: String? = null

    private var scannedFingerprints: List<ModFingerprints> = emptyList()

    private val matchedResults =
        mutableMapOf<Platform, Pair<Map<String, InstalledMod>, Map<String, InstalledMod>>>()

    fun checkProject(platform: Platform, projectId: String): InstalledMod? {
        return installedByProject[projectId]
            ?.takeIf { it.platform == platform && !it.notFound }
    }

    fun checkVersion(version: PlatformVersion): InstalledMod? {
        return installedByVersion[version.platformId()]
            ?.takeIf { it.platform == version.platform() && !it.notFound }
    }

    fun scan(version: Version?) {
        val versionName = version?.getVersionName()
        if (scanJob?.isActive == true && versionName == scannedVersionName) return

        scanJob?.cancel()

        if (versionName != scannedVersionName) {
            installedByProject = emptyMap()
            installedByVersion = emptyMap()
        }

        matchedResults.clear()

        scanJob = viewModelScope.launch {
            matching = true
            try {
                scannedVersionName = versionName
                scannedFingerprints = version?.let { ver ->
                    try {
                        scanModFingerprints(VersionFolders.MOD.getDir(ver.getGameDir()))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Logger.warning(TAG, "Failed to scan local mod fingerprints", e)
                        emptyList()
                    }
                } ?: emptyList()

                applyMatches(currentPlatform)
            } finally {
                matching = false
            }
        }
    }

    fun onPlatformChanged(platform: Platform) {
        if (currentPlatform == platform) return
        currentPlatform = platform

        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            matching = true
            applyMatches(platform)
            matching = false
        }
    }

    private suspend fun applyMatches(platform: Platform) {
        matchedResults[platform]?.let { (byProject, byVersion) ->
            installedByProject = byProject
            installedByVersion = byVersion
            return
        }

        val fingerprints = scannedFingerprints
        if (fingerprints.isEmpty()) {
            installedByProject = emptyMap()
            installedByVersion = emptyMap()
            return
        }

        val byProject = mutableMapOf<String, InstalledMod>()
        val byVersion = mutableMapOf<String, InstalledMod>()

        fun collect(installed: InstalledMod) {
            if (installed.notFound) return
            byProject[installed.projectId] = installed
            byVersion[installed.versionId] = installed
        }

        val matched = matchInstalledMods(fingerprints, platform) { incremental ->
            incremental.byProject.values.forEach(::collect)
            installedByProject = byProject.toMap()
            installedByVersion = byVersion.toMap()
        }

        if (matched.complete) matchedResults[platform] = matched.byProject to matched.byVersion
        installedByProject = matched.byProject
        installedByVersion = matched.byVersion
    }

    override fun onCleared() {
        scanJob?.cancel()
    }
}
