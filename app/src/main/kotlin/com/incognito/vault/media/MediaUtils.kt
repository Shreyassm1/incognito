package com.incognito.vault.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.io.File

object MediaUtils {

    fun imageProxyToJpeg(image: ImageProxy): ByteArray {
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

    fun createThumbnail(jpegBytes: ByteArray, maxDim: Int = 256): ByteArray? {
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

    fun createVideoThumbnail(videoFile: File, maxDim: Int = 256): ByteArray? {
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
}
