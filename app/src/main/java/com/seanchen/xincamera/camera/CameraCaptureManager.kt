package com.seanchen.xincamera.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.seanchen.xincamera.nativebridge.NativeBridge
import com.seanchen.xincamera.storage.MediaStoreManager
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

/**
 * 拍照和图片输出管理器。
 *
 * CameraController 不直接处理 MediaStore 和 Bitmap 细节，避免相机生命周期逻辑和存储逻辑耦合。
 */
class CameraCaptureManager(
    private val mediaStoreManager: MediaStoreManager,
    private val mainExecutor: Executor,
    private val imageProcessingExecutor: ExecutorService
) {
    fun capturePhoto(
        imageCapture: ImageCapture?,
        onSaved: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val captureUseCase = imageCapture ?: run {
            onError("ImageCapture not ready")
            return
        }

        captureUseCase.takePicture(
            mediaStoreManager.createPhotoOutputOptions(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val uri = outputFileResults.savedUri
                    if (uri != null) {
                        mediaStoreManager.finalizePendingImage(uri)
                    }
                    onSaved(uri?.toString() ?: "saved")
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "Capture failed")
                }
            }
        )
    }

    fun saveGrayscaleCopy(
        sourceUriString: String,
        onSaved: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        imageProcessingExecutor.execute {
            try {
                val sourceUri = Uri.parse(sourceUriString)
                val sourceBitmap = mediaStoreManager.decodeBitmap(sourceUri)
                val argbBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
                if (argbBitmap !== sourceBitmap) {
                    sourceBitmap.recycle()
                }

                val width = argbBitmap.width
                val height = argbBitmap.height
                val pixels = IntArray(width * height)
                argbBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                val grayscalePixels = NativeBridge.applyGrayscaleArgb8888(
                    pixels = pixels,
                    width = width,
                    height = height
                )
                if (grayscalePixels.size != pixels.size) {
                    throw IllegalStateException("Native grayscale conversion failed")
                }

                val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                grayscaleBitmap.setPixels(grayscalePixels, 0, width, 0, 0, width, height)
                val savedUri = mediaStoreManager.saveJpegBitmap(grayscaleBitmap, prefix = "xin_gray")

                argbBitmap.recycle()
                grayscaleBitmap.recycle()

                mainExecutor.execute {
                    onSaved(savedUri.toString())
                }
            } catch (error: Exception) {
                mainExecutor.execute {
                    onError(error.message ?: "Grayscale processing failed")
                }
            }
        }
    }
}
