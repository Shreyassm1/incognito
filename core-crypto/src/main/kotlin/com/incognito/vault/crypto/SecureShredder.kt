package com.incognito.vault.crypto

import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.util.Arrays

object SecureShredder {
    private val secureRandom = SecureRandom()
    private const val BUFFER_SIZE = 64 * 1024 // 64 KB

    /**
     * Cryptographically shred a file so forensics cannot recover it from storage blocks.
     * 1. Overwrite with cryptographically secure random bytes
     * 2. Overwrite with zero bytes
     * 3. Sync hardware buffers
     * 4. Truncate to 0 length
     * 5. Delete file
     */
    fun shred(file: File) {
        if (!file.exists()) return

        try {
            val length = file.length()
            if (length > 0) {
                RandomAccessFile(file, "rws").use { raf ->
                    val buffer = ByteArray(BUFFER_SIZE)

                    // Pass 1: Random overwrite
                    raf.seek(0)
                    var written: Long = 0
                    while (written < length) {
                        val toWrite = (length - written).coerceAtMost(BUFFER_SIZE.toLong()).toInt()
                        secureRandom.nextBytes(buffer)
                        raf.write(buffer, 0, toWrite)
                        written += toWrite
                    }
                    raf.fd.sync()

                    // Pass 2: Zero overwrite
                    raf.seek(0)
                    Arrays.fill(buffer, 0.toByte())
                    written = 0
                    while (written < length) {
                        val toWrite = (length - written).coerceAtMost(BUFFER_SIZE.toLong()).toInt()
                        raf.write(buffer, 0, toWrite)
                        written += toWrite
                    }
                    raf.fd.sync()

                    // Truncate
                    raf.setLength(0)
                    raf.fd.sync()
                }
            }
        } catch (e: Exception) {
            // Log or fallback
        } finally {
            file.delete()
        }
    }
}
