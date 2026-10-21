package com.incognito.vault.desktop.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO

object ImageUtils {

    fun bytesToBufferedImage(bytes: ByteArray): BufferedImage? {
        return try {
            ByteArrayInputStream(bytes).use { ImageIO.read(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun bytesToComposeImage(bytes: ByteArray): ImageBitmap? {
        val buffered = bytesToBufferedImage(bytes) ?: return null
        return buffered.toComposeImageBitmap()
    }

    fun createThumbnailBytes(original: BufferedImage, maxDimension: Int = 320): ByteArray {
        val origWidth = original.width
        val origHeight = original.height

        val (targetWidth, targetHeight) = if (origWidth > origHeight) {
            val ratio = origHeight.toDouble() / origWidth.toDouble()
            val w = origWidth.coerceAtMost(maxDimension)
            val h = (w * ratio).toInt().coerceAtLeast(1)
            w to h
        } else {
            val ratio = origWidth.toDouble() / origHeight.toDouble()
            val h = origHeight.coerceAtMost(maxDimension)
            val w = (h * ratio).toInt().coerceAtLeast(1)
            w to h
        }

        val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val g2: Graphics2D = resized.createGraphics()
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.drawImage(original, 0, 0, targetWidth, targetHeight, null)
        } finally {
            g2.dispose()
        }

        val baos = ByteArrayOutputStream()
        ImageIO.write(resized, "jpg", baos)
        return baos.toByteArray()
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatDuration(seconds: Double): String {
        val totalSecs = seconds.toInt().coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
