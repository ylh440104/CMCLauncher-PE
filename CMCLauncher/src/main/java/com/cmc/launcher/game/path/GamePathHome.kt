package com.cmc.launcher.game.path

import java.io.File

private fun String.replaceSeparator(): String = this.replace("/", File.separator)

fun getGameHome(): String = GamePathManager.currentPath.value

fun getVersionsHome(gameHome: String): String = "${gameHome}/versions".replaceSeparator()

fun getVersionsHome(): String = getVersionsHome(getGameHome())

fun getLibrariesHome(gameHome: String): String = "${gameHome}/libraries".replaceSeparator()

fun getLibrariesHome(): String = getLibrariesHome(getGameHome())

fun getAssetsHome(gameHome: String): String = "${gameHome}/assets".replaceSeparator()

fun getAssetsHome(): String = getAssetsHome(getGameHome())

fun getResourcesHome(gameHome: String): String = "${gameHome}/resources".replaceSeparator()

fun getResourcesHome(): String = getResourcesHome(getGameHome())
