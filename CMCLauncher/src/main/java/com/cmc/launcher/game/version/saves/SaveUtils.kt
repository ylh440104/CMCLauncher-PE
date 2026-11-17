package com.cmc.launcher.game.version.saves

import com.github.steveice10.opennbt.NBTIO
import com.github.steveice10.opennbt.tag.builtin.CompoundTag
import com.cmc.launcher.game.version.installed.utils.isBiggerOrEqualVer
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.nbt.asBooleanNotNull
import com.cmc.launcher.utils.nbt.asCompoundTag
import com.cmc.launcher.utils.nbt.asInt
import com.cmc.launcher.utils.nbt.asLong
import com.cmc.launcher.utils.nbt.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SaveUtils"

fun SaveData.isCompatible(minecraftVersion: String) =
    isValid && levelMCVersion != null && minecraftVersion.isBiggerOrEqualVer(levelMCVersion)

suspend fun parseLevelDatFile(
    saveFile: File,
    levelDatFile: File,
    worldGenDatFile: File? = null
): SaveData = withContext(Dispatchers.IO) {

    runCatching {
        if (!levelDatFile.exists()) error("The ${levelDatFile.absolutePath} file does not exist!")

        val compound = NBTIO.readFile(levelDatFile)
            ?: error("Failed to read the level.dat file as a CompoundTag.")
        val data: CompoundTag = compound.asCompoundTag("Data")
            ?: error("{level.dat} Data entry not found in the NBT structure tree.")

        val levelName = data.asString("LevelName", "")

        val levelMCVersion = data.asCompoundTag("Version")?.asString("Name", null)

        val lastPlayed = data.asLong("LastPlayed", 0) ?: 0

        val playTime = data.asLong("Time", 0) ?: 0

        val gameMode = data.asInt("GameType", 0)
            ?.let { levelCode -> GameMode.entries.find { it.levelCode == levelCode } }

        val difficulty = data.asInt("Difficulty", 2)
            ?.let { levelCode -> Difficulty.entries.find { it.levelCode == levelCode } }

        val difficultyLocked = data.asBooleanNotNull("DifficultyLocked", false)

        val hardcoreMode = data.asBooleanNotNull("hardcore", false)

        val allowCommands = if (data.contains("allowCommands")) {
            data.asBooleanNotNull("allowCommands", false)
        } else {

            gameMode == GameMode.CREATIVE
        }

        val worldSeed = if (worldGenDatFile != null && worldGenDatFile.isFile && worldGenDatFile.exists()) {

            runCatching {
                val worldGenCompound = NBTIO.readFile(worldGenDatFile)
                    ?: error("Failed to read the world_gen_settings.dat file as a CompoundTag.")
                val worldData = worldGenCompound.asCompoundTag("data")
                    ?: error("{world_gen_settings.dat} data entry not found in the NBT structure tree.")

                worldData.asLong("seed", null)
            }.onFailure {
                Logger.warning(TAG, "An exception occurred while reading and parsing the world_gen_settings.dat file (${worldGenDatFile.absolutePath}).", it)
            }.getOrNull()
        } else {
            data.asCompoundTag("WorldGenSettings")
                ?.asLong("seed", null)

                ?: data.asLong("RandomSeed", null)
        }

        SaveData(
            saveFile = saveFile,

            isValid = true,
            levelName = levelName,
            levelMCVersion = levelMCVersion,
            lastPlayed = lastPlayed.takeIf { it != 0L },
            playTime = playTime.takeIf { it != 0L },
            gameMode = gameMode,

            difficulty = if (hardcoreMode) Difficulty.HARD else difficulty,
            difficultyLocked = difficultyLocked,
            hardcoreMode = hardcoreMode,
            allowCommands = allowCommands,
            worldSeed = worldSeed
        )
    }.onFailure {
        Logger.warning(TAG, "An exception occurred while reading and parsing the level.dat file (${levelDatFile.absolutePath}).", it)
    }.getOrElse {

        SaveData(
            saveFile = saveFile,

            isValid = false
        )
    }
}
