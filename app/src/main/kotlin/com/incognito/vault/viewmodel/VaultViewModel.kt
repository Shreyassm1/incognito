package com.incognito.vault.viewmodel

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.incognito.vault.crypto.MediaType
import com.incognito.vault.crypto.VaultCrypto
import com.incognito.vault.crypto.VaultManager
import com.incognito.vault.crypto.VaultMediaItem
import com.incognito.vault.media.MediaUtils
import com.incognito.vault.media.ThumbnailCache
import com.incognito.vault.security.BiometricHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.pow

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val vaultDir = File(application.filesDir, "vault")
    val vaultManager = VaultManager(vaultDir)
    val biometricHelper = BiometricHelper(application)

    private val _isSetup = MutableStateFlow(vaultManager.isInitialized())
    val isSetup: StateFlow<Boolean> = _isSetup.asStateFlow()

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _mediaList = MutableStateFlow<List<VaultMediaItem>>(emptyList())
    val mediaList: StateFlow<List<VaultMediaItem>> = _mediaList.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    private val _lockoutSeconds = MutableStateFlow(0)
    val lockoutSeconds: StateFlow<Int> = _lockoutSeconds.asStateFlow()

    private val _biometricAvailable = MutableStateFlow(biometricHelper.isBiometricAvailable())
    val biometricAvailable: StateFlow<Boolean> = _biometricAvailable.asStateFlow()

    private val _biometricSaved = MutableStateFlow(biometricHelper.isBiometricEnrolledAndSaved())
    val biometricSaved: StateFlow<Boolean> = _biometricSaved.asStateFlow()

    private val _pendingImportUris = MutableStateFlow<List<Uri>>(emptyList())
    val pendingImportUris: StateFlow<List<Uri>> = _pendingImportUris.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // Master key held strictly in memory while unlocked. Wiped to 0x00 on lock!
    private var activeMasterKey: ByteArray? = null
    private var lockoutJob: Job? = null

    init {
        refreshVaultStatus()
    }

    fun getActiveKey(): ByteArray? = activeMasterKey

    fun refreshVaultStatus() {
        val initialized = vaultManager.isInitialized()
        _isSetup.value = initialized
        _biometricAvailable.value = biometricHelper.isBiometricAvailable()
        _biometricSaved.value = biometricHelper.isBiometricEnrolledAndSaved()
    }

    fun setupMasterPassword(password: String, enableBiometrics: Boolean = true): Boolean {
        if (password.length < 6) {
            emitToast("Password must be at least 6 characters")
            return false
        }
        val chars = password.toCharArray()
        try {
            val key = vaultManager.setupMasterPassword(chars)
            activeMasterKey = key.copyOf()

            if (enableBiometrics && biometricHelper.isBiometricAvailable()) {
                biometricHelper.saveMasterKeyForBiometrics(key)
                _biometricSaved.value = true
            }

            _isSetup.value = true
            _isLocked.value = false
            _failedAttempts.value = 0
            refreshMediaList()
            emitToast("Vault initialized successfully")
            return true
        } catch (e: Exception) {
            emitToast("Setup failed: ${e.message}")
            return false
        } finally {
            VaultCrypto.wipe(chars)
        }
    }

    fun unlockWithPassword(password: String): Boolean {
        if (_lockoutSeconds.value > 0) {
            emitToast("Locked out. Please wait ${_lockoutSeconds.value}s")
            return false
        }

        val chars = password.toCharArray()
        try {
            val key = vaultManager.unlock(chars)
            if (key != null) {
                activeMasterKey = key
                _isLocked.value = false
                _failedAttempts.value = 0
                refreshMediaList()
                processPendingImports(getApplication())
                return true
            } else {
                handleFailedAttempt()
                return false
            }
        } finally {
            VaultCrypto.wipe(chars)
        }
    }

    fun unlockWithBiometricKey(key: ByteArray): Boolean {
        if (_lockoutSeconds.value > 0) {
            emitToast("Locked out. Please wait ${_lockoutSeconds.value}s")
            VaultCrypto.wipe(key)
            return false
        }

        // Verify key against vault canary
        val salt = vaultManager.getMasterSalt()
        val canary = VaultCrypto.computeCanary(key)
        val configFile = File(vaultDir, ".vault_config.bin")
        val expectedCanary = ByteArray(32)
        java.io.FileInputStream(configFile).use { fis ->
            fis.skip(VaultCrypto.generateSalt().size.toLong())
            fis.read(expectedCanary)
        }

        if (java.security.MessageDigest.isEqual(canary, expectedCanary)) {
            activeMasterKey = key
            _isLocked.value = false
            _failedAttempts.value = 0
            refreshMediaList()
            processPendingImports(getApplication())
            return true
        } else {
            VaultCrypto.wipe(key)
            handleFailedAttempt()
            return false
        }
    }

    private fun handleFailedAttempt() {
        val attempts = _failedAttempts.value + 1
        _failedAttempts.value = attempts
        if (attempts >= 3) {
            val delaySeconds = (2.0.pow((attempts - 3).toDouble()) * 5).toInt().coerceAtMost(60)
            startLockout(delaySeconds)
            emitToast("Too many failed attempts. Locked for ${delaySeconds}s")
        } else {
            val remaining = 3 - attempts
            emitToast("Incorrect password. $remaining attempts before cooldown.")
        }
    }

    private fun startLockout(seconds: Int) {
        lockoutJob?.cancel()
        _lockoutSeconds.value = seconds
        lockoutJob = viewModelScope.launch {
            var current = seconds
            while (current > 0) {
                delay(1000)
                current--
                _lockoutSeconds.value = current
            }
        }
    }

    /**
     * Instantly wipes master keys from memory and evicts cached thumbnails.
     */
    fun lock() {
        activeMasterKey?.let {
            VaultCrypto.wipe(it)
        }
        activeMasterKey = null
        _isLocked.value = true
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
        ThumbnailCache.clear()
    }

    fun refreshMediaList() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = vaultManager.listMedia()
            _mediaList.value = list
        }
    }

    fun savePhoto(jpegBytes: ByteArray, thumbnailBytes: ByteArray? = null): VaultMediaItem? {
        val key = activeMasterKey ?: return null
        return try {
            val item = vaultManager.saveEncryptedPhoto(jpegBytes, key, thumbnailBytes)
            refreshMediaList()
            item
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveVideo(tempVideoFile: File, thumbnailBytes: ByteArray? = null): VaultMediaItem? {
        val key = activeMasterKey ?: return null
        return try {
            val item = vaultManager.saveEncryptedVideo(tempVideoFile, key, thumbnailBytes)
            refreshMediaList()
            item
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteItem(item: VaultMediaItem) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultManager.deleteItem(item)
            refreshMediaList()
            emitToast("File forensically shredded")
        }
    }

    fun toggleSelectionMode(enable: Boolean) {
        _isSelectionMode.value = enable
        if (!enable) {
            _selectedIds.value = emptySet()
        }
    }

    fun toggleSelectItem(id: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedIds.value = current
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        } else {
            _isSelectionMode.value = true
        }
    }

    fun selectAll() {
        _selectedIds.value = _mediaList.value.map { it.id }.toSet()
        _isSelectionMode.value = true
    }

    fun deleteSelected() {
        val toDelete = _selectedIds.value
        if (toDelete.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val items = _mediaList.value.filter { toDelete.contains(it.id) }
            items.forEach { vaultManager.deleteItem(it) }
            _selectedIds.value = emptySet()
            _isSelectionMode.value = false
            refreshMediaList()
            emitToast("${items.size} files forensically shredded")
        }
    }

    fun addPendingImports(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val current = _pendingImportUris.value.toMutableList()
        current.addAll(uris)
        _pendingImportUris.value = current
        emitToast("${uris.size} shared items queued. Unlock vault to import.")
    }

    fun processPendingImports(context: Context) {
        val pending = _pendingImportUris.value
        if (pending.isEmpty() || activeMasterKey == null) return
        _pendingImportUris.value = emptyList()
        importMediaFromUris(context, pending) { _ -> }
    }

    fun importMediaFromUris(
        context: Context,
        uris: List<Uri>,
        onCompleted: (List<Uri>) -> Unit
    ) {
        val key = activeMasterKey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val importedUris = mutableListOf<Uri>()
            val contentResolver = context.contentResolver

            for (uri in uris) {
                try {
                    val mimeType = contentResolver.getType(uri) ?: ""
                    val isVideo = mimeType.startsWith("video") || uri.toString().endsWith(".mp4", ignoreCase = true)

                    if (isVideo) {
                        val tempFile = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}.mp4")
                        contentResolver.openInputStream(uri)?.use { input ->
                            java.io.FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        val thumbBytes = MediaUtils.createVideoThumbnail(tempFile)
                        vaultManager.saveEncryptedVideo(tempFile, key, thumbBytes)
                        importedUris.add(uri)
                    } else {
                        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null && bytes.isNotEmpty()) {
                            val thumbBytes = MediaUtils.createThumbnail(bytes)
                            vaultManager.saveEncryptedPhoto(bytes, key, thumbBytes)
                            importedUris.add(uri)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            refreshMediaList()
            withContext(Dispatchers.Main) {
                emitToast("${importedUris.size} items securely imported to vault")
                onCompleted(importedUris)
            }
        }
    }

    fun createDeleteRequest(context: Context, uris: List<Uri>): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(context.contentResolver, uris)
        } else {
            null
        }
    }

    fun exportItem(context: Context, item: VaultMediaItem): Boolean {
        val key = activeMasterKey ?: return false
        return try {
            val contentResolver = context.contentResolver
            val isPhoto = item.mediaType == MediaType.PHOTO
            val mimeType = if (isPhoto) "image/jpeg" else "video/mp4"
            val collection = if (isPhoto) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            }

            val filename = if (isPhoto) "IMG_${item.timestamp}.jpg" else "VID_${item.timestamp}.mp4"
            val relativeDir = if (isPhoto) {
                Environment.DIRECTORY_PICTURES + "/IncognitoVault"
            } else {
                Environment.DIRECTORY_MOVIES + "/IncognitoVault"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = contentResolver.insert(collection, values) ?: return false

            contentResolver.openOutputStream(uri)?.use { out ->
                if (isPhoto) {
                    val encrypted = item.file.readBytes()
                    val decrypted = VaultCrypto.decryptBuffer(encrypted, key)
                    out.write(decrypted)
                } else {
                    VaultCrypto.openSeekableSource(item.file, key).use { source ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (source.read(buffer, 0, buffer.size).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                        }
                    }
                }
                out.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }

            emitToast("Exported to $relativeDir/$filename")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            emitToast("Export failed: ${e.message}")
            false
        }
    }

    private val _isPickerMode = MutableStateFlow(false)
    val isPickerMode: StateFlow<Boolean> = _isPickerMode.asStateFlow()

    fun setPickerMode(enabled: Boolean) {
        _isPickerMode.value = enabled
    }

    fun shareItem(context: Context, item: VaultMediaItem) {
        val key = activeMasterKey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tempDir = File(context.cacheDir, "shared_temp").apply { mkdirs() }
                val isPhoto = item.mediaType == MediaType.PHOTO
                val ext = if (isPhoto) "jpg" else "mp4"
                val mimeType = if (isPhoto) "image/jpeg" else "video/mp4"
                val tempFile = File(tempDir, "vault_share_${System.currentTimeMillis()}.$ext")

                tempFile.outputStream().use { out ->
                    if (isPhoto) {
                        val encrypted = item.file.readBytes()
                        val decrypted = VaultCrypto.decryptBuffer(encrypted, key)
                        try {
                            out.write(decrypted)
                        } finally {
                            VaultCrypto.wipe(decrypted)
                            VaultCrypto.wipe(encrypted)
                        }
                    } else {
                        VaultCrypto.openSeekableSource(item.file, key).use { source ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            while (source.read(buffer, 0, buffer.size).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                    out.flush()
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    val chooser = Intent.createChooser(shareIntent, "Share to WhatsApp / Apps")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emitToast("Failed to share: ${e.message}")
            }
        }
    }

    fun shareSelected(context: Context) {
        val selected = _selectedIds.value
        val key = activeMasterKey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tempDir = File(context.cacheDir, "shared_temp").apply { mkdirs() }
                val items = _mediaList.value.filter { selected.contains(it.id) }
                val uris = ArrayList<Uri>()

                items.forEach { item ->
                    val isPhoto = item.mediaType == MediaType.PHOTO
                    val ext = if (isPhoto) "jpg" else "mp4"
                    val tempFile = File(tempDir, "vault_share_${item.id}.$ext")
                    tempFile.outputStream().use { out ->
                        if (isPhoto) {
                            val encrypted = item.file.readBytes()
                            val decrypted = VaultCrypto.decryptBuffer(encrypted, key)
                            try {
                                out.write(decrypted)
                            } finally {
                                VaultCrypto.wipe(decrypted)
                                VaultCrypto.wipe(encrypted)
                            }
                        } else {
                            VaultCrypto.openSeekableSource(item.file, key).use { source ->
                                val buffer = ByteArray(64 * 1024)
                                var read: Int
                                while (source.read(buffer, 0, buffer.size).also { read = it } != -1) {
                                    out.write(buffer, 0, read)
                                }
                            }
                        }
                        out.flush()
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                    uris.add(uri)
                }

                if (uris.isNotEmpty()) {
                    val shareIntent = if (uris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, uris[0])
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "image/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        val chooser = Intent.createChooser(shareIntent, "Share to WhatsApp / Apps")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emitToast("Failed to share selected items: ${e.message}")
            }
        }
    }

    fun pickItem(activity: Activity, item: VaultMediaItem) {
        val key = activeMasterKey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tempDir = File(activity.cacheDir, "shared_temp").apply { mkdirs() }
                val isPhoto = item.mediaType == MediaType.PHOTO
                val ext = if (isPhoto) "jpg" else "mp4"
                val tempFile = File(tempDir, "vault_pick_${System.currentTimeMillis()}.$ext")

                tempFile.outputStream().use { out ->
                    if (isPhoto) {
                        val encrypted = item.file.readBytes()
                        val decrypted = VaultCrypto.decryptBuffer(encrypted, key)
                        try {
                            out.write(decrypted)
                        } finally {
                            VaultCrypto.wipe(decrypted)
                            VaultCrypto.wipe(encrypted)
                        }
                    } else {
                        VaultCrypto.openSeekableSource(item.file, key).use { source ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            while (source.read(buffer, 0, buffer.size).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                    out.flush()
                }

                val uri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    tempFile
                )

                val resultIntent = Intent().apply {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    activity.setResult(Activity.RESULT_OK, resultIntent)
                    activity.finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emitToast("Failed to pick item: ${e.message}")
            }
        }
    }

    fun exportSelected(context: Context) {
        val selected = _selectedIds.value
        val key = activeMasterKey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val items = _mediaList.value.filter { selected.contains(it.id) }
            var successCount = 0
            items.forEach { item ->
                if (exportItem(context, item)) {
                    successCount++
                }
            }
            _selectedIds.value = emptySet()
            _isSelectionMode.value = false
            emitToast("Successfully exported $successCount files")
        }
    }

    private fun emitToast(msg: String) {
        viewModelScope.launch {
            _toastEvent.emit(msg)
        }
    }
}
