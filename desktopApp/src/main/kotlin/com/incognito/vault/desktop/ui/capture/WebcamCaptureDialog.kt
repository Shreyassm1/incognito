package com.incognito.vault.desktop.ui.capture

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamResolution
import com.incognito.vault.desktop.service.DesktopVaultService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

@Composable
fun WebcamCaptureDialog(
    service: DesktopVaultService,
    onDismiss: () -> Unit
) {
    var activeWebcam by remember { mutableStateOf<Webcam?>(null) }
    var availableWebcams by remember { mutableStateOf<List<Webcam>>(emptyList()) }
    var currentFrameBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var currentFrameBuffered by remember { mutableStateOf<BufferedImage?>(null) }
    var capturedPhoto by remember { mutableStateOf<BufferedImage?>(null) }
    var isSimulatedCamera by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Helper to generate simulated camera frame
    fun generateSimulatedFrame(step: Int): BufferedImage {
        val width = 640
        val height = 480
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g2 = img.createGraphics()
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            // Dark gradient background
            g2.color = java.awt.Color(18, 20, 26)
            g2.fillRect(0, 0, width, height)

            // Grid lines
            g2.color = java.awt.Color(32, 38, 48)
            for (x in 0 until width step 40) {
                g2.drawLine(x, 0, x, height)
            }
            for (y in 0 until height step 40) {
                g2.drawLine(0, y, width, y)
            }

            // Center target crosshair
            val cx = width / 2
            val cy = height / 2
            g2.color = java.awt.Color(16, 185, 129, 180) // Emerald
            g2.drawOval(cx - 60, cy - 60, 120, 120)
            g2.drawOval(cx - 3, cy - 3, 6, 6)
            g2.drawLine(cx - 80, cy, cx + 80, cy)
            g2.drawLine(cx, cy - 80, cx, cy + 80)

            // Animated scanning beam
            val scanY = (step * 8) % height
            g2.color = java.awt.Color(16, 185, 129, 45)
            g2.fillRect(0, scanY - 10, width, 20)

            // HUD Overlays
            g2.color = java.awt.Color.WHITE
            g2.font = Font("Monospaced", Font.BOLD, 14)
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            g2.drawString("INCOGNITO SECURE CAMERA FEED", 20, 30)
            g2.drawString("TIME: $timeStr", 20, 52)
            g2.drawString("MODE: AES-256 ZERO-LEAK CAPTURE", 20, 74)

            // Red REC indicator
            if ((step / 10) % 2 == 0) {
                g2.color = java.awt.Color.RED
                g2.fillOval(width - 80, 20, 12, 12)
                g2.color = java.awt.Color.WHITE
                g2.drawString("LIVE", width - 60, 31)
            }
        } finally {
            g2.dispose()
        }
        return img
    }

    // Initialize camera
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val cams = Webcam.getWebcams()
                availableWebcams = cams
                if (cams.isNotEmpty()) {
                    val cam = Webcam.getDefault()
                    if (cam != null) {
                        try {
                            cam.viewSize = Dimension(640, 480)
                        } catch (e: Exception) {
                            // default size ok
                        }
                        cam.open()
                        activeWebcam = cam
                    } else {
                        isSimulatedCamera = true
                    }
                } else {
                    isSimulatedCamera = true
                }
            } catch (t: Throwable) {
                isSimulatedCamera = true
            }
        }
    }

    // Camera polling loop
    LaunchedEffect(activeWebcam, isSimulatedCamera, capturedPhoto) {
        if (capturedPhoto != null) return@LaunchedEffect

        var step = 0
        while (isActive) {
            val cam = activeWebcam
            if (cam != null && cam.isOpen) {
                withContext(Dispatchers.IO) {
                    val img = cam.image
                    if (img != null) {
                        currentFrameBuffered = img
                        currentFrameBitmap = img.toComposeImageBitmap()
                    }
                }
            } else if (isSimulatedCamera) {
                step++
                val img = generateSimulatedFrame(step)
                currentFrameBuffered = img
                currentFrameBitmap = img.toComposeImageBitmap()
            }
            delay(40) // ~25 FPS
        }
    }

    // Cleanup when dismissed
    DisposableEffect(Unit) {
        onDispose {
            try {
                activeWebcam?.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE60A0A0C)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .width(720.dp)
                    .padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (capturedPhoto != null) "Review Snapshot" else "Webcam Secure Capture",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Camera status pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSimulatedCamera) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (isSimulatedCamera) "Simulated Camera" else "Physical Camera Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSimulatedCamera) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Viewfinder / Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (capturedPhoto != null) {
                            Image(
                                bitmap = capturedPhoto!!.toComposeImageBitmap(),
                                contentDescription = "Captured Photo Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (currentFrameBitmap != null) {
                            Image(
                                bitmap = currentFrameBitmap!!,
                                contentDescription = "Webcam Live Feed",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Controls
                    if (capturedPhoto == null) {
                        // Live View Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val current = currentFrameBuffered
                                    if (current != null) {
                                        // Clone image
                                        val copy = BufferedImage(current.width, current.height, BufferedImage.TYPE_INT_RGB)
                                        val g = copy.createGraphics()
                                        g.drawImage(current, 0, 0, null)
                                        g.dispose()
                                        capturedPhoto = copy
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Take Snapshot", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Review Captured Photo Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { capturedPhoto = null },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(46.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retake")
                            }

                            Button(
                                onClick = {
                                    val photo = capturedPhoto ?: return@Button
                                    isSaving = true
                                    scope.launch {
                                        service.saveWebcamPhoto(photo)
                                        isSaving = false
                                        onDismiss()
                                    }
                                },
                                enabled = !isSaving,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(46.dp)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Encrypt & Save to Vault", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
