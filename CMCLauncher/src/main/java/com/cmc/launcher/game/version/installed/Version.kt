package com.cmc.launcher.game.version.installed

import android.content.Context
import android.os.Parcelable
import androidx.annotation.Keep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.cmc.launcher.BuildConfig
import com.cmc.launcher.BuildKeys
import com.cmc.launcher.context.GlobalContext
import com.cmc.launcher.game.launch.LogName
import com.cmc.launcher.game.path.getVersionsHome
import com.cmc.launcher.game.support.touch_controller.VibrationHandler
import com.cmc.launcher.path.PathManager
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.setting.unit.getOrMin
import com.cmc.launcher.ui.screens.content.elements.QuickPlay
import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.file.readText
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.platform.getMaxMemoryForSettings
import com.cmc.launcher.utils.string.isNotEmptyOrBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.min

private const val TAG = "Version"

@Keep
@Parcelize
class Version(
    private val versionName: String,
    private val gameHome: String,
    private val versionConfig: VersionConfig,
    private val versionInfo: VersionInfo?,
    private val isValid: Boolean,
    val versionType: VersionType,

    var launchManifest: String? = null,

    var offlineAccountLogin: Boolean = false,

    var quickPlaySingle: QuickPlay? = null,

    var enableTouchProxy: Boolean = false,

    var fpeEmulatorModDetected: Boolean = false,
): Parcelable {

    @IgnoredOnParcel
    var pinnedState by mutableStateOf(versionConfig.pinned)
        private set

    fun setPinnedAndSave(value: Boolean) {
        this.versionConfig.setPinnedAndSave(value) { state ->
            this.pinnedState = state
        }
    }

    fun getGameHome(): String = gameHome

    fun getVersionsFolder(): String = getVersionsHome(gameHome)

    fun getVersionPath(): File = File(getVersionsFolder(), versionName)

    fun getVersionName(): String = versionName

    fun getLauncherVersionPath(): File = File(getVersionPath(), BuildKeys.LAUNCHER_IDENTIFIER)

    fun getLatestLog(): File = File(getLauncherVersionPath(), LogName.GAME.fileName)

    fun getVersionIconFile(): File = File(getLauncherVersionPath(), "VersionIcon.png")

    fun getInheritedClientJar(inheritsFrom: String?): File? =
        inheritsFrom?.let { File(File(getVersionsFolder(), it), "$it.jar") }
            ?.takeIf { jar -> jar.exists() }

    fun getClientJar(): File = File(getVersionPath(), "$versionName.jar")

    fun getVersionConfig() = versionConfig

    fun getVersionInfo() = versionInfo

    fun isSummaryValid(): Boolean {
        val summary = versionConfig.versionSummary
        return summary.isNotEmptyOrBlank()
    }

    fun getVersionSummary(): String {
        if (!isValid()) throw IllegalStateException("The version is invalid!")
        return if (isSummaryValid()) versionConfig.versionSummary else versionInfo!!.getInfoString()
    }

    fun isValid() = isValid && getVersionPath().exists()

    fun isIsolation() = versionConfig.isIsolation()

    fun skipGameIntegrityCheck() = versionConfig.skipGameIntegrityCheck()

    fun getGameDir(): File {
        return if (versionConfig.isIsolation()) getVersionPath()

        else if (versionConfig.customPath.isNotEmpty()) File(versionConfig.customPath)
        else File(gameHome)
    }

    private fun String.getValueOrDefault(default: String): String = this.takeIf { it.isNotEmpty() } ?: default

    fun getRenderer(): String = versionConfig.renderer.getValueOrDefault(AllSettings.renderer.getValue())

    fun isSfpewPluginLayerEnabled(): Boolean = versionConfig.isSfpewPluginLayerEnabled()

    fun isSfpewRelaxedContextEnabled(): Boolean = versionConfig.isSfpewRelaxedContextEnabled()

    fun getDriver(): String = versionConfig.driver.getValueOrDefault(AllSettings.vulkanDriver.getValue())

    fun getGraphicsApi(): GraphicsApi = versionConfig.graphicsApi ?: AllSettings.graphicsApi.getValue()

    fun getControlPath(): File? = versionConfig.control
        .getValueOrDefault(AllSettings.controlLayout.getValue())
        .takeIf { it.isNotEmpty() }
        ?.let { fileName -> File(PathManager.DIR_CONTROL_LAYOUTS, fileName) }

    fun getJavaRuntime(): String = versionConfig.javaRuntime

    fun getJvmArgs(): String = versionConfig.jvmArgs

    fun getGameArgs(): String = versionConfig.gameArgs

    fun getCustomInfo(): String = versionConfig.customInfo.getValueOrDefault(AllSettings.versionCustomInfo.getValue())
        .replace("[zl_version]", BuildConfig.VERSION_NAME)

    fun getServerIp(): String? = versionConfig.serverIp.takeIf { it.isNotEmptyOrBlank() }

    fun getRamAllocation(context: Context = GlobalContext): Int = versionConfig.ramAllocation.takeIf { it >= 256 }?.let {
        min(it, getMaxMemoryForSettings(context))
    } ?: AllSettings.ramAllocation.getOrMin()

    fun getTouchVibrateDuration(): Int? = versionConfig.touchVibrateDuration.takeIf { it >= 80 }

    fun getTouchVibrateKind(): VibrationHandler.VibrateKind = versionConfig.touchVibrateKind ?: VibrationHandler.VibrateKind.default
}

fun getLauncherVersionPath(versionFolder: File): File = File(versionFolder, BuildKeys.LAUNCHER_IDENTIFIER)

fun getVersionIconFile(versionFolder: File): File = File(getLauncherVersionPath(versionFolder), "VersionIcon.png")

private const val VULKAN_RUNTIME_WORLD_VERSION = 4883

suspend fun Version.hasVulkanBackend(): Boolean {
    return withContext(Dispatchers.IO) {
        val clientJar = getClientJar()
        if (!clientJar.exists()) return@withContext false
        runCatching {

            ZipFile(clientJar).use { zip ->
                val worldVersion = zip.getEntry("version.json")
                    ?.readText(zip)
                    ?.let { GSON.fromJson(it, JsonObject::class.java) }

                    ?.get("world_version")?.asInt
                worldVersion != null && worldVersion >= VULKAN_RUNTIME_WORLD_VERSION
            }
        }.onFailure { e ->
            Logger.warning(TAG, "Unable to determine the data version of this client Jar, possibly due to an outdated version.", e)
        }.getOrDefault(false)
    }
}
