package com.cmc.launcher.game.version.resource_pack

import com.cmc.launcher.utils.string.stripColorCodes
import java.io.File

data class ResourcePackInfo(

    val file: File,

    val fileSize: Long? = null,

    val rawName: String = file.name.stripColorCodes(),

    val displayName: String = if (file.isDirectory) file.name else file.nameWithoutExtension,

    val isValid: Boolean,

    val description: String?,

    val packFormat: Int?,

    val icon: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResourcePackInfo

        if (isValid != other.isValid) return false
        if (packFormat != other.packFormat) return false
        if (file != other.file) return false
        if (description != other.description) return false
        if (icon != null) {
            if (other.icon == null) return false
            if (!icon.contentEquals(other.icon)) return false
        } else if (other.icon != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isValid.hashCode()
        result = 31 * result + (packFormat ?: 0)
        result = 31 * result + file.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (icon?.contentHashCode() ?: 0)
        return result
    }
}
