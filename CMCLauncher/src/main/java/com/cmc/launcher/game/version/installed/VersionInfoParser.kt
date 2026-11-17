package com.cmc.launcher.game.version.installed

import com.cmc.launcher.game.version.download.processLibraries
import com.cmc.launcher.game.versioninfo.models.GameManifest
import com.cmc.launcher.game.versioninfo.models.GameManifest.Library
import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.file.child
import java.io.File

class VersionInfoParser(private val version: Version) {
    private var gameManifest: GameManifest? = null
    private var inherit: Boolean? = null
    private var skipIfNotExists: Boolean = false

    fun setManifest(manifest: GameManifest): VersionInfoParser {
        this.gameManifest = manifest
        return this
    }

    fun setInheriting(skipIfNotExists: Boolean = false): VersionInfoParser {
        this.inherit = true
        this.skipIfNotExists = skipIfNotExists
        return this
    }

    fun build(): GameManifest {
        val manifest = gameManifest ?: GSON.fromJson(
            File(version.getVersionPath(), "${version.getVersionName()}.json").readText(),
            GameManifest::class.java
        )

        val inheritsManifest = if (inherit == true && manifest.inheritsFrom != null) {
            val inherits = manifest.inheritsFrom
            File(version.getVersionsFolder()).child(inherits).child("${inherits}.json")
                .let { inheritsFile ->
                    if (skipIfNotExists && !inheritsFile.exists()) null
                    else {
                        GSON.fromJson(inheritsFile.readText(), GameManifest::class.java)
                    }
                }
        } else null

        return getGameManifest(
            gameManifest = manifest,
            inheritsManifest = inheritsManifest
        )
    }
}

private fun getGameManifest(
    gameManifest: GameManifest,
    inheritsManifest: GameManifest?,
): GameManifest {
    var gameManifest0 = gameManifest
    if (inheritsManifest != null && gameManifest0.inheritsFrom != null) {
        mergeManifest(
            from = gameManifest0,
            target = inheritsManifest,
        )

        val inheritLibraryList: MutableList<Library> = ArrayList(inheritsManifest.libraries)
        inheritLibraryList += gameManifest0.libraries
        inheritsManifest.libraries = inheritLibraryList

        if (inheritsManifest.arguments != null && gameManifest0.arguments != null) {
            val totalArgList: MutableList<Any?> = ArrayList(inheritsManifest.arguments.game)

            var nskip = 0
            for (i in 0..<gameManifest0.arguments.game.size) {
                if (nskip > 0) {
                    nskip--
                    continue
                }

                var perCustomArg: Any = gameManifest0.arguments.game[i]
                if (perCustomArg is String) {
                    var perCustomArgStr = perCustomArg

                    if (perCustomArgStr.startsWith("--") && totalArgList.contains(
                            perCustomArgStr
                        )
                    ) {
                        perCustomArg = gameManifest0.arguments.game[i + 1]
                        if (perCustomArg is String) {
                            perCustomArgStr = perCustomArg

                            if (!perCustomArgStr.startsWith("--")) {
                                nskip++
                            }
                        }
                    } else {
                        totalArgList.add(perCustomArgStr)
                    }
                } else if (!totalArgList.contains(perCustomArg)) {
                    totalArgList.add(perCustomArg)
                }
            }

            inheritsManifest.arguments.game = totalArgList
        }

        gameManifest0 = inheritsManifest
    }

    processLibraries { gameManifest0.libraries }

    if (gameManifest0.javaVersion?.majorVersion == 0) {
        gameManifest0.javaVersion.majorVersion = gameManifest0.javaVersion.version
    }

    return uniqueLibraries(gameManifest0)
}

private inline fun <T> mergeField(
    getter: () -> T?,
    setter: (T) -> Unit
) {
    when (val value = getter()) {
        null -> return

        is String -> {
            if (value.isNotEmpty()) {
                setter(value)
            }
        }

        else -> setter(value)
    }
}

private fun mergeManifest(
    from: GameManifest,
    target: GameManifest,
) {
    mergeField(from::getRawAssetIndex, target::setAssetIndex)
    mergeField(from::getAssets, target::setAssets)
    mergeField(from::getId, target::setId)
    mergeField(from::getMainClass, target::setMainClass)
    mergeField(from::getMinecraftArguments, target::setMinecraftArguments)
    mergeField(from::getReleaseTime, target::setReleaseTime)
    mergeField(from::getTime, target::setTime)
    mergeField(from::getType, target::setType)
}
