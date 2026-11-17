package com.cmc.launcher.game.plugin.renderer_v2

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.cmc.launcher.R
import com.cmc.launcher.game.plugin.ApkPlugin
import com.cmc.launcher.game.plugin.ApkPluginManager
import com.cmc.launcher.game.plugin.cacheAppIcon
import com.cmc.launcher.game.plugin.renderer_v2.data.RendererConfig
import com.cmc.launcher.game.plugin.renderer_v2.data.resolveNativePaths
import com.cmc.launcher.path.GLOBAL_JSON
import com.cmc.launcher.utils.logging.Logger

object RendererV2PluginManager : ApkPluginManager() {
    private const val TAG = "RendererV2Plugin"
    private val rendererPluginList: MutableList<RendererV2Data> = mutableListOf()

    fun getRendererList(): List<RendererV2Data> = rendererPluginList

    fun clearPlugin() {
        rendererPluginList.clear()
    }

    override fun parseApkPlugin(
        context: Context,
        info: ApplicationInfo,
        loaded: (ApkPlugin) -> Unit
    ) {
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) return
        val metaData = info.metaData ?: return

        val configRes = metaData.getStringRes("fclPlugin_V2") ?: return
        val configString = context.getString(info, configRes) ?: return

        val pm = context.packageManager
        val packageName = info.packageName

        val config = runCatching {
            GLOBAL_JSON.decodeFromString<RendererConfig>(configString)
        }.onFailure { e ->
            Logger.error(TAG, "Failed to parse config JSON from $packageName", e)
        }.getOrNull() ?: return

        val appLabel = info.loadLabel(pm).toString()
        val appVersion = runCatching {
            pm.getPackageInfo(packageName, 0).versionName ?: ""
        }.getOrDefault("")

        rendererPluginList.add(
            RendererV2Data(
                packageName = packageName,
                nativePath = info.nativeLibraryDir,
                summary = context.getString(R.string.settings_renderer_from_plugins, appLabel),
                renderer = config.resolveNativePaths(info.nativeLibraryDir)
            ) { metaString ->
                context.getMetaString(info, metaString)
            }
        )

        runCatching {
            cacheAppIcon(context, info)
            ApkPlugin(
                packageName = packageName,
                appName = appLabel,
                appVersion = appVersion
            )
        }.getOrNull()?.let { loaded(it) }
    }

    private fun Bundle.getStringRes(key: String): Int? {
        return runCatching {
            getInt(key, -1).takeIf { it > 0 }
        }.getOrNull()
    }

    private fun Context.getString(info: ApplicationInfo, path: Int): String? {
        return runCatching {
            packageManager.getResourcesForApplication(info).getString(path)
        }.getOrNull()
    }

    private fun Context.getMetaString(info: ApplicationInfo, key: String): String? {
        return runCatching {
            val metaData = info.metaData ?: return null
            val path = metaData.getStringRes(key) ?: return null
            packageManager.getResourcesForApplication(info).getString(path)
        }.getOrNull()
    }

    fun removeRenderer(failedToLoadList: List<RendererV2Data>) {
        rendererPluginList.removeAll { it in failedToLoadList }
    }
}
