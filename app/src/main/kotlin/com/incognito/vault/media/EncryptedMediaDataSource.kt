package com.incognito.vault.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.incognito.vault.crypto.SeekableEncryptedSource
import com.incognito.vault.crypto.VaultCrypto
import java.io.File
import java.io.IOException

@OptIn(UnstableApi::class)
class EncryptedMediaDataSource(
    private val keyProvider: () -> ByteArray?
) : BaseDataSource(/* isNetwork = */ false) {

    private var currentDataSpec: DataSpec? = null
    private var seekableSource: SeekableEncryptedSource? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        transferInitializing(dataSpec)

        val uri = dataSpec.uri
        val path = uri.path ?: throw IOException("Invalid URI for encrypted media: $uri")
        val file = File(path)
        if (!file.exists()) {
            throw IOException("Vault file not found: $path")
        }

        val key = keyProvider() ?: throw IOException("Vault master key is locked / unavailable")

        try {
            val source = VaultCrypto.openSeekableSource(file, key)
            this.seekableSource = source

            val totalLength = source.plaintextLength
            if (dataSpec.position > totalLength) {
                source.close()
                this.seekableSource = null
                throw IOException("Seek position ${dataSpec.position} exceeds plaintext length $totalLength")
            }

            source.seek(dataSpec.position)

            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                totalLength - dataSpec.position
            }

            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            seekableSource?.close()
            seekableSource = null
            opened = false
            throw IOException("Failed opening encrypted media data source: ${e.message}", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val source = seekableSource ?: return C.RESULT_END_OF_INPUT
        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            minOf(bytesRemaining, length.toLong()).toInt()
        } else {
            length
        }

        val bytesRead = source.read(buffer, offset, bytesToRead)
        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }

        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = currentDataSpec?.uri

    override fun close() {
        if (opened) {
            opened = false
            try {
                seekableSource?.close()
            } finally {
                seekableSource = null
                transferEnded()
            }
        }
    }

    class Factory(
        private val keyProvider: () -> ByteArray?
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return EncryptedMediaDataSource(keyProvider)
        }
    }
}
