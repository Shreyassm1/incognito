package com.incognito.vault.ui.gallery

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.incognito.vault.ui.theme.VaultIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.incognito.vault.crypto.MediaType
import com.incognito.vault.crypto.VaultMediaItem
import com.incognito.vault.media.ThumbnailCache
import com.incognito.vault.ui.theme.VaultAccent
import com.incognito.vault.ui.theme.VaultBlack
import com.incognito.vault.ui.theme.VaultCard
import com.incognito.vault.ui.theme.VaultError
import com.incognito.vault.ui.theme.VaultSurface
import com.incognito.vault.ui.theme.VaultSurfaceVariant
import com.incognito.vault.ui.theme.VaultTextPrimary
import com.incognito.vault.ui.theme.VaultTextSecondary
import com.incognito.vault.viewmodel.VaultViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GalleryFilterTab {
    ALL, PHOTOS, VIDEOS
}

@Composable
fun GalleryScreen(
    viewModel: VaultViewModel,
    onNavigateToCamera: () -> Unit,
    onNavigateToViewer: (String) -> Unit,
    onLockRequested: () -> Unit
) {
    val context = LocalContext.current
    val mediaList by viewModel.mediaList.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val isPickerMode by viewModel.isPickerMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    var selectedFilter by remember { mutableStateOf(GalleryFilterTab.ALL) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        viewModel.refreshMediaList()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importMediaFromUris(context, uris) { importedUris ->
                val pendingDelete = viewModel.createDeleteRequest(context, importedUris)
                if (pendingDelete != null) {
                    try {
                        deleteRequestLauncher.launch(
                            IntentSenderRequest.Builder(pendingDelete.intentSender).build()
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshMediaList()
    }

    val filteredList = remember(mediaList, selectedFilter) {
        when (selectedFilter) {
            GalleryFilterTab.ALL -> mediaList
            GalleryFilterTab.PHOTOS -> mediaList.filter { it.mediaType == MediaType.PHOTO }
            GalleryFilterTab.VIDEOS -> mediaList.filter { it.mediaType == MediaType.VIDEO }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top App Bar
            GalleryTopBar(
                isSelectionMode = isSelectionMode,
                selectedCount = selectedIds.size,
                totalCount = mediaList.size,
                onBack = {
                    if (isSelectionMode) {
                        viewModel.toggleSelectionMode(false)
                    } else {
                        onNavigateToCamera()
                    }
                },
                onToggleSelect = {
                    viewModel.toggleSelectionMode(!isSelectionMode)
                },
                onSelectAll = {
                    viewModel.selectAll()
                },
                onDelete = {
                    if (selectedIds.isNotEmpty()) {
                        showDeleteConfirmationDialog = true
                    }
                },
                onExport = {
                    viewModel.exportSelected(context)
                },
                onShare = {
                    viewModel.shareSelected(context)
                },
                onImport = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onLock = onLockRequested,
                isPickerMode = isPickerMode
            )

            // Filter Tabs (All, Photos, Videos)
            if (!isSelectionMode) {
                TabRow(
                    selectedTabIndex = selectedFilter.ordinal,
                    containerColor = VaultSurface,
                    contentColor = VaultAccent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedFilter.ordinal]),
                            color = VaultAccent
                        )
                    }
                ) {
                    Tab(
                        selected = selectedFilter == GalleryFilterTab.ALL,
                        onClick = { selectedFilter = GalleryFilterTab.ALL },
                        text = {
                            Text(
                                "ALL (${mediaList.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    )
                    Tab(
                        selected = selectedFilter == GalleryFilterTab.PHOTOS,
                        onClick = { selectedFilter = GalleryFilterTab.PHOTOS },
                        text = {
                            val photoCount = mediaList.count { it.mediaType == MediaType.PHOTO }
                            Text(
                                "PHOTOS ($photoCount)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    )
                    Tab(
                        selected = selectedFilter == GalleryFilterTab.VIDEOS,
                        onClick = { selectedFilter = GalleryFilterTab.VIDEOS },
                        text = {
                            val videoCount = mediaList.count { it.mediaType == MediaType.VIDEO }
                            Text(
                                "VIDEOS ($videoCount)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    )
                }
            }

            // Grid Content
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = VaultIcons.Photo,
                            contentDescription = "Empty Vault",
                            tint = VaultTextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Encrypted Vault is Empty",
                            color = VaultTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Media captured in camera is encrypted chunk-by-chunk in RAM and stored securely.",
                            color = VaultTextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        GalleryGridItem(
                            item = item,
                            viewModel = viewModel,
                            isSelected = selectedIds.contains(item.id),
                            isSelectionMode = isSelectionMode,
                            onItemClick = {
                                if (isPickerMode) {
                                    val activity = context as? android.app.Activity
                                    if (activity != null) {
                                        viewModel.pickItem(activity, item)
                                    }
                                } else if (isSelectionMode) {
                                    viewModel.toggleSelectItem(item.id)
                                } else {
                                    onNavigateToViewer(item.id)
                                }
                            },
                            onItemLongClick = {
                                if (!isSelectionMode) {
                                    viewModel.toggleSelectionMode(true)
                                }
                                viewModel.toggleSelectItem(item.id)
                            }
                        )
                    }
                }
            }
        }

        // Return to Camera FAB
        if (!isSelectionMode) {
            FloatingActionButton(
                onClick = onNavigateToCamera,
                containerColor = VaultAccent,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp)
            ) {
                Icon(
                    painter = VaultIcons.CameraAlt,
                    contentDescription = "Open Camera"
                )
            }
        }

        // Delete confirmation dialog
        if (showDeleteConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmationDialog = false },
                containerColor = VaultCard,
                title = {
                    Text(
                        text = "Forensically Shred ${selectedIds.size} Items?",
                        color = VaultTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Selected files will be overwritten with random bytes and zeros before deletion. Data recovery will be physically impossible.",
                        color = VaultTextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmationDialog = false
                            viewModel.deleteSelected()
                        }
                    ) {
                        Text("SHRED & DELETE", color = VaultError, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                        Text("CANCEL", color = VaultTextPrimary)
                    }
                }
            )
        }
    }
}

@Composable
private fun GalleryTopBar(
    isSelectionMode: Boolean,
    isPickerMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onBack: () -> Unit,
    onToggleSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onImport: () -> Unit,
    onLock: () -> Unit
) {
    Surface(
        color = VaultSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = VaultIcons.ArrowBack,
                        contentDescription = "Back",
                        tint = VaultTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = if (isPickerMode) "SELECT TO ATTACH" else if (isSelectionMode) "$selectedCount SELECTED" else "VAULT GALLERY",
                        color = VaultTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (!isSelectionMode && !isPickerMode) {
                        Text(
                            text = "$totalCount items secured",
                            color = VaultAccent,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelectionMode) {
                    IconButton(onClick = onSelectAll) {
                        Icon(
                            painter = VaultIcons.SelectAll,
                            contentDescription = "Select All",
                            tint = VaultTextPrimary
                        )
                    }
                    IconButton(onClick = onShare, enabled = selectedCount > 0) {
                        Icon(
                            painter = VaultIcons.Share,
                            contentDescription = "Share Selected",
                            tint = if (selectedCount > 0) VaultAccent else VaultTextSecondary.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(onClick = onExport, enabled = selectedCount > 0) {
                        Icon(
                            painter = VaultIcons.Download,
                            contentDescription = "Export Selected",
                            tint = if (selectedCount > 0) VaultAccent else VaultTextSecondary.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                        Icon(
                            painter = VaultIcons.DeleteForever,
                            contentDescription = "Shred Selected",
                            tint = if (selectedCount > 0) VaultError else VaultTextSecondary.copy(alpha = 0.5f)
                        )
                    }
                } else if (!isPickerMode) {
                    IconButton(onClick = onImport) {
                        Icon(
                            painter = VaultIcons.AddPhoto,
                            contentDescription = "Import from Device",
                            tint = VaultAccent
                        )
                    }
                    IconButton(onClick = onToggleSelect) {
                        Icon(
                            painter = VaultIcons.CheckCircle,
                            contentDescription = "Select Mode",
                            tint = VaultTextSecondary
                        )
                    }
                    IconButton(onClick = onLock) {
                        Icon(
                            painter = VaultIcons.Lock,
                            contentDescription = "Lock Vault",
                            tint = VaultAccent
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryGridItem(
    item: VaultMediaItem,
    viewModel: VaultViewModel,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit
) {
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(item.id) {
        val key = viewModel.getActiveKey()
        if (key != null) {
            val bmp = ThumbnailCache.loadThumbnail(item, key)
            thumbnailBitmap = bmp
        }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(VaultCard)
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            )
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) VaultAccent else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = "Vault Item",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isLoading) {
            CircularProgressIndicator(
                color = VaultAccent,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                painter = if (item.mediaType == MediaType.PHOTO) VaultIcons.Photo else VaultIcons.Videocam,
                contentDescription = "Media",
                tint = VaultTextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(32.dp)
            )
        }

        // Video Badge in bottom left corner
        if (item.mediaType == MediaType.VIDEO) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = VaultIcons.Play,
                        contentDescription = "Video",
                        tint = VaultAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "VIDEO",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Selection Checkmark Overlay
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Icon(
                    painter = if (isSelected) VaultIcons.CheckCircle else VaultIcons.RadioUnchecked,
                    contentDescription = "Selected",
                    tint = if (isSelected) VaultAccent else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
