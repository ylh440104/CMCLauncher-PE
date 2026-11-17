package com.cmc.launcher.game.version.saves

import com.cmc.launcher.R

enum class Difficulty(val levelCode: Int, val nameRes: Int) {

    PEACEFUL(0, R.string.saves_manage_difficulty_peaceful),

    EASY(1, R.string.saves_manage_difficulty_easy),

    NORMAL(2, R.string.saves_manage_difficulty_normal),

    HARD(3, R.string.saves_manage_difficulty_hard)
}
