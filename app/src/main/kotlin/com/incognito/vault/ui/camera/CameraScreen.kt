package com.incognito.vault.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.incognito.vault.media.ThumbnailCache
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

enum class CameraCaptureMode {
    PHOTO, VIDEO
}

enum class FlashModeOption {
    OFF, ON, AUTO
}

@Composable
fun CameraScreen(
    viewModel: VaultViewModel,
    onNavigateToGallery: () -> Unit,
    onLockRequested: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var captureMode by remember { mutableStateOf(CameraCaptureMode.PHOTO) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashModeOption by remember { mutableStateOf(FlashModeOption.OFF) }
    var torchEnabled by remember { mutableStateOf(false) }

    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var isProcessingCapture by remember { mutableStateOf(false) }

    val mediaList by viewModel.mediaList.collectAsState()
    val latestItem = mediaList.firstOrNull()
    var latestThumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // CameraX instance references
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var currentTempVideoFile by remember { mutableStateOf<File?>(null) }

    // Pulsating animation for recording indicator
    val infiniteTransition = rememberInfiniteTransition(label = "recordingPulse")
    val recordingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Load latest thumbnail for quick gallery preview
    LaunchedEffect(latestItem) {
        val key = viewModel.getActiveKey()
        if (latestItem != null && key != null) {
            val bmp = ThumbnailCache.loadThumbnail(latestItem, key)
            latestThumbnailBitmap = bmp
        } else {
            latestThumbnailBitmap = null
        }
    }

    // Recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSec = 0
            while (isRecording) {
                delay(1000)
                recordingDurationSec++
            }
        }
    }

    // Bind Camera Provider when lensFacing changes
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    fun bindCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imgCap = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(
                    when (flashModeOption) {
                        FlashModeOption.OFF -> ImageCapture.FLASH_MODE_OFF
                        FlashModeOption.ON -> ImageCapture.FLASH_MODE_ON
                        FlashModeOption.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    }
                )
                .build()
            imageCapture = imgCap

            val qualitySelector = QualitySelector.from(
                Quality.FHD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            val vidCap = VideoCapture.withOutput(recorder)
            videoCapture = vidCap

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imgCap,
                    vidCap
                )

                // Restore torch if active and supported
                if (torchEnabled && camera?.cameraInfo?.hasFlashUnit() == true) {
                    camera?.cameraControl?.enableTorch(true)
                }
            } catch (e: Exception) {
                Log.e("CameraScreen", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(lensFacing) {
        bindCamera()
    }

    // Safe stop when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            try {
                activeRecording?.stop()
                activeRecording = null
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Top Controls Bar (Flash, Camera Switch, Lock)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash / Torch toggle
            IconButton(
                onClick = {
                    if (captureMode == CameraCaptureMode.PHOTO) {
                        flashModeOption = when (flashModeOption) {
                            FlashModeOption.OFF -> FlashModeOption.ON
                            FlashModeOption.ON -> FlashModeOption.AUTO
                            FlashModeOption.AUTO -> FlashModeOption.OFF
                        }
                        imageCapture?.flashMode = when (flashModeOption) {
                            FlashModeOption.OFF -> ImageCapture.FLASH_MODE_OFF
                            FlashModeOption.ON -> ImageCapture.FLASH_MODE_ON
                            FlashModeOption.AUTO -> ImageCapture.FLASH_MODE_AUTO
                        }
                    } else {
                        val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
                        if (hasFlash) {
                            torchEnabled = !torchEnabled
                            camera?.cameraControl?.enableTorch(torchEnabled)
                        }
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                val icon = if (captureMode == CameraCaptureMode.PHOTO) {
                    when (flashModeOption) {
                        FlashModeOption.OFF -> VaultIcons.FlashOff
                        FlashModeOption.ON -> VaultIcons.FlashOn
                        FlashModeOption.AUTO -> VaultIcons.FlashAuto
                    }
                } else {
                    if (torchEnabled) VaultIcons.FlashOn else VaultIcons.FlashOff
                }
                Icon(
                    painter = icon,
                    contentDescription = "Flash Toggle",
                    tint = if (flashModeOption != FlashModeOption.OFF || torchEnabled) VaultAccent else Color.White
                )
            }

            // Recording Indicator (if recording)
            if (isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(VaultError)
                            .alpha(recordingAlpha)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTime(recordingDurationSec),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Text(
                    text = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "SELFIE" else "BACK",
                    color = VaultTextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Quick Lock Vault Button
            IconButton(
                onClick = onLockRequested,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    painter = VaultIcons.Lock,
                    contentDescription = "Lock Vault",
                    tint = VaultAccent
                )
            }
        }

        // Bottom Controls Container
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Selector: PHOTO <-> VIDEO
            if (!isRecording) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    ModeTab(
                        title = "PHOTO",
                        selected = captureMode == CameraCaptureMode.PHOTO,
                        onClick = { captureMode = CameraCaptureMode.PHOTO }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ModeTab(
                        title = "VIDEO",
                        selected = captureMode == CameraCaptureMode.VIDEO,
                        onClick = { captureMode = CameraCaptureMode.VIDEO }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Shutter Row: [Gallery Thumbnail] [Shutter Button] [Lens Switch]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Gallery Thumbnail Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VaultSurface.copy(alpha = 0.7f))
                        .border(1.5.dp, VaultTextSecondary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable(enabled = !isRecording && !isProcessingCapture) {
                            onNavigateToGallery()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (latestThumbnailBitmap != null) {
                        Image(
                            bitmap = latestThumbnailBitmap!!.asImageBitmap(),
                            contentDescription = "Vault Gallery",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = VaultIcons.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Main Shutter / Record Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessingCapture) {
                        CircularProgressIndicator(
                            color = VaultAccent,
                            modifier = Modifier.size(40.dp)
                        )
                    } else if (captureMode == CameraCaptureMode.PHOTO) {
                        // Photo Shutter Button (White circle)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable {
                                    val cap = imageCapture ?: return@clickable
                                    isProcessingCapture = true
                                    val executor = ContextCompat.getMainExecutor(context)
                                    cap.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val jpegBytes = imageProxyToJpeg(image)
                                                    image.close()
                                                    val thumbBytes = createThumbnail(jpegBytes)
                                                    viewModel.savePhoto(jpegBytes, thumbBytes)
                                                } finally {
                                                    withContext(Dispatchers.Main) {
                                                        isProcessingCapture = false
                                                    }
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e("CameraScreen", "Photo capture failed", exception)
                                            isProcessingCapture = false
                                        }
                                    })
                                }
                        )
                    } else {
                        // Video Record / Stop Button (Red circle / square)
                        Box(
                            modifier = Modifier
                                .size(if (isRecording) 32.dp else 56.dp)
                                .clip(if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                                .background(VaultError)
                                .clickable {
                                    val vidCap = videoCapture ?: return@clickable
                                    if (isRecording) {
                                        // Stop recording
                                        isProcessingCapture = true
                                        try {
                                            activeRecording?.stop()
                                        } catch (e: Exception) {
                                            isProcessingCapture = false
                                        }
                                    } else {
                                        // Start recording to temporary cache file
                                        val tempFile = File(context.cacheDir, "temp_rec_${System.currentTimeMillis()}.mp4")
                                        currentTempVideoFile = tempFile

                                        val outputOptions = FileOutputOptions.Builder(tempFile).build()
                                        var pendingRecording = vidCap.output.prepareRecording(context, outputOptions)

                                        if (ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            pendingRecording = pendingRecording.withAudioEnabled()
                                        }

                                        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                                            when (event) {
                                                is VideoRecordEvent.Start -> {
                                                    isRecording = true
                                                }
                                                is VideoRecordEvent.Finalize -> {
                                                    isRecording = false
                                                    activeRecording = null
                                                    if (!event.hasError()) {
                                                        scope.launch(Dispatchers.IO) {
                                                            try {
                                                                val thumbBytes = createVideoThumbnail(tempFile)
                                                                viewModel.saveVideo(tempFile, thumbBytes)
                                                            } finally {
                                                                withContext(Dispatchers.Main) {
                                                                    isProcessingCapture = false
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        Log.e("CameraScreen", "Video recording error: ${event.error}")
                                                        tempFile.delete()
                                                        isProcessingCapture = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        )
                    }
                }

                // Front / Back Lens Switcher (Selfies!)
                IconButton(
                    onClick = {
                        if (!isRecording && !isProcessingCapture) {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        }
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        painter = VaultIcons.CameraSwitch,
                        contentDescription = "Switch Camera Lens",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) VaultAccent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = if (selected) Color.Black else Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

private fun imageProxyToJpeg(image: ImageProxy): ByteArray {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val rotationDegrees = image.imageInfo.rotationDegrees
    if (rotationDegrees == 0) {
        return bytes
    }

    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    val baos = ByteArrayOutputStream()
    rotated.compress(Bitmap.CompressFormat.JPEG, 92, baos)
    bitmap.recycle()
    rotated.recycle()
    return baos.toByteArray()
}

private fun createThumbnail(jpegBytes: ByteArray, maxDim: Int = 256): ByteArray? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
    val maxActual = maxOf(options.outWidth, options.outHeight)
    var inSampleSize = 1
    while (maxActual / inSampleSize > maxDim * 2) {
        inSampleSize *= 2
    }
    val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
    val sampled = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, decodeOptions) ?: return null
    val scale = minOf(maxDim.toFloat() / sampled.width, maxDim.toFloat() / sampled.height)
    val finalThumb = if (scale < 1.0f) {
        Bitmap.createScaledBitmap(sampled, (sampled.width * scale).toInt(), (sampled.height * scale).toInt(), true)
    } else sampled

    val baos = ByteArrayOutputStream()
    finalThumb.compress(Bitmap.CompressFormat.JPEG, 80, baos)
    if (finalThumb !== sampled) sampled.recycle()
    finalThumb.recycle()
    return baos.toByteArray()
}

private fun createVideoThumbnail(videoFile: File, maxDim: Int = 256): ByteArray? {
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(videoFile.absolutePath)
        val frame = retriever.frameAtTime ?: retriever.getFrameAtTime(1_000_000)
        retriever.release()
        if (frame != null) {
            val scale = minOf(maxDim.toFloat() / frame.width, maxDim.toFloat() / frame.height)
            val thumb = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
            } else frame
            val baos = ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            if (thumb !== frame) frame.recycle()
            thumb.recycle()
            return baos.toByteArray()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
