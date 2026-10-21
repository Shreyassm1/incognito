package com.incognito.vault.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.SecureRandom
import kotlin.random.Random

class VaultCryptoTest {

    @Test
    fun testKeyDerivationAndCanary() {
        val password = "StrongMasterPassword#2026".toCharArray()
        val wrongPassword = "WrongPassword#123".toCharArray()
        val salt = VaultCrypto.generateSalt()

        val key = VaultCrypto.deriveKey(password, salt)
        val canary = VaultCrypto.computeCanary(key)

        assertTrue(VaultCrypto.verifyCanary(key, canary), "Canary should verify with correct key")

        val wrongKey = VaultCrypto.deriveKey(wrongPassword, salt)
        assertFalse(VaultCrypto.verifyCanary(wrongKey, canary), "Canary should reject invalid key")

        VaultCrypto.wipe(key)
        VaultCrypto.wipe(wrongKey)
    }

    @Test
    fun testPhotoBufferEncryptionRoundTrip() {
        val password = "MyPhotoPassword".toCharArray()
        val salt = VaultCrypto.generateSalt()
        val key = VaultCrypto.deriveKey(password, salt)

        val originalData = "FakeJPEGDataContent_RandomBytes".toByteArray(Charsets.UTF_8)
        val encrypted = VaultCrypto.encryptBuffer(originalData, key, salt, MediaType.PHOTO)

        assertNotNull(encrypted)
        assertTrue(encrypted.size > originalData.size, "Encrypted data includes header and GCM tag")

        val decrypted = VaultCrypto.decryptBuffer(encrypted, key)
        assertArrayEquals(originalData, decrypted, "Decrypted photo data must match original")

        VaultCrypto.wipe(key)
    }

    @Test
    fun testChunkedFileEncryptionAndSeeking(@TempDir tempDir: File) {
        val password = "VideoPassword".toCharArray()
        val salt = VaultCrypto.generateSalt()
        val key = VaultCrypto.deriveKey(password, salt)

        // Create a 250 KB file (spanning multiple 64KB chunks: 4 chunks)
        val plaintext = ByteArray(250 * 1024)
        Random.nextBytes(plaintext)

        val inputFile = File(tempDir, "sample_video.mp4")
        inputFile.writeBytes(plaintext)

        val encryptedFile = File(tempDir, "sample_video.icgt")
        VaultCrypto.encryptFile(
            inputFile = inputFile,
            outputFile = encryptedFile,
            key = key,
            salt = salt,
            mediaType = MediaType.VIDEO,
            chunkSize = 64 * 1024
        )

        assertTrue(encryptedFile.exists())
        assertTrue(encryptedFile.length() > plaintext.size)

        // Read using SeekableEncryptedSource and test random-access seeking
        VaultCrypto.openSeekableSource(encryptedFile, key).use { source ->
            assertEquals(plaintext.size.toLong(), source.plaintextLength)

            // 1. Read beginning
            val buf1 = ByteArray(1024)
            val read1 = source.read(buf1, 0, 1024)
            assertEquals(1024, read1)
            val expectedStart = plaintext.copyOfRange(0, 1024)
            assertArrayEquals(expectedStart, buf1)

            // 2. Seek to middle of chunk 2 (e.g. byte 140,000)
            val seekPos = 140_000L
            source.seek(seekPos)
            assertEquals(seekPos, source.currentPosition)

            val buf2 = ByteArray(2048)
            val read2 = source.read(buf2, 0, 2048)
            assertEquals(2048, read2)
            val expectedMid = plaintext.copyOfRange(seekPos.toInt(), (seekPos + 2048).toInt())
            assertArrayEquals(expectedMid, buf2)

            // 3. Seek near end of file across chunk boundary to EOF
            val nearEnd = plaintext.size - 500L
            source.seek(nearEnd)
            val buf3 = ByteArray(1000)
            val read3 = source.read(buf3, 0, 1000)
            assertEquals(500, read3, "Should read remaining 500 bytes to EOF")
            val expectedEnd = plaintext.copyOfRange(nearEnd.toInt(), plaintext.size)
            val actualEnd = buf3.copyOfRange(0, 500)
            assertArrayEquals(expectedEnd, actualEnd)

            // 4. Next read should return -1 (EOF)
            val eofRead = source.read(buf3, 0, 10)
            assertEquals(-1, eofRead)
        }

        VaultCrypto.wipe(key)
    }

    @Test
    fun testSecureShredder(@TempDir tempDir: File) {
        val testFile = File(tempDir, "sensitive_temp_video.mp4")
        testFile.writeBytes(ByteArray(100 * 1024) { 0xFF.toByte() })
        assertTrue(testFile.exists())

        SecureShredder.shred(testFile)
        assertFalse(testFile.exists(), "Shredded file must be deleted")
    }

    @Test
    fun testVaultManagerLifecycle(@TempDir tempDir: File) {
        val vaultDir = File(tempDir, "vault")
        val manager = VaultManager(vaultDir)

        assertFalse(manager.isInitialized())

        val password = "MasterKey@Pass1".toCharArray()
        val key = manager.setupMasterPassword(password)
        assertTrue(manager.isInitialized())

        // Unlock test
        val unlockedKey = manager.unlock(password)
        assertNotNull(unlockedKey)
        assertArrayEquals(key, unlockedKey)

        // Wrong password unlock test
        val failKey = manager.unlock("WrongPassword".toCharArray())
        assertNull(failKey)

        // Save photo
        val photoBytes = "FakePhotoBytes123456789".toByteArray()
        val thumbBytes = "ThumbBytes123".toByteArray()
        val item = manager.saveEncryptedPhoto(photoBytes, key, thumbBytes)
        assertNotNull(item)
        assertEquals(MediaType.PHOTO, item.mediaType)

        // List media
        val mediaList = manager.listMedia()
        assertEquals(1, mediaList.size)
        assertEquals(item.id, mediaList[0].id)

        // Export decrypted
        val exportedFile = File(tempDir, "exported.jpg")
        manager.exportDecrypted(mediaList[0], key, exportedFile)
        assertArrayEquals(photoBytes, exportedFile.readBytes())

        // Delete item
        manager.deleteItem(mediaList[0])
        val afterDeleteList = manager.listMedia()
        assertTrue(afterDeleteList.isEmpty())

        VaultCrypto.wipe(key)
        VaultCrypto.wipe(unlockedKey)
    }
}
