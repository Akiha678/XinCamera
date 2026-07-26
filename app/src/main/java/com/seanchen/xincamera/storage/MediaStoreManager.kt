package com.seanchen.xincamera.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 系统相册访问层。
 *
 * Camera 层不应该直接散落 `MediaStore` 细节；集中封装后，拍照保存、滤镜输出、
 * Android 版本兼容和失败回滚都在一个地方维护。
 */
class MediaStoreManager(
    private val context: Context
) {
    fun createPhotoOutputOptions(): ImageCapture.OutputFileOptions {
        return ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            createImageContentValues(prefix = "xin", mimeType = "image/jpeg")
        ).build()
    }

    fun finalizePendingImage(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val finalizeValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, finalizeValues, null, null)
        }
    }

    @Suppress("DEPRECATION")
    fun decodeBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    fun saveJpegBitmap(bitmap: Bitmap, prefix: String): Uri {
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            createImageContentValues(prefix = prefix, mimeType = "image/jpeg")
        ) ?: throw IllegalStateException("Cannot create gallery item")

        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)) {
                    throw IllegalStateException("Cannot encode image")
                }
            } ?: throw IllegalStateException("Cannot open gallery output stream")

            finalizePendingImage(uri)
            return uri
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun createImageContentValues(prefix: String, mimeType: String): ContentValues {
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${prefix}_${TIMESTAMP_FORMAT.format(Date())}")
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/XinCamera"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
    }

    private companion object {
        const val JPEG_QUALITY = 95
        val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    }
}
