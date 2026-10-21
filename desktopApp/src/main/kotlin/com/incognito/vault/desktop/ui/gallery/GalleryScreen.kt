package com.incognito.vault.desktop.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.incognito.vault.crypto.VaultMediaItem
import com.incognito.vault.desktop.service.DesktopVaultService
import com.incognito.vault.desktop.service.GalleryFilter
import com.incognito.vault.desktop.util.NativeDialogs
import kotlinx.coroutines.launch

@Composable
fun GalleryScreen(
    service: DesktopVaultService,
    onOpenItem: (VaultMediaItem) -> Unit,
    onImportClick: () -> Unit,
    onWebcamClick: () -> Unit,
    onLockClick: () -> Unit
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<VaultMediaItem?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var notificationMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val filteredItems = service.getFilteredAndSortedItems()
    val selectedCount = service.selectedIds.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            GalleryTopBar(
                service = service,
                onImportClick = onImportClick,
                onWebcamClick = onWebcamClick,
                onLockClick = onLockClick,
                isSelectionMode = isSelectionMode,
                onToggleSelectionMode = {
                    isSelectionMode = !isSelectionMode
                    if (!isSelectionMode) {
                        service.clearSelection()
                    }
                }
            )

            // Filter & Search Bar
            GalleryFilterBar(service = service)

            // Grid or Empty State
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (service.mediaItems.isEmpty()) {
                        // Empty Vault State
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "Your Encrypted Vault is Empty",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Import private photos and videos from your PC or capture snapshots via webcam.\nAll items are encrypted with AES-256-GCM before storage.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(
                                    onClick = onImportClick,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Import Media")
                                }

                                FilledTonalButton(
                                    onClick = onWebcamClick,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Capture via Webcam")
                                }
                            }
                        }
                    } else {
                        // Filtered Out State
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No matching items found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    service.currentFilter = GalleryFilter.ALL
                                    service.searchQuery = ""
                                }
                            ) {
                                Text("Reset Filters")
                            }
                        }
                    }
                }
            } else {
                // Media Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 10.dp,
                        bottom = if (isSelectionMode && selectedCount > 0) 90.dp else 20.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize().weight(1f)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val isSelected = service.selectedIds.contains(item.id)
                        MediaGridItem(
                            item = item,
                            service = service,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    service.toggleSelection(item.id)
                                } else {
                                    onOpenItem(item)
                                }
                            },
                            onExportClick = {
                                val destDir = NativeDialogs.pickDirectory(title = "Export Decrypted Media")
                                if (destDir != null) {
                                    scope.launch {
                                        val file = service.exportItem(item, destDir)
                                        notificationMessage = "Exported to: ${file.name}"
                                    }
                                }
                            },
                            onDeleteClick = {
                                itemToDelete = item
                            }
                        )
                    }
                }
            }
        }

        // Floating Batch Selection Action Bar
        if (isSelectionMode && selectedCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(0.9f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 12.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(onClick = { service.selectAll() }) {
                            Text("Select All")
                        }

                        TextButton(onClick = { service.clearSelection() }) {
                            Text("Clear")
                        }

                        FilledTonalButton(
                            onClick = {
                                val destDir = NativeDialogs.pickDirectory(title = "Select Folder to Export $selectedCount Items")
                                if (destDir != null) {
                                    scope.launch {
                                        val count = service.exportSelected(destDir)
                                        notificationMessage = "Successfully exported $count decrypted items."
                                        isSelectionMode = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Decrypted")
                        }

                        Button(
                            onClick = { showBatchDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shred & Delete")
                        }
                    }
                }
            }
        }

        // Notification Snackbar
        if (notificationMessage != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .widthIn(max = 480.dp),
                action = {
                    TextButton(onClick = { notificationMessage = null }) {
                        Text("Dismiss", color = MaterialTheme.colorScheme.primary)
                    }
                }
            ) {
                Text(notificationMessage ?: "")
            }
        }

        // Single Item Delete Confirmation Dialog
        if (itemToDelete != null) {
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Securely Shred & Delete?") },
                text = {
                    Text("This file will be cryptographically overwritten with random and zero bytes before removal. This action is irreversible.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val item = itemToDelete
                            itemToDelete = null
                            if (item != null) {
                                scope.launch {
                                    service.deleteItems(listOf(item))
                                    notificationMessage = "Item securely shredded."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Shred & Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Batch Delete Confirmation Dialog
        if (showBatchDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteDialog = false },
                icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Shred & Delete $selectedCount Items?") },
                text = {
                    Text("All $selectedCount selected items will be cryptographically overwritten with random bytes and erased. Forensics cannot recover them.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBatchDeleteDialog = false
                            scope.launch {
                                service.deleteSelected()
                                isSelectionMode = false
                                notificationMessage = "$selectedCount items securely shredded."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Shred & Delete All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
