package com.cmc.launcher.filemanager.viewmodel

import com.cmc.launcher.filemanager.config.FmConfig
import com.cmc.launcher.filemanager.logic.entry.FmEntry

data class SortConfig(
    val field: FmConfig.SortField = FmConfig.SortField.NAME,
    val ascending: Boolean = true,

    val folderFirst: Boolean = true
) {
    companion object {
        fun load(): SortConfig = SortConfig(
            field = runCatching {
                FmConfig.SortField.valueOf(FmConfig.sortField())
            }.getOrDefault(FmConfig.SortField.NAME),
            ascending = FmConfig.sortAscending(),
            folderFirst = FmConfig.folderFirst()
        )
    }
}

data class TrashSortConfig(
    val field: FmConfig.TrashSortField = FmConfig.TrashSortField.DELETED,

    val ascending: Boolean = false,

    val folderFirst: Boolean = true
) {
    companion object {
        fun load(): TrashSortConfig = TrashSortConfig(
            field = runCatching {
                FmConfig.TrashSortField.valueOf(FmConfig.trashSortField())
            }.getOrDefault(FmConfig.TrashSortField.DELETED),
            ascending = FmConfig.trashSortAscending(),
            folderFirst = FmConfig.trashFolderFirst()
        )
    }
}

fun TrashSortConfig.persist() {
    FmConfig.setTrashSortField(field.name)
    FmConfig.setTrashSortAscending(ascending)
    FmConfig.setTrashFolderFirst(folderFirst)
}

fun SortConfig.persist() {
    FmConfig.setSortField(field.name)
    FmConfig.setSortAscending(ascending)
    FmConfig.setFolderFirst(folderFirst)
}

fun applyVisibility(
    entries: List<FmEntry>,
    config: SortConfig,
    showHidden: Boolean
): List<FmEntry> {
    var list = entries
    if (!showHidden) {
        list = list.filterNot { it.hidden }
    }
    val comparator: Comparator<FmEntry> = when (config.field) {
        FmConfig.SortField.NAME -> compareBy { it.name.lowercase() }

        FmConfig.SortField.SIZE -> compareBy<FmEntry> {
            if (it.isDirectory) 0L else it.size
        }.thenBy { it.name.lowercase() }

        FmConfig.SortField.MODIFIED -> compareBy { it.modifiedMs }
    }
    val ordered = if (config.ascending) {
        list.sortedWith(comparator)
    } else {
        list.sortedWith(comparator.reversed())
    }

    if (!config.folderFirst) return ordered

    val dirs = ordered.filter { it.isDirectory }
    val files = ordered.filter { it.isFile }
    return dirs + files
}
