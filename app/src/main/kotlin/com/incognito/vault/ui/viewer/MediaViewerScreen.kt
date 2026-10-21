package com.incognito.vault.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.incognito.vault.ui.theme.VaultIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.incognito.vault.crypto.MediaType
import com.incognito.vault.crypto.VaultCrypto
import com.incognito.vault.crypto.VaultMediaItem
import com.incognito.vault.media.EncryptedMediaDataSource
import com.incognito.vault.ui.theme.VaultAccent
import com.incognito.vault.ui.theme.VaultBlack
import com.incognito.vault.ui.theme.VaultCard
import com.incognito.vault.ui.theme.VaultError
import com.incognito.vault.ui.theme.VaultSurface
import com.incognito.vault.ui.theme.VaultTextPrimary
import com.incognito.vault.ui.theme.VaultTextSecondary
import com.incognito.vault.viewmodel.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun MediaViewerScreen(
    itemId: String,
    viewModel: VaultViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mediaList by viewModel.mediaList.collectAsState()
    val item = mediaList.find { it.id == itemId }

    var showControls by remember { mutableStateOf(true) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    if (item == null) {
        LaunchedEffect(Unit) {
            onBack()
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        if (item.mediaType == MediaType.PHOTO) {
            PhotoViewer(
                item = item,
                viewModel = viewModel,
                rotationAngle = rotationAngle
            )
        } else {
            VideoPlayer(
                item = item,
                viewModel = viewModel,
                showControls = showControls
            )
        }

        // Overlay Controls (Top Bar)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ViewerTopBar(
                isPhoto = item.mediaType == MediaType.PHOTO,
                onBack = onBack,
                onRotate = {
                    rotationAngle = (rotationAngle + 90f) % 360f
                },
                onInfo = { showInfoDialog = true },
                onExport = {
                    viewModel.exportItem(context, item)
                },
                onDelete = { showDeleteDialog = true }
            )
        }

        // Info Dialog
        if (showInfoDialog) {
            MediaInfoDialog(
                item = item,
                onDismiss = { showInfoDialog = false }
            )
        }

        // Shred Confirmation Dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = VaultCard,
                title = {
                    Text(
                        text = "Forensically Shred Media?",
                        color = VaultTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This file will be securely overwritten with random data and zeros before deletion. Data recovery will be impossible.",
                        color = VaultTextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteItem(item)
                            onBack()
                        }
                    ) {
                        Text("SHRED & DELETE", color = VaultError, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("CANCEL", color = VaultTextPrimary)
                    }
                }
            )
        }
    }
}

@Composable
private fun PhotoViewer(
    item: VaultMediaItem,
    viewModel: VaultViewModel,
    rotationAngle: Float
) {
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(item.id) {
        val key = viewModel.getActiveKey()
        if (key != null) {
            withContext(Dispatchers.IO) {
                try {
                    val encrypted = item.file.readBytes()
                    val decrypted = VaultCrypto.decryptBuffer(encrypted, key)
                    val bmp = BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
                    withContext(Dispatchers.Main) {
                        photoBitmap = bmp
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (photoBitmap != null) {
            Image(
                bitmap = photoBitmap!!.asImageBitmap(),
                contentDescription = "Decrypted Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                        rotationZ = rotationAngle
                    )
            )
        } else if (isLoading) {
            CircularProgressIndicator(color = VaultAccent)
        } else {
            Text(text = "Failed to decrypt photo", color = VaultError)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    item: VaultMediaItem,
    viewModel: VaultViewModel,
    showControls: Boolean
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isMuted by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    LaunchedEffect(item.id) {
        val factory = EncryptedMediaDataSource.Factory {
            viewModel.getActiveKey()
        }
        val mediaItem = MediaItem.fromUri(Uri.fromFile(item.file))
        val mediaSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(mediaItem)

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        })
    }

    // Progress polling loop
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                currentPosMs = exoPlayer.currentPosition
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(250)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Time & Scrub Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatMs(currentPosMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = if (durationMs > 0) currentPosMs.toFloat() / durationMs.toFloat() else 0f,
                            onValueChange = { ratio ->
                                val target = (ratio * durationMs).toLong()
                                exoPlayer.seekTo(target)
                                currentPosMs = target
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = VaultAccent,
                                activeTrackColor = VaultAccent,
                                inactiveTrackColor = VaultSurface
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatMs(durationMs),
                            color = VaultTextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Playback Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Replay
                        IconButton(onClick = {
                            exoPlayer.seekTo(0)
                            exoPlayer.play()
                        }) {
                            Icon(
                                painter = VaultIcons.Replay,
                                contentDescription = "Replay",
                                tint = Color.White
                            )
                        }

                        // Play/Pause
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(VaultAccent)
                        ) {
                            Icon(
                                painter = if (isPlaying) VaultIcons.Pause else VaultIcons.Play,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Mute / Unmute
                        IconButton(onClick = {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                        }) {
                            Icon(
                                painter = if (isMuted) VaultIcons.VolumeOff else VaultIcons.VolumeUp,
                                contentDescription = "Toggle Audio",
                                tint = if (isMuted) VaultError else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerTopBar(
    isPhoto: Boolean,
    onBack: () -> Unit,
    onRotate: () -> Unit,
    onInfo: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.65f),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = VaultIcons.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPhoto) {
                    IconButton(onClick = onRotate) {
                        Icon(
                            painter = VaultIcons.RotateRight,
                            contentDescription = "Rotate",
                            tint = Color.White
                        )
                    }
                }
                IconButton(onClick = onInfo) {
                    Icon(
                        painter = VaultIcons.Info,
                        contentDescription = "Info",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onExport) {
                    Icon(
                        painter = VaultIcons.Download,
                        contentDescription = "Export to Gallery",
                        tint = VaultAccent
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = VaultIcons.DeleteForever,
                        contentDescription = "Shred",
                        tint = VaultError
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaInfoDialog(
    item: VaultMediaItem,
    onDismiss: () -> Unit
) {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
    val sizeKb = item.plaintextLength / 1024
    val sizeMb = sizeKb / 1024f

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultCard,
        title = {
            Text(
                text = "Media Vault Properties",
                color = VaultTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                InfoRow("File ID", item.id.take(12) + "...")
                InfoRow("Media Type", if (item.mediaType == MediaType.PHOTO) "Photo (JPEG)" else "Video (MP4)")
                InfoRow("Plaintext Size", String.format(Locale.US, "%.2f MB (%d KB)", sizeMb, sizeKb))
                InfoRow("Encrypted File Size", "${item.file.length() / 1024} KB")
                InfoRow("Cipher", "AES-256-GCM / 64KB Chunks")
                InfoRow("Captured At", dateStr)
                InfoRow("Storage Path", item.file.name)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = VaultAccent)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = VaultTextSecondary, fontSize = 12.sp)
        Text(text = value, color = VaultTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
