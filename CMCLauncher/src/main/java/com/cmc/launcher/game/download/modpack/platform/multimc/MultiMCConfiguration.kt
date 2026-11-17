package com.cmc.launcher.game.download.modpack.platform.multimc

import java.io.File
import java.util.Optional
import java.util.Properties

data class MultiMCConfiguration(
    val instanceType: String?,
    val name: String?,
    val gameVersion: String?,
    val permGen: Int?,
    val wrapperCommand: String?,
    val preLaunchCommand: String?,
    val postExitCommand: String?,
    val notes: String?,
    val javaPath: String?,
    val jvmArgs: String?,
    val isFullscreen: Boolean,
    val width: Int?,
    val height: Int?,
    val maxMemory: Int?,
    val minMemory: Int?,
    val joinServerOnLaunch: String?,
    val isShowConsole: Boolean,
    val isShowConsoleOnError: Boolean,
    val isAutoCloseConsole: Boolean,
    val isOverrideMemory: Boolean,
    val isOverrideJavaLocation: Boolean,
    val isOverrideJavaArgs: Boolean,
    val isOverrideConsole: Boolean,
    val isOverrideCommands: Boolean,
    val isOverrideWindow: Boolean,
    val iconKey: String?
) {

    constructor(
        properties: Properties,
        instanceName: String? = null,
        gameVersion: String? = null
    ): this(
        instanceType = readValue(properties, "InstanceType"),
        isAutoCloseConsole = readValue(properties, "AutoCloseConsole").toBoolean(),
        gameVersion = gameVersion ?: readValue(properties, "IntendedVersion"),
        javaPath = readValue(properties, "JavaPath"),
        jvmArgs = readValue(properties, "JvmArgs"),
        isFullscreen = readValue(properties, "LaunchMaximized").toBoolean(),
        maxMemory = readValue(properties, "MaxMemAlloc")?.toIntOrNull(),
        minMemory = readValue(properties, "MinMemAlloc")?.toIntOrNull(),
        joinServerOnLaunch = readValue(properties, "JoinServerOnLaunchAddress"),
        height = readValue(properties, "MinecraftWinHeight")?.toIntOrNull(),
        width = readValue(properties, "MinecraftWinWidth")?.toIntOrNull(),
        isOverrideCommands = readValue(properties, "OverrideCommands").toBoolean(),
        isOverrideConsole = readValue(properties, "OverrideConsole").toBoolean(),
        isOverrideJavaArgs = readValue(properties, "OverrideJavaArgs").toBoolean(),
        isOverrideJavaLocation = readValue(properties, "OverrideJavaLocation").toBoolean(),
        isOverrideMemory = readValue(properties, "OverrideMemory").toBoolean(),
        isOverrideWindow = readValue(properties, "OverrideWindow").toBoolean(),
        permGen = readValue(properties, "PermGen")?.toIntOrNull(),
        postExitCommand = readValue(properties, "PostExitCommand"),
        preLaunchCommand = readValue(properties, "PreLaunchCommand"),
        isShowConsole = readValue(properties, "ShowConsole").toBoolean(),
        isShowConsoleOnError = readValue(properties, "ShowConsoleOnError").toBoolean(),
        wrapperCommand = readValue(properties, "WrapperCommand"),
        name = instanceName ?: readValue(properties, "name"),
        notes = Optional.ofNullable<String?>(readValue(properties, "notes")).orElse(""),
        iconKey = readValue(properties, "iconKey")
    )
}

private fun readValue(properties: Properties, key: String?): String? {
    val value = properties.getProperty(key) ?: return null

    val l = value.length
    if (l >= 2 && value[0] == '"' && value[l - 1] == ':') {
        return value.take(l - 1)
    }
    return value
}

fun loadMMCConfigFromPack(root: File): MultiMCConfiguration? {

    val configuration = File(root, "instance.cfg")
    return if (configuration.exists() && configuration.isFile) {
        val properties = Properties()
        configuration.reader(Charsets.UTF_8).use { isr ->
            properties.load(isr)
        }
        MultiMCConfiguration(properties = properties)
    } else {
        null
    }
}
