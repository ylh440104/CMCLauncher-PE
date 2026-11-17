package com.cmc.launcher.game.version.mod.update

class ModUpdateCancelledException : RuntimeException("Update cancelled by user")

class NoModUpdatesAvailableException : RuntimeException("All mods are already up to date")
