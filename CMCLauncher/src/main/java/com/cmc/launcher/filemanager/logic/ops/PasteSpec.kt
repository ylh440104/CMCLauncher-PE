package com.cmc.launcher.filemanager.logic.ops

import java.nio.file.Path

enum class ConflictResolution {

    SKIP,

    OVERWRITE,

    KEEP_BOTH
}

enum class PasteMode {

    COPY,

    MOVE
}

sealed interface PasteRequest {

    data class Ready(val sources: List<Path>, val targetDir: Path, val mode: PasteMode) : PasteRequest

    data class ResolveRequest(
        val sources: List<Path>,
        val targetDir: Path,
        val mode: PasteMode,

        val conflicts: List<ConflictItem?>
    ) : PasteRequest
}

data class ConflictItem(val source: Path, val existing: Path)

data class ItemResult(
    val source: Path,
    val target: Path?,
    val success: Boolean,
    val reason: String?
)

data class PasteSummary(
    val mode: PasteMode,
    val targetDir: Path,
    val results: List<ItemResult>
)
