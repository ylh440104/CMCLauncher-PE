package com.cmc.launcher.game.version.installed.utils

import org.jackhuang.hmcl.util.versioning.GameVersionNumber

fun String.isBiggerVer(other: String): Boolean {
    return GameVersionNumber.compare(this, other) > 0
}

fun String.isBiggerOrEqualVer(other: String): Boolean {
    return GameVersionNumber.compare(this, other) >= 0
}

fun String.isLowerVer(other: String): Boolean {
    return GameVersionNumber.compare(this, other) < 0
}

fun String.isLowerOrEqualVer(other: String): Boolean {
    return GameVersionNumber.compare(this, other) <= 0
}
