package com.cmc.launcher.game.download.addon.platform.modrinth.models

import kotlinx.serialization.json.Json

interface ModrinthFacet {

    fun facetName(): String

    fun facetValue(): String

    fun describe(): String? = "${facetName()}:${facetValue()}"
}

class VersionFacet(val version: String) : ModrinthFacet {
    override fun facetValue(): String = version
    override fun facetName(): String = "versions"
}

enum class ProjectTypeFacet : ModrinthFacet {
    MOD {
        override fun facetValue(): String = "mod"
    },

    MODPACK {
        override fun facetValue(): String = "modpack"
    },

    RESOURCE_PACK {
        override fun facetValue(): String = "resourcepack"
    },

    SHADER {
        override fun facetValue(): String = "shader"
    };

    override fun facetName(): String = "project_type"
}

fun List<ModrinthFacet>.toFacetsString(): String {
    val rawFacets = this.map { listOfNotNull(it.describe()) }
    return Json.encodeToString(rawFacets)
}
