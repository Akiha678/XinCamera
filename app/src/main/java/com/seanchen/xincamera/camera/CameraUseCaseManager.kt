package com.seanchen.xincamera.camera

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService

/**
 * CameraX UseCase 管理器。
 *
 * 这一层只负责创建和绑定 CameraX use case，不处理 UI 状态、MediaStore 或 JNI 算法。
 */
class CameraUseCaseManager {
    fun createUseCases(
        previewView: PreviewView,
        analysisExecutor: ExecutorService,
        analyzer: Analyzer,
        captureOutputFormat: Int
    ): CameraUseCases {
        val preview = Preview.Builder().build().also { useCase ->
            useCase.surfaceProvider = previewView.surfaceProvider
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(
                if (captureOutputFormat != ImageCapture.OUTPUT_FORMAT_JPEG) {
                    ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                } else {
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                }
            )
            .setOutputFormat(captureOutputFormat)
            .build()
        // RAW + JPEG 同拍已经占用两个高分辨率输出流。部分 RAW 设备无法再同时承载
        // YUV 分析流，因此该模式暂停直方图，避免整个相机会话绑定失败。
        val imageAnalysis = if (captureOutputFormat == ImageCapture.OUTPUT_FORMAT_RAW_JPEG) {
            null
        } else {
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { useCase -> useCase.setAnalyzer(analysisExecutor, analyzer) }
        }

        return CameraUseCases(
            preview = preview,
            imageCapture = imageCapture,
            imageAnalysis = imageAnalysis
        )
    }

    fun preferredRawOutputFormat(
        provider: ProcessCameraProvider,
        lensFacing: Int
    ): Int? {
        val capabilities = ImageCapture.getImageCaptureCapabilities(
            provider.getCameraInfo(cameraSelector(lensFacing))
        )
        return when {
            capabilities.supportedOutputFormats.contains(ImageCapture.OUTPUT_FORMAT_RAW_JPEG) ->
                ImageCapture.OUTPUT_FORMAT_RAW_JPEG
            capabilities.supportedOutputFormats.contains(ImageCapture.OUTPUT_FORMAT_RAW) ->
                ImageCapture.OUTPUT_FORMAT_RAW
            else -> null
        }
    }

    fun bindToLifecycle(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int,
        useCases: CameraUseCases
    ): Camera {
        provider.unbindAll()
        val boundUseCases = buildList {
            add(useCases.preview)
            add(useCases.imageCapture)
            useCases.imageAnalysis?.let(::add)
        }
        return provider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector(lensFacing),
            *boundUseCases.toTypedArray()
        )
    }

    private fun cameraSelector(lensFacing: Int): CameraSelector =
        CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
}

data class CameraUseCases(
    val preview: Preview,
    val imageCapture: ImageCapture,
    val imageAnalysis: ImageAnalysis?
)
