package com.incognito.vault.desktop.util

import com.incognito.vault.crypto.SeekableEncryptedSource
import com.incognito.vault.crypto.VaultCrypto
import com.incognito.vault.crypto.VaultMediaItem
import org.jcodec.api.FrameGrab
import org.jcodec.common.io.SeekableByteChannel
import org.jcodec.scale.AWTUtil
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer

class EncryptedSourceByteChannel(
    private val source: SeekableEncryptedSource
) : SeekableByteChannel {

    private var open: Boolean = true

    override fun read(dst: ByteBuffer): Int {
        if (!open) throw IllegalStateException("Channel closed")
        if (!dst.hasRemaining()) return 0

        val toRead = dst.remaining()
        val temp = ByteArray(toRead.coerceAtMost(64 * 1024))
        val read = source.read(temp, 0, temp.size)
        if (read > 0) {
            dst.put(temp, 0, read)
        }
        return read
    }

    override fun write(src: ByteBuffer): Int {
        throw UnsupportedOperationException("Vault seekable source is read-only")
    }

    override fun position(): Long = source.currentPosition

    override fun setPosition(newPosition: Long): SeekableByteChannel {
        if (!open) throw IllegalStateException("Channel closed")
        source.seek(newPosition)
        return this
    }

    override fun size(): Long = source.plaintextLength

    override fun truncate(size: Long): SeekableByteChannel {
        throw UnsupportedOperationException("Vault seekable source is read-only")
    }

    override fun isOpen(): Boolean = open

    override fun close() {
        if (open) {
            open = false
            source.close()
        }
    }
}

object VideoUtils {

    /**
     * Extracts a thumbnail frame from an encrypted video using in-memory random-access decryption.
     * Zero decrypted temp files on disk.
     */
    fun extractVideoThumbnail(item: VaultMediaItem, key: ByteArray): ByteArray? {
        return try {
            val source = VaultCrypto.openSeekableSource(item.file, key)
            val channel = EncryptedSourceByteChannel(source)
            channel.use { ch ->
                val grab = FrameGrab.createFrameGrab(ch)
                val picture = grab.nativeFrame ?: return null
                val buffered = AWTUtil.toBufferedImage(picture)
                ImageUtils.createThumbnailBytes(buffered)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts a frame from an encrypted video at the given second offset.
     */
    fun grabFrameAtSecond(file: File, key: ByteArray, second: Double): BufferedImage? {
        return try {
            val source = VaultCrypto.openSeekableSource(file, key)
            val channel = EncryptedSourceByteChannel(source)
            channel.use { ch ->
                val grab = FrameGrab.createFrameGrab(ch)
                if (second > 0) {
                    grab.seekToSecondPrecise(second)
                }
                val picture = grab.nativeFrame ?: return null
                AWTUtil.toBufferedImage(picture)
            }
        } catch (e: Exception) {
            null
        }
    }
}
