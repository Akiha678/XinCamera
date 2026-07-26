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
        analyzer: Analyzer
    ): CameraUseCases {
        val preview = Preview.Builder().build().also { useCase ->
            useCase.surfaceProvider = previewView.surfaceProvider
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(analysisExecutor, analyzer)
            }

        return CameraUseCases(
            preview = preview,
            imageCapture = imageCapture,
            imageAnalysis = imageAnalysis
        )
    }

    fun bindToLifecycle(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int,
        useCases: CameraUseCases
    ): Camera {
        provider.unbindAll()
        return provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build(),
            useCases.preview,
            useCases.imageCapture,
            useCases.imageAnalysis
        )
    }
}

data class CameraUseCases(
    val preview: Preview,
    val imageCapture: ImageCapture,
    val imageAnalysis: ImageAnalysis
)
