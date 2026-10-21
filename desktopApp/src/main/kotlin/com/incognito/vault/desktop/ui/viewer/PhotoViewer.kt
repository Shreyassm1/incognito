package com.incognito.vault.desktop.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.incognito.vault.crypto.VaultMediaItem
import com.incognito.vault.desktop.service.DesktopVaultService

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PhotoViewer(
    item: VaultMediaItem,
    service: DesktopVaultService,
    scale: Float,
    rotation: Float,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var fullImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(item.id) {
        isLoading = true
        offset = Offset.Zero
        fullImage = service.loadFullPhoto(item)
        isLoading = false
    }

    // Reset offset when scale drops to 1x
    LaunchedEffect(scale) {
        if (scale <= 1.0f) {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta != 0f) {
                    val newScale = (scale - delta * 0.15f).coerceIn(0.5f, 5.0f)
                    onScaleChange(newScale)
                }
            }
            .pointerInput(scale) {
                if (scale > 1.0f) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Decrypting high-resolution photo in memory...",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else if (fullImage != null) {
            Image(
                bitmap = fullImage!!,
                contentDescription = "Decrypted Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = rotation
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Unable to decrypt photo content",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
