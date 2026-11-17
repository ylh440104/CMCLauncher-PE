package com.cmc.launcher.game.download.addon.utils

import android.content.Context
import com.cmc.launcher.game.download.addon.platform.PlatformClasses
import com.cmc.launcher.game.download.addon.platform.PlatformProject
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeProject
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthSingleProject
import com.cmc.launcher.utils.isChinese
import com.cmc.launcher.utils.string.containsChinese
import com.cmc.launcher.utils.string.tokenize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jackhuang.hmcl.addon.AddonProjectInfo
import org.jackhuang.hmcl.addon.AddonProjectInfoRegistry

fun PlatformClasses.getTranslations(): AddonProjectInfoRegistry<*>? =
    when (this) {
        PlatformClasses.MOD -> AddonProjectInfoRegistry.MOD
        PlatformClasses.MOD_PACK -> AddonProjectInfoRegistry.MODPACK
        else -> null
    }

fun PlatformProject.getMcMod(
    classes: PlatformClasses
): AddonProjectInfo? {
    val translations = classes.getTranslations() ?: return null
    return when (this) {
        is ModrinthSingleProject -> translations.getInfoByCurseForgeId(slug)
        is CurseForgeProject -> translations.getInfoByCurseForgeId(data.slug)
        else -> error("Unknown project type: $this")
    }
}

fun AddonProjectInfo?.getMcmodTitle(originTitle: String, context: Context? = null): String {
    return this?.displayName()?.takeIf { isChinese(context) } ?: originTitle
}

suspend fun String.localizedModSearchKeywords(
    classes: PlatformClasses
): Pair<Boolean, Set<String>?> {
    val mcMods = this.searchMcMods(classes) ?: return false to null
    val englishSearchFiltersSet: MutableSet<String> = LinkedHashSet(16)

    for ((count, mod) in mcMods.withIndex()) {
        val englishSearchFilter = tokenize(mod.subName().ifBlank { mod.mainName() }).joinToString(" ")
        if (englishSearchFilter.isNotBlank()) {
            englishSearchFiltersSet.add(englishSearchFilter)
        }
        if (count >= 3) break
    }

    return true to englishSearchFiltersSet
}

suspend fun String.searchMcMods(
    classes: PlatformClasses
): List<AddonProjectInfo>? {
    if (!this.containsChinese()) return null
    val translations = classes.getTranslations() ?: return null
    return withContext(Dispatchers.Default) {
        ensureActive()
        translations.search(this@searchMcMods)
    }
}
