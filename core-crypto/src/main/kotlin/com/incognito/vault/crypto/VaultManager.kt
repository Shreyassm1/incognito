package com.incognito.vault.crypto

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

data class VaultMediaItem(
    val id: String,
    val file: File,
    val mediaType: MediaType,
    val timestamp: Long,
    val plaintextLength: Long,
    val thumbnailFile: File? = null
)

class VaultManager(val vaultDir: File) {

    private val configFile = File(vaultDir, ".vault_config.bin")
    val mediaDir = File(vaultDir, "media")
    val thumbDir = File(vaultDir, "thumbs")

    init {
        if (!vaultDir.exists()) vaultDir.mkdirs()
        if (!mediaDir.exists()) mediaDir.mkdirs()
        if (!thumbDir.exists()) thumbDir.mkdirs()
    }

    fun isInitialized(): Boolean = configFile.exists() && configFile.length() >= 64

    fun setupMasterPassword(password: CharArray): ByteArray {
        val salt = VaultCrypto.generateSalt()
        val key = VaultCrypto.deriveKey(password, salt)
        val canary = VaultCrypto.computeCanary(key)

        FileOutputStream(configFile).use { fos ->
            fos.write(salt)
            fos.write(canary)
            fos.flush()
        }
        return key
    }

    fun unlock(password: CharArray): ByteArray? {
        if (!isInitialized()) return null

        val salt = ByteArray(VaultHeader.SALT_LENGTH)
        val expectedCanary = ByteArray(32)

        FileInputStream(configFile).use { fis ->
            fis.read(salt)
            fis.read(expectedCanary)
        }

        val candidateKey = VaultCrypto.deriveKey(password, salt)
        val valid = VaultCrypto.verifyCanary(candidateKey, expectedCanary)

        return if (valid) {
            candidateKey
        } else {
            VaultCrypto.wipe(candidateKey)
            null
        }
    }

    fun getMasterSalt(): ByteArray {
        if (!isInitialized()) throw IllegalStateException("Vault not initialized")
        val salt = ByteArray(VaultHeader.SALT_LENGTH)
        FileInputStream(configFile).use { it.read(salt) }
        return salt
    }

    fun saveEncryptedPhoto(
        jpegBytes: ByteArray,
        key: ByteArray,
        thumbnailBytes: ByteArray? = null,
        timestamp: Long = System.currentTimeMillis()
    ): VaultMediaItem {
        val id = UUID.randomUUID().toString()
        val mediaFile = File(mediaDir, "$id.icgt")
        val salt = getMasterSalt()

        val encryptedMedia = VaultCrypto.encryptBuffer(jpegBytes, key, salt, MediaType.PHOTO, timestamp)
        FileOutputStream(mediaFile).use { it.write(encryptedMedia) }

        var thumbFile: File? = null
        if (thumbnailBytes != null && thumbnailBytes.isNotEmpty()) {
            thumbFile = File(thumbDir, "$id.thumb.icgt")
            val encryptedThumb = VaultCrypto.encryptBuffer(thumbnailBytes, key, salt, MediaType.PHOTO, timestamp)
            FileOutputStream(thumbFile).use { it.write(encryptedThumb) }
        }

        return VaultMediaItem(
            id = id,
            file = mediaFile,
            mediaType = MediaType.PHOTO,
            timestamp = timestamp,
            plaintextLength = jpegBytes.size.toLong(),
            thumbnailFile = thumbFile
        )
    }

    fun saveEncryptedVideo(
        tempVideoFile: File,
        key: ByteArray,
        thumbnailBytes: ByteArray? = null,
        timestamp: Long = System.currentTimeMillis()
    ): VaultMediaItem {
        val id = UUID.randomUUID().toString()
        val mediaFile = File(mediaDir, "$id.icgt")
        val salt = getMasterSalt()

        try {
            VaultCrypto.encryptFile(
                inputFile = tempVideoFile,
                outputFile = mediaFile,
                key = key,
                salt = salt,
                mediaType = MediaType.VIDEO,
                timestamp = timestamp
            )
        } finally {
            // Guarantee immediate forensic shredding of unencrypted temp video
            SecureShredder.shred(tempVideoFile)
        }

        var thumbFile: File? = null
        if (thumbnailBytes != null && thumbnailBytes.isNotEmpty()) {
            thumbFile = File(thumbDir, "$id.thumb.icgt")
            val encryptedThumb = VaultCrypto.encryptBuffer(thumbnailBytes, key, salt, MediaType.PHOTO, timestamp)
            FileOutputStream(thumbFile).use { it.write(encryptedThumb) }
        }

        val header = readHeader(mediaFile)
        return VaultMediaItem(
            id = id,
            file = mediaFile,
            mediaType = MediaType.VIDEO,
            timestamp = timestamp,
            plaintextLength = header.plaintextLength,
            thumbnailFile = thumbFile
        )
    }

    fun listMedia(): List<VaultMediaItem> {
        val files = mediaDir.listFiles { _, name -> name.endsWith(".icgt") } ?: return emptyList()
        val items = mutableListOf<VaultMediaItem>()

        for (file in files) {
            try {
                val header = readHeader(file)
                val baseName = file.nameWithoutExtension
                val thumbFile = File(thumbDir, "$baseName.thumb.icgt").takeIf { it.exists() }
                items.add(
                    VaultMediaItem(
                        id = baseName,
                        file = file,
                        mediaType = header.mediaType,
                        timestamp = header.timestamp,
                        plaintextLength = header.plaintextLength,
                        thumbnailFile = thumbFile
                    )
                )
            } catch (e: Exception) {
                // Ignore corrupted or partially written files
            }
        }

        // Sort descending by timestamp (newest first)
        return items.sortedByDescending { it.timestamp }
    }

    fun deleteItem(item: VaultMediaItem) {
        SecureShredder.shred(item.file)
        item.thumbnailFile?.let { SecureShredder.shred(it) }
    }

    fun exportDecrypted(item: VaultMediaItem, key: ByteArray, targetFile: File) {
        if (item.mediaType == MediaType.PHOTO) {
            val encrypted = item.file.readBytes()
            val decrypted = VaultCrypto.decryptBuffer(encrypted, key)
            targetFile.writeBytes(decrypted)
        } else {
            VaultCrypto.openSeekableSource(item.file, key).use { source ->
                FileOutputStream(targetFile).use { fos ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (source.read(buffer, 0, buffer.size).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                    }
                    fos.flush()
                }
            }
        }
    }

    private fun readHeader(file: File): VaultHeader {
        FileInputStream(file).use { fis ->
            return VaultHeader.readFrom(fis)
        }
    }
}
