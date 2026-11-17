package com.cmc.launcher.filemanager.logic.trash

import org.json.JSONObject
import java.nio.file.Path

data class TrashMeta(
    val originalPath: String,
    val deletedAt: Long,
    val name: String,
    val isFolder: Boolean,
    val corrupted: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("originalPath", originalPath)
        put("deletedAt", deletedAt)
        put("name", name)
        put("isFolder", isFolder)
        put("corrupted", corrupted)
    }

    companion object {
        fun fromJson(text: String): TrashMeta? = runCatching {
            val obj = JSONObject(text)
            TrashMeta(
                originalPath = obj.getString("originalPath"),
                deletedAt = obj.getLong("deletedAt"),
                name = obj.getString("name"),
                isFolder = obj.getBoolean("isFolder"),
                corrupted = obj.optBoolean("corrupted", false)
            )
        }.getOrNull()
    }
}

data class TrashItem(
    val uuid: String,
    val meta: TrashMeta,
    val contentDir: Path,
    val size: Long
) {
    val isFolder: Boolean get() = meta.isFolder
    val name: String get() = meta.name
    val deletedAt: Long get() = meta.deletedAt
}
