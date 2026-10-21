package com.incognito.vault.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.incognito.vault.crypto.MediaType
import com.incognito.vault.crypto.VaultCrypto
import com.incognito.vault.crypto.VaultMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailCache {
    // Max 30 MB in-memory LRU cache for decrypted thumbnails
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtMost(30 * 1024)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(id: String): Bitmap? = synchronized(memoryCache) {
        memoryCache.get(id)
    }

    fun put(id: String, bitmap: Bitmap) = synchronized(memoryCache) {
        memoryCache.put(id, bitmap)
    }

    fun clear() = synchronized(memoryCache) {
        memoryCache.evictAll()
    }

    suspend fun loadThumbnail(
        item: VaultMediaItem,
        key: ByteArray
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cached = get(item.id)
        if (cached != null) return@withContext cached

        try {
            val thumbFile = item.thumbnailFile
            if (thumbFile != null && thumbFile.exists()) {
                val encrypted = thumbFile.readBytes()
                var decrypted: ByteArray? = null
                try {
                    decrypted = VaultCrypto.decryptBuffer(encrypted, key)
                    val bitmap = BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
                    if (bitmap != null) {
                        put(item.id, bitmap)
                        return@withContext bitmap
                    }
                } finally {
                    decrypted?.let { VaultCrypto.wipe(it) }
                    VaultCrypto.wipe(encrypted)
                }
            }

            // For photos without a separate thumb, decrypt photo buffer directly into memory and sample down
            if (item.mediaType == MediaType.PHOTO && item.file.exists()) {
                val encrypted = item.file.readBytes()
                var decrypted: ByteArray? = null
                try {
                    decrypted = VaultCrypto.decryptBuffer(encrypted, key)

                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size, options)

                    val sampleSize = calculateInSampleSize(options, 256, 256)
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val bitmap = BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size, decodeOptions)
                    if (bitmap != null) {
                        put(item.id, bitmap)
                        return@withContext bitmap
                    }
                } finally {
                    decrypted?.let { VaultCrypto.wipe(it) }
                    VaultCrypto.wipe(encrypted)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
