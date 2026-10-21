package com.incognito.vault.crypto

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class VaultHeader(
    val mediaType: MediaType,
    val timestamp: Long,
    val chunkSize: Int,
    val plaintextLength: Long,
    val salt: ByteArray, // 32 bytes
    val fileIV: ByteArray // 12 bytes
) {
    init {
        require(salt.size == SALT_LENGTH) { "Salt must be $SALT_LENGTH bytes" }
        require(fileIV.size == IV_LENGTH) { "File IV must be $IV_LENGTH bytes" }
    }

    companion object {
        val MAGIC = byteArrayOf(0x49, 0x43, 0x47, 0x54) // "ICGT"
        const val VERSION: Byte = 1
        const val SALT_LENGTH = 32
        const val IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 16 // 128-bit authentication tag
        const val DEFAULT_CHUNK_SIZE = 64 * 1024 // 64 KB

        // MAGIC(4) + VERSION(1) + MEDIATYPE(1) + TIMESTAMP(8) + CHUNK_SIZE(4) + PLAINTEXT_LENGTH(8) + SALT(32) + IV(12) = 70 bytes
        const val HEADER_SIZE = 4 + 1 + 1 + 8 + 4 + 8 + SALT_LENGTH + IV_LENGTH

        fun readFrom(stream: InputStream): VaultHeader {
            val magicBuf = ByteArray(4)
            var read = 0
            while (read < 4) {
                val r = stream.read(magicBuf, read, 4 - read)
                if (r < 0) throw IllegalArgumentException("Unexpected EOF reading magic")
                read += r
            }
            if (!magicBuf.contentEquals(MAGIC)) {
                throw IllegalArgumentException("Invalid magic bytes in vault file")
            }

            val version = stream.read()
            if (version != VERSION.toInt()) {
                throw IllegalArgumentException("Unsupported vault version: $version")
            }

            val mediaTypeCode = stream.read().toByte()
            val mediaType = MediaType.fromCode(mediaTypeCode)

            val bb = ByteBuffer.allocate(8 + 4 + 8)
            bb.order(ByteOrder.BIG_ENDIAN)
            val numBuf = ByteArray(20)
            read = 0
            while (read < 20) {
                val r = stream.read(numBuf, read, 20 - read)
                if (r < 0) throw IllegalArgumentException("Unexpected EOF reading header numbers")
                read += r
            }
            bb.put(numBuf)
            bb.flip()
            val timestamp = bb.long
            val chunkSize = bb.int
            val plaintextLength = bb.long

            val salt = ByteArray(SALT_LENGTH)
            read = 0
            while (read < SALT_LENGTH) {
                val r = stream.read(salt, read, SALT_LENGTH - read)
                if (r < 0) throw IllegalArgumentException("Unexpected EOF reading salt")
                read += r
            }

            val fileIV = ByteArray(IV_LENGTH)
            read = 0
            while (read < IV_LENGTH) {
                val r = stream.read(fileIV, read, IV_LENGTH - read)
                if (r < 0) throw IllegalArgumentException("Unexpected EOF reading IV")
                read += r
            }

            return VaultHeader(mediaType, timestamp, chunkSize, plaintextLength, salt, fileIV)
        }
    }

    fun writeTo(stream: OutputStream) {
        stream.write(MAGIC)
        stream.write(VERSION.toInt())
        stream.write(mediaType.code.toInt())
        val bb = ByteBuffer.allocate(8 + 4 + 8)
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.putLong(timestamp)
        bb.putInt(chunkSize)
        bb.putLong(plaintextLength)
        stream.write(bb.array())
        stream.write(salt)
        stream.write(fileIV)
    }

    fun toByteArray(): ByteArray {
        val baos = java.io.ByteArrayOutputStream(HEADER_SIZE)
        writeTo(baos)
        return baos.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultHeader) return false
        return mediaType == other.mediaType &&
                timestamp == other.timestamp &&
                chunkSize == other.chunkSize &&
                plaintextLength == other.plaintextLength &&
                salt.contentEquals(other.salt) &&
                fileIV.contentEquals(other.fileIV)
    }

    override fun hashCode(): Int {
        var result = mediaType.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + chunkSize
        result = 31 * result + plaintextLength.hashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + fileIV.contentHashCode()
        return result
    }
}
