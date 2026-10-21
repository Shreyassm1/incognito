package com.incognito.vault.desktop.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.incognito.vault.crypto.MediaType
import com.incognito.vault.crypto.VaultMediaItem
import com.incognito.vault.desktop.service.DesktopVaultService
import com.incognito.vault.desktop.util.ImageUtils
import com.incognito.vault.desktop.util.NativeDialogs
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaViewerDialog(
    initialItem: VaultMediaItem,
    items: List<VaultMediaItem>,
    service: DesktopVaultService,
    onDismiss: () -> Unit
) {
    var currentIndex by remember {
        mutableStateOf(items.indexOfFirst { it.id == initialItem.id }.coerceAtLeast(0))
    }
    val currentItem = items.getOrNull(currentIndex) ?: initialItem

    var scale by remember { mutableStateOf(1.0f) }
    var rotation by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Reset zoom and rotation when current item changes
    LaunchedEffect(currentItem.id) {
        scale = 1.0f
        rotation = 0f
    }

    fun navigatePrev() {
        if (currentIndex > 0) {
            currentIndex--
        } else {
            currentIndex = items.size - 1
        }
    }

    fun navigateNext() {
        if (currentIndex < items.size - 1) {
            currentIndex++
        } else {
            currentIndex = 0
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF00A0A0C))
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            Key.DirectionLeft -> {
                                navigatePrev()
                                true
                            }
                            Key.DirectionRight -> {
                                navigateNext()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        ) {
            // Top Toolbar Overlay
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = Color(0xCC121214)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Item Counter & Metadata
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${currentIndex + 1} / ${items.size}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (currentItem.mediaType == MediaType.PHOTO) "Encrypted Photo" else "Encrypted Video",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${ImageUtils.formatTimestamp(currentItem.timestamp)} • ${ImageUtils.formatFileSize(currentItem.plaintextLength)}",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // Top Action Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Export Decrypted Button
                        IconButton(
                            onClick = {
                                val dest = NativeDialogs.pickDirectory(title = "Export Decrypted Item")
                                if (dest != null) {
                                    scope.launch {
                                        service.exportItem(currentItem, dest)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export Decrypted", tint = Color.White)
                        }

                        // Shred & Delete Button
                        IconButton(
                            onClick = { showDeleteConfirm = true }
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Shred & Delete", tint = MaterialTheme.colorScheme.error)
                        }

                        // Close Button (Esc)
                        IconButton(
                            onClick = onDismiss
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }

            // Central Media Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = if (currentItem.mediaType == MediaType.PHOTO) 64.dp else 0.dp),
                contentAlignment = Alignment.Center
            ) {
                if (currentItem.mediaType == MediaType.PHOTO) {
                    PhotoViewer(
                        item = currentItem,
                        service = service,
                        scale = scale,
                        rotation = rotation,
                        onScaleChange = { scale = it }
                    )
                } else {
                    VideoPlayerView(
                        item = currentItem,
                        service = service,
                        onExportClick = {
                            val dest = NativeDialogs.pickDirectory(title = "Export Decrypted Video")
                            if (dest != null) {
                                scope.launch {
                                    service.exportItem(currentItem, dest)
                                }
                            }
                        }
                    )
                }
            }

            // Left Navigation Button
            if (items.size > 1) {
                IconButton(
                    onClick = { navigatePrev() },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x80000000))
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }

            // Right Navigation Button
            if (items.size > 1) {
                IconButton(
                    onClick = { navigateNext() },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x80000000))
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }

            // Bottom Toolbar for Photos (Zoom, Pan, Rotate)
            if (currentItem.mediaType == MediaType.PHOTO) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xCC121214)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Zoom Out
                        IconButton(
                            onClick = { scale = (scale - 0.25f).coerceAtLeast(0.5f) }
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
                        }

                        // Zoom percentage text
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.width(44.dp)
                        )

                        // Zoom In
                        IconButton(
                            onClick = { scale = (scale + 0.25f).coerceAtMost(5.0f) }
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
                        }

                        // Reset Zoom (1:1)
                        TextButton(
                            onClick = {
                                scale = 1.0f
                                rotation = 0f
                            }
                        ) {
                            Text("Reset", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }

                        // Rotate Left (-90)
                        IconButton(
                            onClick = { rotation = (rotation - 90f) % 360f }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Rotate Left", tint = Color.White)
                        }

                        // Rotate Right (+90)
                        IconButton(
                            onClick = { rotation = (rotation + 90f) % 360f }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate Right", tint = Color.White)
                        }
                    }
                }
            }

            // Delete Confirmation
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Shred & Delete Item?") },
                    text = { Text("This will permanently overwrite the file blocks with random bytes and remove it from the vault.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteConfirm = false
                                scope.launch {
                                    service.deleteItems(listOf(currentItem))
                                    if (items.size <= 1) {
                                        onDismiss()
                                    } else {
                                        navigateNext()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Shred & Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
