package com.cmc.launcher.game.download.addon.platform.curseforge

import com.cmc.launcher.game.download.addon.platform.PlatformSortField
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeCategory
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeClassID
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeModLoader
import io.ktor.http.Parameters

data class CurseForgeSearchRequest(

    val gameId: Int = 432,

    val classId: Int = CurseForgeClassID.MOD.classID,

    val categories: Set<CurseForgeCategory>? = null,

    val searchFilter: String? = null,

    val gameVersion: String? = null,

    val sortField: PlatformSortField = PlatformSortField.RELEVANCE,

    val sortOrder: String = "desc",

    val modLoader: CurseForgeModLoader? = null,

    val index: Int = 0,

    val pageSize: Int = 20,
) {

    fun toParameters(): Parameters = Parameters.build {
        append("gameId", gameId.toString())
        append("classId", classId.toString())
        categories.mutableParameters(
            singleName = "categoryId",
            mutableName = "categoryIds",
            toString = { it.describe() },
        ) { name, value ->
            append(name, value)
        }
        searchFilter?.let {
            append("searchFilter", it)
        }
        gameVersion?.let {
            append("gameVersion", it)
        }
        modLoader?.let {
            append("modLoaderType", it.code.toString())
        }
        append("sortField", sortField.curseforge)
        append("sortOrder", sortOrder)
        append("index", index.toString())
        append("pageSize", pageSize.toString())
    }

    private fun <E> Collection<E>?.mutableParameters(
        singleName: String,
        mutableName: String,
        toString: E.(E) -> String?,
        append: (name: String, value: String) -> Unit
    ) {
        this?.takeIf { it.isNotEmpty() }?.let { c1 ->
            if (c1.size > 1) {
                c1.mapNotNull { it.toString(it) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { categoryStrings ->

                        append(mutableName, categoryStrings.joinToString(separator = ",", prefix = "[", postfix = "]") { it })
                    }
            } else {
                val value = c1.first()
                value.toString(value)
                    .takeIf { !it.isNullOrEmpty() }
                    ?.let { categoryString ->
                        append(singleName, categoryString)
                    }
            }
        }
    }
}
