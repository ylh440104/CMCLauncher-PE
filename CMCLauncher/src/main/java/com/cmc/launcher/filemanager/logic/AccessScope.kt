package com.cmc.launcher.filemanager.logic

import com.cmc.launcher.filemanager.os.FmLog
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val TAG = "FileAccessScope"

class AccessScope(val root: Path) {
    init {
        require(Files.isDirectory(root)) {
            "AccessScope root must be an existing directory: $root"
        }
    }

    fun guard(target: Path): Path {
        val resolved = resolveAgainstRoot(target)
        val normalized = resolved.normalize().toAbsolutePath()
        requireInside(normalized)
        return normalized
    }

    fun guardAbsolute(target: Path): Path {
        val normalized = target.normalize().toAbsolutePath()
        requireInside(normalized)
        return normalized
    }

    fun isUnder(child: Path): Boolean {
        val normalized = child.normalize().toAbsolutePath()
        return normalized == rootAbs || normalized.startsWith(rootAbs)
    }

    val rootAbs: Path = root.normalize().toAbsolutePath()

    private fun resolveAgainstRoot(target: Path): Path {
        return if (target.isAbsolute) {
            target
        } else {
            rootAbs.resolve(target)
        }
    }

    private fun requireInside(normalized: Path) {
        if (!normalized.startsWith(rootAbs)) {
            FmLog.warn(TAG, "Denied out-of-scope access.")
            throw OutOfScopeException("Target path is outside the accessible scope.")
        }
    }

    companion object {

        fun ofRoot(rootPath: String): AccessScope {
            val root = Paths.get(rootPath).normalize().toAbsolutePath()
            if (!Files.isDirectory(root)) {
                throw IllegalArgumentException("Root directory does not exist or is not a directory")
            }
            return AccessScope(root)
        }
    }
}

class OutOfScopeException(message: String) : SecurityException(message)
