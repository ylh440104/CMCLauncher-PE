package com.cmc.launcher.game.version.multiplayer.description

import com.cmc.launcher.utils.string.stripColorCodes

data class StringDescription(
    val value: String
): ServerDescription {
    override fun toString(): String {
        return "StringDescription:\n${value.stripColorCodes()}"
    }
}
