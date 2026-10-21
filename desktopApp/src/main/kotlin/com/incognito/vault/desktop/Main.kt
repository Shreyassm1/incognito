package com.incognito.vault.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.incognito.vault.crypto.VaultMediaItem
import com.incognito.vault.desktop.service.DesktopVaultService
import com.incognito.vault.desktop.theme.VaultTheme
import com.incognito.vault.desktop.ui.auth.SetupScreen
import com.incognito.vault.desktop.ui.auth.UnlockScreen
import com.incognito.vault.desktop.ui.capture.WebcamCaptureDialog
import com.incognito.vault.desktop.ui.dialogs.ImportDialog
import com.incognito.vault.desktop.ui.gallery.GalleryScreen
import com.incognito.vault.desktop.ui.viewer.MediaViewerDialog
import com.incognito.vault.desktop.util.NativeDialogs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1150.dp, 780.dp))
    val service = remember { DesktopVaultService() }

    // Dialog & overlay states
    var activeViewerItem by remember { mutableStateOf<VaultMediaItem?>(null) }
    var showWebcamDialog by remember { mutableStateOf(false) }
    var filesForImportDialog by remember { mutableStateOf<List<File>?>(null) }

    // Inactivity tracking: 5 minutes = 300,000 ms
    val idleTimeoutMs = 5 * 60 * 1000L
    var lastActivityTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    fun registerUserActivity() {
        lastActivityTimestamp = System.currentTimeMillis()
    }

    // Auto-lock when window is minimized
    LaunchedEffect(windowState.isMinimized) {
        if (windowState.isMinimized && service.isUnlocked) {
            service.lock()
            activeViewerItem = null
            showWebcamDialog = false
            filesForImportDialog = null
        }
    }

    // Idle timer coroutine
    LaunchedEffect(service.isUnlocked) {
        if (service.isUnlocked) {
            lastActivityTimestamp = System.currentTimeMillis()
            while (isActive) {
                delay(2000)
                val elapsed = System.currentTimeMillis() - lastActivityTimestamp
                if (elapsed >= idleTimeoutMs) {
                    service.lock()
                    activeViewerItem = null
                    showWebcamDialog = false
                    filesForImportDialog = null
                    break
                }
            }
        }
    }

    Window(
        onCloseRequest = {
            service.lock()
            exitApplication()
        },
        state = windowState,
        title = if (service.isUnlocked) "Incognito Vault [Unlocked] - PC Companion" else "Incognito Vault [Locked] - PC Companion"
    ) {
        // Register native window minimize listener
        DisposableEffect(window) {
            val listener = object : WindowAdapter() {
                override fun windowIconified(e: WindowEvent) {
                    if (service.isUnlocked) {
                        service.lock()
                        activeViewerItem = null
                        showWebcamDialog = false
                        filesForImportDialog = null
                    }
                }
            }
            window.addWindowListener(listener)
            onDispose {
                window.removeWindowListener(listener)
            }
        }

        VaultTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .onPointerEvent(PointerEventType.Move) { registerUserActivity() }
                    .onPointerEvent(PointerEventType.Press) { registerUserActivity() }
                    .onPointerEvent(PointerEventType.Scroll) { registerUserActivity() }
                    .onKeyEvent { event ->
                        registerUserActivity()
                        // Ctrl+L shortcut to lock immediately
                        if (event.isCtrlPressed && event.key == Key.L && event.type == KeyEventType.KeyDown) {
                            if (service.isUnlocked) {
                                service.lock()
                                activeViewerItem = null
                                showWebcamDialog = false
                                filesForImportDialog = null
                                true
                            } else false
                        } else false
                    }
            ) {
                // Navigation flow
                when {
                    !service.isVaultInitialized -> {
                        SetupScreen(
                            service = service,
                            onSuccess = { /* service.isUnlocked is now true */ }
                        )
                    }

                    !service.isUnlocked -> {
                        UnlockScreen(
                            service = service,
                            onUnlocked = { /* unlocked */ }
                        )
                    }

                    else -> {
                        GalleryScreen(
                            service = service,
                            onOpenItem = { item -> activeViewerItem = item },
                            onImportClick = {
                                val picked = NativeDialogs.pickMediaFiles(window)
                                if (picked.isNotEmpty()) {
                                    filesForImportDialog = picked
                                }
                            },
                            onWebcamClick = { showWebcamDialog = true },
                            onLockClick = {
                                service.lock()
                                activeViewerItem = null
                                showWebcamDialog = false
                                filesForImportDialog = null
                            }
                        )
                    }
                }

                // Fullscreen / Lightbox Media Viewer Modal
                if (activeViewerItem != null && service.isUnlocked) {
                    MediaViewerDialog(
                        initialItem = activeViewerItem!!,
                        items = service.getFilteredAndSortedItems(),
                        service = service,
                        onDismiss = { activeViewerItem = null }
                    )
                }

                // Webcam Capture Dialog Modal
                if (showWebcamDialog && service.isUnlocked) {
                    WebcamCaptureDialog(
                        service = service,
                        onDismiss = { showWebcamDialog = false }
                    )
                }

                // Import Dialog Modal with Shred Checkbox
                if (filesForImportDialog != null && service.isUnlocked) {
                    ImportDialog(
                        initialFiles = filesForImportDialog!!,
                        service = service,
                        onDismiss = { filesForImportDialog = null },
                        onImportComplete = { filesForImportDialog = null }
                    )
                }
            }
        }
    }
}
