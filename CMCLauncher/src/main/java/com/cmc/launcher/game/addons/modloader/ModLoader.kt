package com.cmc.launcher.game.addons.modloader

enum class ModLoader(
    val displayName: String,
    val isLoader: Boolean = true,
    val isApiMod: Boolean = false,
    val autoDownloadable: Boolean = true
) {
    UNKNOWN(displayName = "", isLoader = false, autoDownloadable = false),
    OPTIFINE(displayName = "OptiFine", isLoader = false, autoDownloadable = false),
    FORGE(displayName = "Forge"),
    NEOFORGE(displayName = "NeoForge"),

    FABRIC(displayName = "Fabric"),
    FABRIC_API(displayName = "Fabric API", isLoader = false, isApiMod = true, autoDownloadable = false),

    LEGACY_FABRIC(displayName = "Legacy Fabric"),
    LEGACY_FABRIC_API(displayName = "Legacy Fabric API", isLoader = false, isApiMod = true, autoDownloadable = false),

    BABRIC(displayName = "Babric", autoDownloadable = false),
    BABRIC_API(displayName = "Babric API", isLoader = false, isApiMod = true, autoDownloadable = false),

    QUILT(displayName = "Quilt"),
    QUILT_API(displayName = "Quilted Fabric API", isLoader = false, isApiMod = true, autoDownloadable = false),

    LITE_LOADER(displayName = "LiteLoader", autoDownloadable = false),

    PACK(displayName = "Pack", isLoader = false, autoDownloadable = false)
}
