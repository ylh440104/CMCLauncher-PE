package com.cmc.launcher.game.download.modpack.platform.modrinth

fun ModrinthManifest.getGameVersion(): String = this.dependencies["minecraft"]!!
