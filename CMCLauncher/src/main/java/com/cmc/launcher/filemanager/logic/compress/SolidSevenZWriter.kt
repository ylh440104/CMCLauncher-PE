package com.cmc.launcher.filemanager.logic.compress

import com.cmc.launcher.filemanager.os.FmLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tukaani.xz.ArrayCache
import org.tukaani.xz.FinishableWrapperOutputStream
import org.tukaani.xz.LZMA2Options
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import java.util.BitSet
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TAG = "SolidSevenZ"

object SolidSevenZWriter {

    private const val SIGNATURE_HEADER_SIZE = 32
    private const val NUM_CYCLES_POWER = 19
    private const val BUFFER_SIZE = 64 * 1024

    private const val N_END = 0x00
    private const val N_HEADER = 0x01
    private const val N_MAIN_STREAMS_INFO = 0x04
    private const val N_FILES_INFO = 0x05
    private const val N_PACK_INFO = 0x06
    private const val N_UNPACK_INFO = 0x07
    private const val N_SUB_STREAMS_INFO = 0x08
    private const val N_SIZE = 0x09
    private const val N_CRC = 0x0A
    private const val N_FOLDER = 0x0B
    private const val N_CODERS_UNPACK_SIZE = 0x0C
    private const val N_NUM_UNPACK_STREAM = 0x0D
    private const val N_EMPTY_STREAM = 0x0E
    private const val N_EMPTY_FILE = 0x0F
    private const val N_NAME = 0x11

    private val SEVEN_Z_SIGNATURE = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)

    suspend fun write(
        sources: List<Path>,
        output: Path,
        options: CompressOptions,
        total: Int,
        bytesTotal: Long,
        onProgress: (completed: Int, total: Int, currentName: String?) -> Unit,
        checkCancel: () -> Boolean,
        onBytes: (bytesDone: Long, bytesTotal: Long) -> Unit = { _, _ -> }
    ): CompressSummary = withContext(Dispatchers.IO) {
        val password = options.password ?: throw IllegalArgumentException("SolidSevenZ requires a password")
        require(sources.isNotEmpty()) { "No sources to compress" }

        val dictSize = dictionarySize(options.level)
        val dictPropsByte = lzma2DictPropsByte(dictSize)
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val aesKey = deriveKey(password, NUM_CYCLES_POWER, EMPTY_SALT)

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))

        val entries = mutableListOf<EntryInfo>()
        val folderCrc = CRC32()
        var totalUncompressed = 0L
        val state = ProgressState()

        val openOptions = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        Files.newByteChannel(output, *openOptions).use { channel ->
            channel.position(SIGNATURE_HEADER_SIZE.toLong())

            val channelOut = NoCloseOutputStream(Channels.newOutputStream(channel))
            val packedCounter = CountingCrcOutputStream(channelOut)
            val aesOut = AesCbcZeroPadOutputStream(packedCounter, cipher)
            val lzma2Counter = CountingOutputStream(aesOut)

            val lzma2Options = LZMA2Options().apply { setDictSize(dictSize) }
            val lzma2 = lzma2Options.getOutputStream(
                FinishableWrapperOutputStream(lzma2Counter),
                ArrayCache.getDefaultCache()
            )

            for (source in sources) {
                if (checkCancel()) throw CancellationException()
                val name = source.fileName?.toString() ?: "entry"
                totalUncompressed += addSource(
                    lzma2 = lzma2,
                    source = source,
                    entryRoot = name,
                    entries = entries,
                    folderCrc = folderCrc,
                    state = state,
                    total = total,
                    bytesTotal = bytesTotal,
                    onProgress = onProgress,
                    onBytes = onBytes,
                    checkCancel = checkCancel
                )
            }

            lzma2.finish()
            aesOut.finish()

            val packedSize = packedCounter.count
            val packedCrc = packedCounter.crcValue
            val lzma2PackedLen = lzma2Counter.count

            val header = buildHeader(
                entries = entries,
                packedSize = packedSize,
                packedCrc = packedCrc,
                lzma2PackedLen = lzma2PackedLen,
                totalUncompressed = totalUncompressed,
                folderCrc = folderCrc.value.toInt(),
                dictPropsByte = dictPropsByte,
                iv = iv
            )
            channel.position(SIGNATURE_HEADER_SIZE.toLong() + packedSize)
            writeFully(channel, header)

            val signature = buildSignatureHeader(
                nextHeaderOffset = packedSize,
                nextHeaderSize = header.size.toLong(),
                nextHeaderCrc = crc32Of(header)
            )
            channel.position(0)
            writeFully(channel, signature)
        }
        CompressSummary(output, total)
    }

    private class ProgressState {
        var completed = 0
        var bytesDone = 0L
    }

    private class EntryInfo(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val hasStream: Boolean,
        val emptyFile: Boolean
    )

    private fun addSource(
        lzma2: OutputStream,
        source: Path,
        entryRoot: String,
        entries: MutableList<EntryInfo>,
        folderCrc: CRC32,
        state: ProgressState,
        total: Int,
        bytesTotal: Long,
        onProgress: (Int, Int, String?) -> Unit,
        onBytes: (Long, Long) -> Unit,
        checkCancel: () -> Boolean
    ): Long {
        val attrs = Files.readAttributes(source, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (attrs.isSymbolicLink) {
            FmLog.info(TAG, "Skip symlink during compress: $source")
            return 0L
        }
        if (attrs.isDirectory) {
            entries += EntryInfo(name = "$entryRoot/", isDirectory = true, size = 0, hasStream = false, emptyFile = false)
            var bytes = 0L
            for (child in sortedChildren(source)) {
                if (checkCancel()) throw CancellationException()
                val childName = child.fileName?.toString() ?: continue
                bytes += addSource(
                    lzma2, child, "$entryRoot/$childName", entries, folderCrc,
                    state, total, bytesTotal, onProgress, onBytes, checkCancel
                )
            }
            return bytes
        }

        var size = 0L
        Files.newInputStream(source).use { input ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                if (checkCancel()) throw CancellationException()
                val n = input.read(buf)
                if (n < 0) break
                lzma2.write(buf, 0, n)
                folderCrc.update(buf, 0, n)
                size += n
                state.bytesDone += n
                onBytes(state.bytesDone, bytesTotal)
            }
        }
        entries += EntryInfo(
            name = entryRoot,
            isDirectory = false,
            size = size,
            hasStream = size > 0L,
            emptyFile = size == 0L
        )
        state.completed++
        onProgress(state.completed, total, entryRoot)
        return size
    }

    private fun sortedChildren(dir: Path): List<Path> {
        return runCatching { Files.newDirectoryStream(dir).use { it.toList() } }
            .getOrElse { emptyList() }
            .sortedBy { it.fileName?.toString() ?: it.toString() }
    }

    private fun buildHeader(
        entries: List<EntryInfo>,
        packedSize: Long,
        packedCrc: Int,
        lzma2PackedLen: Long,
        totalUncompressed: Long,
        folderCrc: Int,
        dictPropsByte: Int,
        iv: ByteArray
    ): ByteArray {
        val nonEmpty = entries.filter { it.hasStream }
        val numFiles = entries.size

        val bos = ByteArrayOutputStream()
        val out = DataOutputStream(bos)

        out.write(N_HEADER)
        out.write(N_MAIN_STREAMS_INFO)

        if (nonEmpty.isNotEmpty()) {

            out.write(N_PACK_INFO)
            writeUint64(out, 0)
            writeUint64(out, 1)
            out.write(N_SIZE)
            writeUint64(out, packedSize)
            out.write(N_CRC)
            out.write(1)
            writeIntLE(out, packedCrc)
            out.write(N_END)

            out.write(N_UNPACK_INFO)
            out.write(N_FOLDER)
            writeUint64(out, 1)
            out.write(0)
            writeFolder(out, dictPropsByte, iv)
            out.write(N_CODERS_UNPACK_SIZE)
            writeUint64(out, lzma2PackedLen)
            writeUint64(out, totalUncompressed)
            out.write(N_CRC)
            out.write(1)
            writeIntLE(out, folderCrc)
            out.write(N_END)

            out.write(N_SUB_STREAMS_INFO)
            out.write(N_NUM_UNPACK_STREAM)
            writeUint64(out, nonEmpty.size.toLong())
            out.write(N_SIZE)

            for (i in 0 until nonEmpty.size - 1) {
                writeUint64(out, nonEmpty[i].size)
            }
            out.write(N_END)
        }

        out.write(N_END)

        out.write(N_FILES_INFO)
        writeUint64(out, numFiles.toLong())

        if (entries.any { !it.hasStream }) {
            val emptyStreamIndices = entries.mapIndexedNotNull { i, e -> if (!e.hasStream) i else null }

            val emptyStreamBits = BitSet()
            emptyStreamIndices.forEach { emptyStreamBits.set(it) }
            writeBitsProperty(out, N_EMPTY_STREAM, emptyStreamBits, numFiles)

            if (emptyStreamIndices.any { entries[it].emptyFile }) {
                val emptyFileBits = BitSet()
                emptyStreamIndices.forEachIndexed { j, i -> if (entries[i].emptyFile) emptyFileBits.set(j) }
                writeBitsProperty(out, N_EMPTY_FILE, emptyFileBits, emptyStreamIndices.size)
            }
        }

        out.write(N_NAME)
        val nameBytes = ByteArrayOutputStream()
        for (e in entries) {
            nameBytes.write(e.name.toByteArray(Charsets.UTF_16LE))
            nameBytes.write(0)
            nameBytes.write(0)
        }
        val names = nameBytes.toByteArray()
        writeUint64(out, 1L + names.size)
        out.write(0)
        out.write(names)

        out.write(N_END)
        out.write(N_END)

        out.flush()
        return bos.toByteArray()
    }

    private fun writeFolder(out: DataOutputStream, dictPropsByte: Int, iv: ByteArray) {
        writeUint64(out, 2)

        out.write(0x24)
        out.write(0x06)
        out.write(0xF1)
        out.write(0x07)
        out.write(0x01)
        writeUint64(out, (2 + iv.size).toLong())
        out.write(0x53)
        out.write(0x0F)
        out.write(iv)

        out.write(0x21)
        out.write(0x21)
        writeUint64(out, 1)
        out.write(dictPropsByte)

        writeUint64(out, 1)
        writeUint64(out, 0)
    }

    private fun writeBitsProperty(out: DataOutputStream, nid: Int, bits: BitSet, length: Int) {
        out.write(nid)
        val packed = packBits(bits, length)
        writeUint64(out, packed.size.toLong())
        out.write(packed)
    }

    private fun packBits(bits: BitSet, length: Int): ByteArray {
        val bytes = ByteArray((length + 7) / 8)
        for (i in 0 until length) {
            if (bits.get(i)) {
                bytes[i / 8] = (bytes[i / 8].toInt() or (0x80 shr (i % 8))).toByte()
            }
        }
        return bytes
    }

    private fun buildSignatureHeader(nextHeaderOffset: Long, nextHeaderSize: Long, nextHeaderCrc: Int): ByteArray {
        val b = ByteBuffer.allocate(SIGNATURE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        b.put(SEVEN_Z_SIGNATURE)
        b.put(0).put(2)
        b.putInt(0)
        b.putLong(nextHeaderOffset)
        b.putLong(nextHeaderSize)
        b.putInt(nextHeaderCrc)
        val crc = CRC32()
        crc.update(b.array(), 12, 20)
        b.putInt(8, crc.value.toInt())
        return b.array()
    }

    private fun deriveKey(password: String, numCyclesPower: Int, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val extra = ByteArray(8)
        val passwordBytes = password.toByteArray(Charsets.UTF_16LE)
        val iterations = 1L shl numCyclesPower
        for (j in 0 until iterations) {
            digest.update(salt)
            digest.update(passwordBytes)
            digest.update(extra)
            for (k in extra.indices) {
                extra[k] = (extra[k] + 1).toByte()
                if (extra[k] != 0.toByte()) break
            }
        }
        return digest.digest()
    }

    private fun lzma2DictPropsByte(dictSize: Int): Int {
        val lead = Integer.numberOfLeadingZeros(dictSize)
        val secondBit = (dictSize ushr (30 - lead)) - 2
        return (19 - lead) * 2 + secondBit
    }

    private fun dictionarySize(level: Int?): Int {
        val l = level?.coerceIn(1, 9) ?: 5
        return 1 shl (18 + l.coerceAtMost(7))
    }

    private fun crc32Of(bytes: ByteArray): Int {
        val crc = CRC32()
        crc.update(bytes)
        return crc.value.toInt()
    }

    private fun writeFully(channel: SeekableByteChannel, bytes: ByteArray) {
        val buf = ByteBuffer.wrap(bytes)
        while (buf.hasRemaining()) channel.write(buf)
    }

    private fun writeUint64(out: DataOutputStream, value: Long) {
        var v = value
        var firstByte = 0
        var mask = 0x80
        var i = 0
        while (i < 8) {
            if (v < 1L shl 7 * (i + 1)) {
                firstByte = (firstByte.toLong() or (v ushr 8 * i)).toInt()
                break
            }
            firstByte = (firstByte.toLong() or mask.toLong()).toInt()
            mask = mask ushr 1
            i++
        }
        out.write(firstByte)
        while (i > 0) {
            out.write((v and 0xFF).toInt())
            v = v ushr 8
            i--
        }
    }

    private fun writeIntLE(out: DataOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private val EMPTY_SALT = ByteArray(0)

    private class NoCloseOutputStream(private val out: OutputStream) : OutputStream() {
        override fun write(b: Int) = out.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun flush() = out.flush()
        override fun close() = out.flush()
    }

    private open class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var count: Long = 0L
            protected set
        override fun write(b: Int) {
            out.write(b)
            count++
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
        override fun flush() = out.flush()
        override fun close() = out.close()
    }

    private class CountingCrcOutputStream(out: OutputStream) : CountingOutputStream(out) {
        private val crc = CRC32()
        val crcValue: Int get() = crc.value.toInt()
        override fun write(b: Int) {
            super.write(b)
            crc.update(b)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            super.write(b, off, len)
            crc.update(b, off, len)
        }
    }

    private class AesCbcZeroPadOutputStream(
        private val out: OutputStream,
        cipher: Cipher
    ) : OutputStream() {
        private val cipherOut = CipherOutputStream(out, cipher)
        private val blockSize = cipher.blockSize
        private val buffer = ByteArray(blockSize)
        private var count = 0

        private fun flushBuffer() {
            cipherOut.write(buffer)
            count = 0
            Arrays.fill(buffer, 0)
        }

        override fun write(b: Int) {
            buffer[count++] = (b and 0xFF).toByte()
            if (count == blockSize) flushBuffer()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len == 0) return
            var gap = if (len + count > blockSize) blockSize - count else len
            System.arraycopy(b, off, buffer, count, gap)
            count += gap
            if (count == blockSize) {
                flushBuffer()
                if (len - gap >= blockSize) {
                    val multi = (len - gap) / blockSize * blockSize
                    cipherOut.write(b, off + gap, multi)
                    gap += multi
                }
                System.arraycopy(b, off + gap, buffer, 0, len - gap)
                count = len - gap
            }
        }

        fun finish() {
            if (count > 0) cipherOut.write(buffer)
            cipherOut.close()
        }
    }
}
