package com.cmc.launcher.game.download.engine

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

class FileSink(
    targetFile: File,
    private val expectedChecksum: String?
) : Closeable {
    private val target: Path = targetFile.toPath()
    private val temp: Path
    private val channel: FileChannel
    private val digest: MessageDigest? = expectedChecksum?.let { MessageDigest.getInstance("SHA-1") }

    var broken = false
        private set

    init {
        val parent = target.toAbsolutePath().parent
        Files.createDirectories(parent)
        temp = Files.createTempFile(parent, null, null)
        channel = FileChannel.open(
            temp,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE
        )
    }

    fun write(buffer: ByteArray, offset: Int, length: Int) {
        try {
            digest?.update(buffer, offset, length)
            val wrapped = ByteBuffer.wrap(buffer, offset, length)
            while (wrapped.hasRemaining()) {
                channel.write(wrapped)
            }
        } catch (e: IOException) {
            broken = true
            throw e
        }
    }

    fun finish() {
        var moved = false
        try {
            channel.close()

            if (expectedChecksum != null) {
                val actual = digest!!.digest().toHexString()
                if (!expectedChecksum.equals(actual, ignoreCase = true)) {
                    throw ChecksumMismatchException("SHA-1", expectedChecksum, actual)
                }
            }

            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            moved = true
        } finally {
            if (!moved) deleteTemp()
        }
    }

    override fun close() {
        runCatching { channel.close() }
        deleteTemp()
    }

    private fun deleteTemp() {
        runCatching { Files.deleteIfExists(temp) }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { "%02x".format(it) }
}
