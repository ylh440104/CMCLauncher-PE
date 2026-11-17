package com.cmc.launcher.viewmodel

import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import com.cmc.launcher.game.plugin.driver.Driver
import com.cmc.launcher.game.plugin.driver.DriverPluginManager
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.installed.utils.isLowerVer
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.setting.launcherMMKV
import com.cmc.launcher.ui.vulkan_checker.VCOperation
import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.device.VulkanCapabilities
import com.cmc.launcher.utils.device.VulkanChecker
import com.cmc.launcher.utils.device.VulkanRequirements
import com.cmc.launcher.utils.device.normalizeMcVersion
import com.cmc.launcher.utils.device.profileSupport
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

private const val TAG = "VulkanCheckerViewModel"
private const val KEY_VULKAN_CHECK_RECORD = "vulkanCheckRecord"

@Keep
data class VulkanCheckRecord(
    val useTurnip: Boolean,
    val driverPath: String,

    val supportedRanges: List<Range> = emptyList(),
    val version: Int = 0,
) {
    @Keep
    data class Range(
        val since: String,
        val until: String?
    )

    fun isSupported(mcVersion: String): Boolean {
        return supportedRanges.any { range ->
            !mcVersion.isLowerVer(range.since) &&
                    (range.until == null || mcVersion.isLowerVer(range.until))
        }
    }
}

class VulkanCheckerViewModel: ViewModel() {
    private val _vcOperation = MutableStateFlow<VCOperation>(VCOperation.None)
    val vcOperation = _vcOperation.asStateFlow()

    private val mutex = Mutex()

    var vulkanCheckerCont: (Continuation<Unit>)? = null
        private set

    fun changeOperation(operation: VCOperation) {
        _vcOperation.update { operation }
    }

    suspend fun waitForVulkanChecker(version: Version) {
        suspendCancellableCoroutine { cont ->
            vulkanCheckerCont = cont
            changeOperation(VCOperation.Tip(version))

            cont.invokeOnCancellation {
                vulkanCheckerCont = null
            }
        }
    }

    fun resumeCont() {
        vulkanCheckerCont?.resume(Unit)
        vulkanCheckerCont = null
    }

    suspend fun check(version: Version): Pair<VulkanCapabilities?, Boolean> {
        return mutex.withLock {
            val driver = DriverPluginManager.getDriver(version.getDriver())
            val useTurnip = !driver.isLauncher
            val capabilities = doCheck(useTurnip, driver)
            saveRecord(
                VulkanCheckRecord(
                    useTurnip = useTurnip,
                    driverPath = driverPath(useTurnip, driver),
                    supportedRanges = capabilities?.profileSupport()
                        ?.filter { it.supported }
                        ?.map { VulkanCheckRecord.Range(since = it.since, until = it.until) }
                        ?: emptyList(),
                    version = VulkanRequirements.VULKAN_REQUIREMENTS_VERSION
                )
            )
            capabilities to useTurnip
        }
    }

    suspend fun ensureSupported(version: Version): Boolean {
        val driver = DriverPluginManager.getDriver(version.getDriver())
        val useTurnip = !driver.isLauncher
        val mcVersion = version.getVersionInfo()?.minecraftVersion?.let(::normalizeMcVersion)
        val path = driverPath(useTurnip, driver)

        loadRecord()?.takeIf { last ->

            last.version == VulkanRequirements.VULKAN_REQUIREMENTS_VERSION &&

            last.useTurnip == useTurnip && last.driverPath == path
        }?.let {

            return mcVersion != null && it.isSupported(mcVersion)
        }

        waitForVulkanChecker(version)
        val record = loadRecord() ?: return false
        return mcVersion != null && record.isSupported(mcVersion)
    }

    private fun driverPath(useTurnip: Boolean, driver: Driver): String {
        return if (useTurnip) driver.path else ""
    }

    private suspend fun doCheck(useTurnip: Boolean, driver: Driver): VulkanCapabilities? {
        return withContext(Dispatchers.IO) {
            if (useTurnip) {
                val tempDir = File(PathManager.DIR_CACHE, "vulkan_temp")
                VulkanChecker.checkCapabilities(null, driver.path, tempDir.absolutePath)
            } else {
                VulkanChecker.checkCapabilities(null, null, null)
            }
        }
    }

    private fun loadRecord(): VulkanCheckRecord? {
        val json = launcherMMKV().getString(KEY_VULKAN_CHECK_RECORD, null) ?: return null
        return runCatching {
            GSON.fromJson(json, VulkanCheckRecord::class.java)
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to read vulkan check record", e)
        }.getOrNull()
    }

    private fun saveRecord(record: VulkanCheckRecord) {
        runCatching {
            launcherMMKV()
                .putString(KEY_VULKAN_CHECK_RECORD, GSON.toJson(record))
                .apply()
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to save vulkan check record", e)
        }
    }
}
