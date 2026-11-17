package com.cmc.launcher.game.version.saves

import java.io.File

data class SaveData(

    val saveFile: File,

    val isValid: Boolean,

    val levelName: String? = null,

    val levelMCVersion: String? = null,

    val lastPlayed: Long? = null,

    val playTime: Long? = null,

    val gameMode: GameMode? = null,

    val difficulty: Difficulty? = null,

    val difficultyLocked: Boolean? = null,

    val hardcoreMode: Boolean? = null,

    val allowCommands: Boolean? = null,

    val worldSeed: Long? = null
)
