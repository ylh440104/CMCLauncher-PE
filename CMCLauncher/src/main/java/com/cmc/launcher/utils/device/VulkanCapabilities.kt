package com.cmc.launcher.utils.device

import androidx.annotation.Keep
import com.cmc.launcher.utils.logging.Logger
import org.apache.commons.io.FileUtils
import java.io.File

private const val TAG = "VulkanCapabilities"

@Keep
data class VulkanCapabilities(
    val apiVersionMajor: Int,
    val apiVersionMinor: Int,
    val apiVersionPatch: Int,
    val extensions: List<String>,
    val features: Map<String, Boolean>
) {

    val versionString: String
        get() = "$apiVersionMajor.$apiVersionMinor.$apiVersionPatch"

    val isVersionSupported: Boolean
        get() = apiVersionMajor > 1 || (apiVersionMajor == 1 && apiVersionMinor >= 2)
}

@Keep
fun interface VulkanLogCallback {
    fun log(level: String, message: String)
}

@Keep
object VulkanChecker {
    init {
        try {
            System.loadLibrary("vulkan_check")
            nativeSetLogCallback { level, msg ->
                when (level) {
                    "INFO" -> Logger.info(TAG, msg)
                    "WARN" -> Logger.warning(TAG, msg)
                    "ERROR" -> Logger.error(TAG, msg)
                    else -> Logger.debug(TAG, msg)
                }
            }
        } catch (e: UnsatisfiedLinkError) {
            Logger.error(TAG, "Failed to load vulkan_check library", e)
        }
    }

    fun checkCapabilities(
        driverPath: String?,
        nativeDir: String?,
        cacheDir: String?
    ): VulkanCapabilities? {
        return try {
            nativeCheckVulkan(
                driverPath = driverPath,
                nativeDir = nativeDir,
                cacheDir = cacheDir,
            )?.also { caps ->
                Logger.info(TAG, "Vulkan version: ${caps.versionString}")
                Logger.info(TAG, "Version >= 1.2: ${caps.isVersionSupported}")
                caps.profileSupport().forEach { profile ->
                    Logger.info(
                        TAG,
                        "Minecraft ${profile.versionRangeText} Vulkan supported: ${profile.supported}"
                    )
                    if (!profile.supported) {
                        val support = caps.supportFor(profile.since)
                        val missing = (support.missingRequired + support.missingOptional)
                            .joinToString { it.dependency.name }
                        Logger.warning(
                            TAG,
                            "Minecraft ${profile.versionRangeText} missing: $missing"
                        )
                    }
                }
            }
        } catch (e: UnsatisfiedLinkError) {
            Logger.error(TAG, "Native library or method not found", e)
            null
        } catch (e: Exception) {
            Logger.error(TAG, "Native check failed", e)
            null
        } finally {
            if (nativeDir != null && cacheDir != null) {
                FileUtils.deleteQuietly(File(cacheDir))
            }
        }
    }

    @Keep
    @JvmStatic
    private external fun nativeSetLogCallback(callback: VulkanLogCallback)

    @Keep
    @JvmStatic
    private external fun nativeCheckVulkan(
        driverPath: String?,
        nativeDir: String?,
        cacheDir: String?
    ): VulkanCapabilities?
}
