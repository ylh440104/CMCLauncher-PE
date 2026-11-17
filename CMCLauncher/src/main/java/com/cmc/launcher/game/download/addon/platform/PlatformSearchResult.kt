package com.cmc.launcher.game.download.addon.platform

import com.cmc.launcher.game.download.addon.utils.getTranslations
import com.cmc.launcher.ui.screens.content.download.addon.elements.AddonPage
import com.cmc.launcher.utils.string.LevCalculator
import com.cmc.launcher.utils.string.containsChinese
import kotlin.math.max

private const val CONTAIN_CHINESE_WEIGHT = 10

interface PlatformSearchResult {

    fun getAddonPage(classes: PlatformClasses): AddonPage

    fun processChineseSearchResults(searchFilter: String, classes: PlatformClasses): PlatformSearchResult
}

fun <T> List<T>.searchRankWithChineseBias(
    searchFilter: String,
    classes: PlatformClasses,
    getSlug: (T) -> String?
): List<T> {
    val translations = classes.getTranslations()
    val (chineseResults, englishResults) = partition { mod ->
        translations
            ?.getInfoByCurseForgeId(getSlug(mod))
            ?.mainName()
            ?.takeIf { it.isNotBlank() && it.containsChinese() } != null
    }

    val levCalculator = LevCalculator()
    val sortedChineseResults = chineseResults.map { mod ->
        val translation = translations?.getInfoByCurseForgeId(getSlug(mod))
        val modName = translation?.mainName() ?: ""

        val relevanceScore = when {
            searchFilter.isEmpty() || modName.isEmpty() ->
                max(searchFilter.length, modName.length)
            else -> {
                var levDistance = levCalculator.calc(searchFilter, modName)
                searchFilter.forEach { char ->
                    if (modName.contains(char)) levDistance -= CONTAIN_CHINESE_WEIGHT
                }
                levDistance
            }
        }
        mod to relevanceScore
    }.sortedBy { it.second }
        .map { it.first }

    return sortedChineseResults + englishResults
}
