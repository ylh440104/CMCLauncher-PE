package com.cmc.launcher.game.version.mod

import com.cmc.launcher.utils.file.MurmurHash2Incremental
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

val CURSEFORGE_FINGERPRINT_SKIP_BYTES = listOf(0x9, 0xa, 0xd, 0x20)

private val CURSEFORGE_FINGERPRINT_SKIP_TABLE = BooleanArray(256).also { table ->
    CURSEFORGE_FINGERPRINT_SKIP_BYTES.forEach { table[it] = true }
}

class ModFingerprints(
    val sha1: String,
    val murmur2: Long
)

private object ModFingerprintMemoryCache {
    private class Entry(
        val fingerprints: ModFingerprints,
        val lastModified: Long,
        val length: Long
    )

    private val cache = ConcurrentHashMap<String, Entry>()

    fun get(file: File): ModFingerprints? {
        return cache[file.absolutePath]
            ?.takeIf { it.lastModified == file.lastModified() && it.length == file.length() }
            ?.fingerprints
    }

    fun put(file: File, fingerprints: ModFingerprints) {
        cache[file.absolutePath] = Entry(fingerprints, file.lastModified(), file.length())
    }
}

suspend fun computeModFingerprints(file: File): ModFingerprints = withContext(Dispatchers.IO) {
    ModFingerprintMemoryCache.get(file)?.let { return@withContext it }

    val context = currentCoroutineContext()
    val digest = MessageDigest.getInstance("SHA-1")
    var filteredLength = 0

    Files.newInputStream(file.toPath()).use { stream ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            context.ensureActive()
            digest.update(buffer, 0, bytesRead)
            for (i in 0 until bytesRead) {
                if (!CURSEFORGE_FINGERPRINT_SKIP_TABLE[buffer[i].toInt() and 0xFF]) filteredLength++
            }
        }
    }

    val sha1 = digest.digest().joinToString("") { "%02x".format(it) }
    val murmur2 = MurmurHash2Incremental.computeHash(
        file = file,
        byteToSkip = CURSEFORGE_FINGERPRINT_SKIP_BYTES,
        filteredLength = filteredLength
    )

    ModFingerprints(sha1 = sha1, murmur2 = murmur2).also {
        ModFingerprintMemoryCache.put(file, it)
    }
}
