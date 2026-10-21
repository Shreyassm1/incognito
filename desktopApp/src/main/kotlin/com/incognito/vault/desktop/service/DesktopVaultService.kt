package com.incognito.vault.desktop.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.incognito.vault.crypto.MediaType
import com.incognito.vault.crypto.SecureShredder
import com.incognito.vault.crypto.VaultCrypto
import com.incognito.vault.crypto.VaultHeader
import com.incognito.vault.crypto.VaultManager
import com.incognito.vault.crypto.VaultMediaItem
import com.incognito.vault.desktop.util.ImageUtils
import com.incognito.vault.desktop.util.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

enum class GalleryFilter {
    ALL,
    PHOTOS,
    VIDEOS
}

enum class SortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
    LARGEST_FIRST,
    SMALLEST_FIRST
}

class DesktopVaultService(
    initialVaultDir: File = File(System.getProperty("user.home"), ".incognito_vault")
) {
    var vaultDir: File = initialVaultDir
        private set

    var vaultManager: VaultManager = VaultManager(initialVaultDir)
        private set

    private var masterKey: ByteArray? = null

    var isVaultInitialized by mutableStateOf(vaultManager.isInitialized())
        private set

    var isUnlocked by mutableStateOf(false)
        private set

    var mediaItems by mutableStateOf<List<VaultMediaItem>>(emptyList())
        private set

    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var currentFilter by mutableStateOf(GalleryFilter.ALL)
    var currentSortOrder by mutableStateOf(SortOrder.NEWEST_FIRST)
    var searchQuery by mutableStateOf("")

    private val thumbnailCache = ConcurrentHashMap<String, ImageBitmap>()
    private val fullPhotoCache = ConcurrentHashMap<String, ImageBitmap>()

    fun changeVaultDirectory(newDir: File) {
        if (isUnlocked) {
            lock()
        }
        vaultDir = newDir
        vaultManager = VaultManager(newDir)
        isVaultInitialized = vaultManager.isInitialized()
    }

    fun setupMasterPassword(password: CharArray): Boolean {
        return try {
            val key = vaultManager.setupMasterPassword(password)
            masterKey = key
            isVaultInitialized = true
            isUnlocked = true
            refreshMediaList()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun unlock(password: CharArray): Boolean {
        val key = vaultManager.unlock(password) ?: return false
        masterKey = key
        isUnlocked = true
        refreshMediaList()
        return true
    }

    fun lock() {
        VaultCrypto.wipe(masterKey)
        masterKey = null
        isUnlocked = false
        selectedIds = emptySet()
        thumbnailCache.clear()
        fullPhotoCache.clear()
        mediaItems = emptyList()
    }

    fun getActiveKey(): ByteArray? = masterKey

    fun refreshMediaList() {
        if (!isUnlocked) {
            mediaItems = emptyList()
            return
        }
        mediaItems = vaultManager.listMedia()
    }

    fun getFilteredAndSortedItems(): List<VaultMediaItem> {
        var list = mediaItems

        // Filter by type
        list = when (currentFilter) {
            GalleryFilter.ALL -> list
            GalleryFilter.PHOTOS -> list.filter { it.mediaType == MediaType.PHOTO }
            GalleryFilter.VIDEOS -> list.filter { it.mediaType == MediaType.VIDEO }
        }

        // Filter by search query (id or date)
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.id.lowercase().contains(q) ||
                        ImageUtils.formatTimestamp(it.timestamp).lowercase().contains(q)
            }
        }

        // Sort
        return when (currentSortOrder) {
            SortOrder.NEWEST_FIRST -> list.sortedByDescending { it.timestamp }
            SortOrder.OLDEST_FIRST -> list.sortedBy { it.timestamp }
            SortOrder.LARGEST_FIRST -> list.sortedByDescending { it.plaintextLength }
            SortOrder.SMALLEST_FIRST -> list.sortedBy { it.plaintextLength }
        }
    }

    // --- Selection Management ---
    fun toggleSelection(id: String) {
        selectedIds = if (selectedIds.contains(id)) {
            selectedIds - id
        } else {
            selectedIds + id
        }
    }

    fun selectAll() {
        selectedIds = getFilteredAndSortedItems().map { it.id }.toSet()
    }

    fun clearSelection() {
        selectedIds = emptySet()
    }

    // --- Image / Thumbnail Loading ---
    suspend fun loadThumbnail(item: VaultMediaItem): ImageBitmap? = withContext(Dispatchers.IO) {
        thumbnailCache[item.id]?.let { return@withContext it }
        val key = masterKey ?: return@withContext null

        try {
            // If item has a saved thumbnail file, decrypt it
            val thumbFile = item.thumbnailFile
            if (thumbFile != null && thumbFile.exists()) {
                val encrypted = thumbFile.readBytes()
                val decrypted = VaultCrypto.decryptBuffer(encrypted, key)
                val bitmap = ImageUtils.bytesToComposeImage(decrypted)
                if (bitmap != null) {
                    thumbnailCache[item.id] = bitmap
                    return@withContext bitmap
                }
            }

            // Otherwise, derive thumbnail on the fly
            if (item.mediaType == MediaType.PHOTO) {
                val encrypted = item.file.readBytes()
                val decrypted = VaultCrypto.decryptBuffer(encrypted, key)
                val buffered = ImageUtils.bytesToBufferedImage(decrypted) ?: return@withContext null
                val thumbBytes = ImageUtils.createThumbnailBytes(buffered)
                val bitmap = ImageUtils.bytesToComposeImage(thumbBytes)
                if (bitmap != null) {
                    thumbnailCache[item.id] = bitmap
                }
                return@withContext bitmap
            } else {
                val thumbBytes = VideoUtils.extractVideoThumbnail(item, key)
                if (thumbBytes != null) {
                    val bitmap = ImageUtils.bytesToComposeImage(thumbBytes)
                    if (bitmap != null) {
                        thumbnailCache[item.id] = bitmap
                    }
                    return@withContext bitmap
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun loadFullPhoto(item: VaultMediaItem): ImageBitmap? = withContext(Dispatchers.IO) {
        if (item.mediaType != MediaType.PHOTO) return@withContext null
        fullPhotoCache[item.id]?.let { return@withContext it }
        val key = masterKey ?: return@withContext null

        try {
            val encrypted = item.file.readBytes()
            val decrypted = VaultCrypto.decryptBuffer(encrypted, key)
            val bitmap = ImageUtils.bytesToComposeImage(decrypted)
            if (bitmap != null) {
                fullPhotoCache[item.id] = bitmap
            }
            return@withContext bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- Media Ingestion / Import ---
    suspend fun importFiles(
        files: List<File>,
        shredOriginal: Boolean,
        onProgress: (current: Int, total: Int, name: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val key = masterKey ?: return@withContext

        files.forEachIndexed { index, file ->
            onProgress(index + 1, files.size, file.name)
            val name = file.name.lowercase()
            val isVideo = name.endsWith(".mp4") || name.endsWith(".mkv") ||
                    name.endsWith(".mov") || name.endsWith(".avi") || name.endsWith(".webm")

            if (isVideo) {
                val id = UUID.randomUUID().toString()
                val mediaFile = File(vaultManager.mediaDir, "$id.icgt")
                val salt = vaultManager.getMasterSalt()

                // Encrypt directly into vault media
                VaultCrypto.encryptFile(
                    inputFile = file,
                    outputFile = mediaFile,
                    key = key,
                    salt = salt,
                    mediaType = MediaType.VIDEO
                )

                // Try generating thumbnail
                val thumbItem = VaultMediaItem(
                    id = id,
                    file = mediaFile,
                    mediaType = MediaType.VIDEO,
                    timestamp = System.currentTimeMillis(),
                    plaintextLength = file.length()
                )
                val thumbBytes = VideoUtils.extractVideoThumbnail(thumbItem, key)
                if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                    val thumbFile = File(vaultManager.thumbDir, "$id.thumb.icgt")
                    val encryptedThumb = VaultCrypto.encryptBuffer(thumbBytes, key, salt, MediaType.PHOTO)
                    thumbFile.writeBytes(encryptedThumb)
                }

                if (shredOriginal) {
                    SecureShredder.shred(file)
                }
            } else {
                // Photo
                val fileBytes = file.readBytes()
                val buffered = ImageUtils.bytesToBufferedImage(fileBytes)
                val thumbBytes = if (buffered != null) {
                    ImageUtils.createThumbnailBytes(buffered)
                } else null

                vaultManager.saveEncryptedPhoto(
                    jpegBytes = fileBytes,
                    key = key,
                    thumbnailBytes = thumbBytes
                )

                if (shredOriginal) {
                    SecureShredder.shred(file)
                }
            }
        }

        refreshMediaList()
    }

    // --- Webcam Capture ---
    suspend fun saveWebcamPhoto(image: BufferedImage): VaultMediaItem = withContext(Dispatchers.IO) {
        val key = masterKey ?: throw IllegalStateException("Vault is locked")

        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", baos)
        val jpegBytes = baos.toByteArray()

        val thumbBytes = ImageUtils.createThumbnailBytes(image)
        val item = vaultManager.saveEncryptedPhoto(
            jpegBytes = jpegBytes,
            key = key,
            thumbnailBytes = thumbBytes
        )

        refreshMediaList()
        item
    }

    // --- Export Decrypted ---
    suspend fun exportItem(item: VaultMediaItem, destinationDir: File): File = withContext(Dispatchers.IO) {
        val key = masterKey ?: throw IllegalStateException("Vault is locked")
        val ext = if (item.mediaType == MediaType.PHOTO) "jpg" else "mp4"
        val timestampStr = ImageUtils.formatTimestamp(item.timestamp).replace(" ", "_").replace(":", "-").replace("•", "_")
        val targetName = "Decrypted_${timestampStr}_${item.id.take(8)}.$ext"
        val targetFile = File(destinationDir, targetName)

        vaultManager.exportDecrypted(item, key, targetFile)
        targetFile
    }

    suspend fun exportSelected(destinationDir: File): Int = withContext(Dispatchers.IO) {
        val selectedItems = mediaItems.filter { selectedIds.contains(it.id) }
        selectedItems.forEach { item ->
            exportItem(item, destinationDir)
        }
        selectedItems.size
    }

    // --- Delete / Shred ---
    suspend fun deleteItems(items: List<VaultMediaItem>) = withContext(Dispatchers.IO) {
        items.forEach { item ->
            vaultManager.deleteItem(item)
            thumbnailCache.remove(item.id)
            fullPhotoCache.remove(item.id)
        }
        selectedIds = selectedIds - items.map { it.id }.toSet()
        refreshMediaList()
    }

    suspend fun deleteSelected() {
        val selected = mediaItems.filter { selectedIds.contains(it.id) }
        deleteItems(selected)
    }
}
