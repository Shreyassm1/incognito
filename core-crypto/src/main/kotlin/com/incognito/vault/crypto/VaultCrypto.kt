package com.incognito.vault.crypto

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultCrypto {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_BIT_LENGTH = 256
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"

    private val CANARY_PAYLOAD = "INCOGNITO_VAULT_CANARY_V1".toByteArray(Charsets.UTF_8)
    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray {
        val salt = ByteArray(VaultHeader.SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun generateIV(): ByteArray {
        val iv = ByteArray(VaultHeader.IV_LENGTH)
        secureRandom.nextBytes(iv)
        return iv
    }

    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BIT_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    fun computeCanary(key: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, "AES"))
        return mac.doFinal(CANARY_PAYLOAD)
    }

    fun verifyCanary(key: ByteArray, expectedCanary: ByteArray): Boolean {
        val calculated = computeCanary(key)
        return MessageDigest.isEqual(calculated, expectedCanary)
    }

    fun deriveChunkIV(baseIV: ByteArray, chunkIndex: Int): ByteArray {
        val chunkIV = baseIV.copyOf()
        // XOR the lower 4 bytes with the chunk index
        chunkIV[8] = (chunkIV[8].toInt() xor ((chunkIndex ushr 24) and 0xFF)).toByte()
        chunkIV[9] = (chunkIV[9].toInt() xor ((chunkIndex ushr 16) and 0xFF)).toByte()
        chunkIV[10] = (chunkIV[10].toInt() xor ((chunkIndex ushr 8) and 0xFF)).toByte()
        chunkIV[11] = (chunkIV[11].toInt() xor (chunkIndex and 0xFF)).toByte()
        return chunkIV
    }

    fun wipe(array: ByteArray?) {
        if (array != null) {
            Arrays.fill(array, 0.toByte())
        }
    }

    fun wipe(array: CharArray?) {
        if (array != null) {
            Arrays.fill(array, '\u0000')
        }
    }

    /**
     * Encrypt an in-memory buffer (such as photo JPEG/PNG) directly into .icgt format.
     */
    fun encryptBuffer(
        data: ByteArray,
        key: ByteArray,
        salt: ByteArray,
        mediaType: MediaType,
        timestamp: Long = System.currentTimeMillis()
    ): ByteArray {
        val fileIV = generateIV()
        val chunkSize = if (data.isNotEmpty()) data.size else VaultHeader.DEFAULT_CHUNK_SIZE
        val header = VaultHeader(
            mediaType = mediaType,
            timestamp = timestamp,
            chunkSize = chunkSize,
            plaintextLength = data.size.toLong(),
            salt = salt,
            fileIV = fileIV
        )

        val headerBytes = header.toByteArray()
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val chunkIV = deriveChunkIV(fileIV, 0)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(VaultHeader.GCM_TAG_LENGTH * 8, chunkIV)
        )
        cipher.updateAAD(headerBytes)
        val ciphertext = cipher.doFinal(data)

        val result = ByteArray(headerBytes.size + ciphertext.size)
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.size)
        System.arraycopy(ciphertext, 0, result, headerBytes.size, ciphertext.size)
        return result
    }

    /**
     * Decrypt an in-memory buffer (e.g. photo or thumbnail).
     */
    fun decryptBuffer(encryptedData: ByteArray, key: ByteArray): ByteArray {
        val bais = java.io.ByteArrayInputStream(encryptedData)
        val header = VaultHeader.readFrom(bais)
        val headerBytes = header.toByteArray()
        val cipherOffset = headerBytes.size
        val cipherLength = encryptedData.size - cipherOffset

        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val chunkIV = deriveChunkIV(header.fileIV, 0)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(VaultHeader.GCM_TAG_LENGTH * 8, chunkIV)
        )
        cipher.updateAAD(headerBytes)
        return cipher.doFinal(encryptedData, cipherOffset, cipherLength)
    }

    /**
     * Chunked streaming encryption for large files (videos).
     * Prevents OutOfMemoryError and streams data chunk by chunk.
     */
    fun encryptFile(
        inputFile: File,
        outputFile: File,
        key: ByteArray,
        salt: ByteArray,
        mediaType: MediaType,
        timestamp: Long = System.currentTimeMillis(),
        chunkSize: Int = VaultHeader.DEFAULT_CHUNK_SIZE
    ) {
        val totalLength = inputFile.length()
        val fileIV = generateIV()
        val header = VaultHeader(
            mediaType = mediaType,
            timestamp = timestamp,
            chunkSize = chunkSize,
            plaintextLength = totalLength,
            salt = salt,
            fileIV = fileIV
        )

        val headerBytes = header.toByteArray()
        val keySpec = SecretKeySpec(key, "AES")

        FileInputStream(inputFile).use { fis ->
            FileOutputStream(outputFile).use { fos ->
                fos.write(headerBytes)

                val buffer = ByteArray(chunkSize)
                var chunkIndex = 0

                while (true) {
                    var bytesReadThisChunk = 0
                    while (bytesReadThisChunk < chunkSize) {
                        val r = fis.read(buffer, bytesReadThisChunk, chunkSize - bytesReadThisChunk)
                        if (r < 0) break
                        bytesReadThisChunk += r
                    }

                    if (bytesReadThisChunk == 0) {
                        break
                    }

                    val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
                    val chunkIV = deriveChunkIV(fileIV, chunkIndex)
                    cipher.init(
                        Cipher.ENCRYPT_MODE,
                        keySpec,
                        GCMParameterSpec(VaultHeader.GCM_TAG_LENGTH * 8, chunkIV)
                    )
                    cipher.updateAAD(headerBytes)
                    cipher.updateAAD(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(chunkIndex).array())

                    val encryptedChunk = cipher.doFinal(buffer, 0, bytesReadThisChunk)
                    fos.write(encryptedChunk)

                    chunkIndex++
                    if (bytesReadThisChunk < chunkSize) break
                }
                fos.flush()
            }
        }
    }

    /**
     * Opens a seekable source to read decrypted bytes on the fly.
     * ZERO temporary files on disk. O(1) chunk seeking.
     */
    fun openSeekableSource(file: File, key: ByteArray): SeekableEncryptedSource {
        val raf = RandomAccessFile(file, "r")
        try {
            val headerBytes = ByteArray(VaultHeader.HEADER_SIZE)
            raf.readFully(headerBytes)
            val header = VaultHeader.readFrom(java.io.ByteArrayInputStream(headerBytes))

            return ChunkedSeekableEncryptedSource(
                file = file,
                raf = raf,
                header = header,
                headerBytes = headerBytes,
                key = key.copyOf()
            )
        } catch (t: Throwable) {
            raf.close()
            throw t
        }
    }

    private class ChunkedSeekableEncryptedSource(
        private val file: File,
        private val raf: RandomAccessFile,
        override val header: VaultHeader,
        private val headerBytes: ByteArray,
        private val key: ByteArray
    ) : SeekableEncryptedSource {

        override val plaintextLength: Long = header.plaintextLength
        private var position: Long = 0

        private var cachedChunkIndex: Int = -1
        private var cachedChunkData: ByteArray = ByteArray(0)
        private val keySpec = SecretKeySpec(key, "AES")

        override val currentPosition: Long
            get() = position

        override fun seek(position: Long) {
            require(position >= 0) { "Seek position cannot be negative: $position" }
            this.position = position.coerceAtMost(plaintextLength)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= plaintextLength) return -1
            if (length <= 0) return 0

            val bytesToRead = length.toLong().coerceAtMost(plaintextLength - position).toInt()
            var bytesReadTotal = 0

            while (bytesReadTotal < bytesToRead) {
                val chunkIndex = (position / header.chunkSize).toInt()
                val offsetInChunk = (position % header.chunkSize).toInt()

                loadChunk(chunkIndex)

                val availableInChunk = cachedChunkData.size - offsetInChunk
                if (availableInChunk <= 0) {
                    break
                }

                val toCopy = (bytesToRead - bytesReadTotal).coerceAtMost(availableInChunk)
                System.arraycopy(
                    cachedChunkData,
                    offsetInChunk,
                    buffer,
                    offset + bytesReadTotal,
                    toCopy
                )

                position += toCopy
                bytesReadTotal += toCopy
            }

            return if (bytesReadTotal > 0) bytesReadTotal else -1
        }

        private fun loadChunk(chunkIndex: Int) {
            if (cachedChunkIndex == chunkIndex) return

            val chunkSize = header.chunkSize
            val totalPlaintext = header.plaintextLength

            val chunkStartPlaintext = chunkIndex.toLong() * chunkSize
            val chunkPlaintextLen = (totalPlaintext - chunkStartPlaintext).coerceAtMost(chunkSize.toLong()).toInt()
            val chunkCiphertextLen = chunkPlaintextLen + VaultHeader.GCM_TAG_LENGTH

            // Each previous chunk 0 until chunkIndex-1 was of full size (chunkSize + GCM_TAG_LENGTH)
            val fileOffset = VaultHeader.HEADER_SIZE.toLong() + chunkIndex.toLong() * (chunkSize + VaultHeader.GCM_TAG_LENGTH)

            raf.seek(fileOffset)
            val cipherBuf = ByteArray(chunkCiphertextLen)
            raf.readFully(cipherBuf)

            val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
            val chunkIV = deriveChunkIV(header.fileIV, chunkIndex)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keySpec,
                GCMParameterSpec(VaultHeader.GCM_TAG_LENGTH * 8, chunkIV)
            )
            cipher.updateAAD(headerBytes)
            cipher.updateAAD(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(chunkIndex).array())

            cachedChunkData = cipher.doFinal(cipherBuf)
            cachedChunkIndex = chunkIndex
        }

        override fun close() {
            wipe(key)
            Arrays.fill(cachedChunkData, 0.toByte())
            raf.close()
        }
    }
}
