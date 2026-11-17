package com.cmc.launcher.game.version.saves

import com.cmc.launcher.R

enum class GameMode(val levelCode: Int, val nameRes: Int) {

    SURVIVAL(0, R.string.saves_manage_gamemode_survival),

    CREATIVE(1, R.string.saves_manage_gamemode_creative),

    ADVENTURE(2, R.string.saves_manage_gamemode_adventure),

    SPECTATOR(3, R.string.saves_manage_gamemode_spectator)
}
