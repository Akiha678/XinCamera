package com.seanchen.xincamera.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.seanchen.xincamera.domain.model.PhotoCaptureResult
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
        captureOutputFormat: Int,
        onSaved: (PhotoCaptureResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val captureUseCase = imageCapture ?: run {
            onError("ImageCapture not ready")
            return
        }

        when (captureOutputFormat) {
            ImageCapture.OUTPUT_FORMAT_RAW_JPEG ->
                captureRawAndJpeg(captureUseCase, onSaved, onError)
            ImageCapture.OUTPUT_FORMAT_RAW ->
                captureRawOnly(captureUseCase, onSaved, onError)
            else -> captureJpeg(captureUseCase, onSaved, onError)
        }
    }

    private fun captureJpeg(
        captureUseCase: ImageCapture,
        onSaved: (PhotoCaptureResult) -> Unit,
        onError: (String) -> Unit
    ) {
        captureUseCase.takePicture(
            mediaStoreManager.createPhotoOutputOptions(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val uri = outputFileResults.savedUri
                    if (uri != null) {
                        mediaStoreManager.finalizePendingImage(uri)
                    }
                    onSaved(PhotoCaptureResult(jpegUri = uri?.toString()))
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "Capture failed")
                }
            }
        )
    }

    private fun captureRawAndJpeg(
        captureUseCase: ImageCapture,
        onSaved: (PhotoCaptureResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val rawTempFile = mediaStoreManager.createRawTempFile()
        var jpegUri: String? = null
        var rawUri: String? = null
        var rawSizeBytes: Long? = null
        var rawFingerprint: String? = null
        var jpegFinished = false
        var rawFinished = false
        var terminalCallbackSent = false

        fun finishIfComplete() {
            if (!terminalCallbackSent && jpegFinished && rawFinished) {
                terminalCallbackSent = true
                onSaved(
                    PhotoCaptureResult(
                        jpegUri = jpegUri,
                        rawUri = rawUri,
                        rawSizeBytes = rawSizeBytes,
                        rawFingerprint = rawFingerprint
                    )
                )
            }
        }

        fun fail(message: String) {
            if (!terminalCallbackSent) {
                terminalCallbackSent = true
                rawTempFile.delete()
                onError(message)
            }
        }

        try {
            captureUseCase.takePicture(
                mediaStoreManager.createRawOutputOptions(rawTempFile),
                mediaStoreManager.createPhotoOutputOptions(),
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(
                        outputFileResults: ImageCapture.OutputFileResults
                    ) {
                        val savedUri = outputFileResults.savedUri
                        val isRaw = outputFileResults.imageFormat == ImageFormat.RAW_SENSOR
                        if (!isRaw) {
                            if (savedUri == null) {
                                fail("JPEG 文件保存失败")
                                return
                            }
                            mediaStoreManager.finalizePendingImage(savedUri)
                            jpegUri = savedUri.toString()
                            jpegFinished = true
                            finishIfComplete()
                            return
                        }

                        imageProcessingExecutor.execute {
                            try {
                                val summary = NativeBridge.inspectRawDng(rawTempFile.absolutePath)
                                if (!summary.isValid) {
                                    throw IllegalStateException("JNI 检测到无效的 DNG 文件")
                                }
                                val importedRawUri = mediaStoreManager.saveDngFile(rawTempFile)
                                mainExecutor.execute {
                                    rawUri = importedRawUri.toString()
                                    rawSizeBytes = summary.sizeBytes
                                    rawFingerprint = summary.fingerprint
                                    rawFinished = true
                                    finishIfComplete()
                                }
                            } catch (error: Exception) {
                                mainExecutor.execute {
                                    fail(error.message ?: "RAW 文件处理失败")
                                }
                            } finally {
                                rawTempFile.delete()
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        fail(exception.message ?: "RAW 拍摄失败")
                    }
                }
            )
        } catch (error: Exception) {
            fail(error.message ?: "当前相机无法启动 RAW 拍摄")
        }
    }

    private fun captureRawOnly(
        captureUseCase: ImageCapture,
        onSaved: (PhotoCaptureResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val rawTempFile = mediaStoreManager.createRawTempFile()
        try {
            captureUseCase.takePicture(
                mediaStoreManager.createRawOutputOptions(rawTempFile),
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(
                        outputFileResults: ImageCapture.OutputFileResults
                    ) {
                        imageProcessingExecutor.execute {
                            try {
                                val summary = NativeBridge.inspectRawDng(rawTempFile.absolutePath)
                                if (!summary.isValid) {
                                    throw IllegalStateException("JNI 检测到无效的 DNG 文件")
                                }
                                val rawUri = mediaStoreManager.saveDngFile(rawTempFile)
                                mainExecutor.execute {
                                    onSaved(
                                        PhotoCaptureResult(
                                            rawUri = rawUri.toString(),
                                            rawSizeBytes = summary.sizeBytes,
                                            rawFingerprint = summary.fingerprint
                                        )
                                    )
                                }
                            } catch (error: Exception) {
                                mainExecutor.execute {
                                    onError(error.message ?: "RAW 文件处理失败")
                                }
                            } finally {
                                rawTempFile.delete()
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        rawTempFile.delete()
                        onError(exception.message ?: "RAW 拍摄失败")
                    }
                }
            )
        } catch (error: Exception) {
            rawTempFile.delete()
            onError(error.message ?: "当前相机无法启动 RAW 拍摄")
        }
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
