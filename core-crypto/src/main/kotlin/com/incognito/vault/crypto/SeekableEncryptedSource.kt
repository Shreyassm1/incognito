package com.incognito.vault.crypto

import java.io.Closeable

/**
 * Interface enabling zero-leak random-access decrypted streaming directly in memory.
 * Used by Media3/ExoPlayer (Android) and Desktop video previewers.
 */
interface SeekableEncryptedSource : Closeable {
    val header: VaultHeader
    val plaintextLength: Long
    val currentPosition: Long

    fun seek(position: Long)
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}
