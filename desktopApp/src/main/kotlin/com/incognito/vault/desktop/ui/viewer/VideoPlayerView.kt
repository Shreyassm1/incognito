package com.incognito.vault.desktop.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.incognito.vault.crypto.VaultCrypto
import com.incognito.vault.crypto.VaultMediaItem
import com.incognito.vault.desktop.service.DesktopVaultService
import com.incognito.vault.desktop.util.EncryptedSourceByteChannel
import com.incognito.vault.desktop.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jcodec.api.FrameGrab
import org.jcodec.scale.AWTUtil
import java.awt.image.BufferedImage

@Composable
fun VideoPlayerView(
    item: VaultMediaItem,
    service: DesktopVaultService,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionSec by remember { mutableStateOf(0.0) }
    var estimatedDurationSec by remember { mutableStateOf(30.0) }
    var currentFrameBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingFrame by remember { mutableStateOf(true) }
    var decodeError by remember { mutableStateOf<String?>(null) }
    val activeKey = service.getActiveKey()
    val scope = rememberCoroutineScope()

    // Estimate duration based on file length (rough default if metadata parsing fails)
    LaunchedEffect(item.id) {
        // Assume rough bitrate of 2 MB/s for duration baseline
        val mb = item.plaintextLength / (1024.0 * 1024.0)
        estimatedDurationSec = (mb * 4.0).coerceIn(5.0, 3600.0)
        currentPositionSec = 0.0
        isPlaying = false
        decodeError = null
    }

    // Function to decode frame at specific second
    suspend fun decodeFrame(sec: Double): BufferedImage? = withContext(Dispatchers.IO) {
        if (activeKey == null) return@withContext null
        try {
            val source = VaultCrypto.openSeekableSource(item.file, activeKey)
            val channel = EncryptedSourceByteChannel(source)
            channel.use { ch ->
                val grab = FrameGrab.createFrameGrab(ch)
                if (sec > 0) {
                    try {
                        grab.seekToSecondPrecise(sec)
                    } catch (e: Exception) {
                        // fallback to start if seek fails
                    }
                }
                val pic = grab.nativeFrame ?: return@withContext null
                AWTUtil.toBufferedImage(pic)
            }
        } catch (e: Exception) {
            null
        }
    }

    // Load initial frame
    LaunchedEffect(item.id) {
        isLoadingFrame = true
        val frame = decodeFrame(0.0)
        if (frame != null) {
            currentFrameBitmap = frame.toComposeImageBitmap()
        } else {
            // Try loading cached thumbnail as poster
            currentFrameBitmap = service.loadThumbnail(item)
            if (currentFrameBitmap == null) {
                decodeError = "Video codec not supported by built-in previewer."
            }
        }
        isLoadingFrame = false
    }

    // Playback loop
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val step = 0.5 // Update frame every 500ms for smooth preview
            while (isActive && isPlaying) {
                delay(500)
                if (currentPositionSec >= estimatedDurationSec) {
                    currentPositionSec = 0.0
                    isPlaying = false
                    break
                }
                currentPositionSec += step
                val frame = decodeFrame(currentPositionSec)
                if (frame != null) {
                    currentFrameBitmap = frame.toComposeImageBitmap()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        // Video Frame Display
        if (currentFrameBitmap != null) {
            Image(
                bitmap = currentFrameBitmap!!,
                contentDescription = "Video Frame",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)
            )
        } else if (isLoadingFrame) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Streaming decrypted video chunks from memory...",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            // Fallback display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 80.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Encrypted Video File (${ImageUtils.formatFileSize(item.plaintextLength)})",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Zero-leak in-memory playback ready. Export decrypted to play in VLC or Windows Media Player.",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(onClick = onExportClick) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Decrypted Video")
                }
            }
        }

        // Overlay Play Icon if paused
        if (!isPlaying && currentFrameBitmap != null) {
            IconButton(
                onClick = { isPlaying = true },
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Bottom Video Control Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Seek Slider
                Slider(
                    value = currentPositionSec.toFloat(),
                    onValueChange = { newPos ->
                        currentPositionSec = newPos.toDouble()
                    },
                    onValueChangeFinished = {
                        // Update frame at new position
                        scope.launch {
                            val frame = decodeFrame(currentPositionSec)
                            if (frame != null) {
                                currentFrameBitmap = frame.toComposeImageBitmap()
                            }
                        }
                    },
                    valueRange = 0f..estimatedDurationSec.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Time indicator
                    Text(
                        text = "${ImageUtils.formatDuration(currentPositionSec)} / ${ImageUtils.formatDuration(estimatedDurationSec)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )

                    // Transport controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Rewind 5s
                        IconButton(
                            onClick = {
                                currentPositionSec = (currentPositionSec - 5.0).coerceAtLeast(0.0)
                            }
                        ) {
                            Icon(Icons.Default.Replay5, contentDescription = "Rewind 5s", tint = MaterialTheme.colorScheme.onSurface)
                        }

                        // Play/Pause
                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        // Forward 5s
                        IconButton(
                            onClick = {
                                currentPositionSec = (currentPositionSec + 5.0).coerceAtMost(estimatedDurationSec)
                            }
                        ) {
                            Icon(Icons.Default.Forward5, contentDescription = "Forward 5s", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Export Decrypted shortcut
                    OutlinedButton(
                        onClick = onExportClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Decrypted", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
