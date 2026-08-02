package com.seanchen.xincamera.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.seanchen.xincamera.domain.model.ProfessionalCameraCapabilities
import com.seanchen.xincamera.domain.model.ProfessionalCameraSettings
import com.seanchen.xincamera.domain.model.PhotoCaptureResult
import com.seanchen.xincamera.storage.MediaStoreManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 相机模块总入口。
 *
 * Controller 只做编排：生命周期绑定、状态观察和对外 API。CameraX use case、
 * 拍照存储、专业参数和帧分析分别交给专门的 manager。
 */
class CameraController(
    private val context: Context
) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val imageProcessingExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val useCaseManager = CameraUseCaseManager()
    private val settingsManager = CameraSettingsManager()
    private val captureManager = CameraCaptureManager(
        mediaStoreManager = MediaStoreManager(context),
        mainExecutor = mainExecutor,
        imageProcessingExecutor = imageProcessingExecutor
    )

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var captureOutputFormat: Int = ImageCapture.OUTPUT_FORMAT_JPEG
    private var previewView: PreviewView? = null
    private var zoomObserver: Observer<ZoomState>? = null
    private var torchObserver: Observer<Int>? = null

    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int,
        rawCaptureRequested: Boolean,
        onZoomChanged: (zoomRatio: Float, minZoomRatio: Float, maxZoomRatio: Float) -> Unit,
        onTorchAvailabilityChanged: (Boolean) -> Unit,
        onTorchStateChanged: (Boolean) -> Unit,
        onProfessionalCapabilitiesChanged: (ProfessionalCameraCapabilities) -> Unit,
        onRawAvailabilityChanged: (Boolean) -> Unit,
        onHistogramChanged: (IntArray) -> Unit,
        onError: (String) -> Unit
    ) {
        this.previewView = previewView
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                val provider = providerFuture.get()
                clearObservers()

                try {
                    val rawOutputFormat = useCaseManager.preferredRawOutputFormat(provider, lensFacing)
                    val requestedOutputFormat = if (rawCaptureRequested) {
                        rawOutputFormat ?: ImageCapture.OUTPUT_FORMAT_JPEG
                    } else {
                        ImageCapture.OUTPUT_FORMAT_JPEG
                    }
                    onRawAvailabilityChanged(rawOutputFormat != null)
                    val analyzer = Analyzer(
                        mainExecutor = mainExecutor,
                        onHistogramChanged = onHistogramChanged,
                        onError = onError
                    )
                    val useCases = useCaseManager.createUseCases(
                        previewView = previewView,
                        analysisExecutor = analysisExecutor,
                        analyzer = analyzer,
                        captureOutputFormat = requestedOutputFormat
                    )
                    val boundCamera = useCaseManager.bindToLifecycle(
                        provider = provider,
                        lifecycleOwner = lifecycleOwner,
                        lensFacing = lensFacing,
                        useCases = useCases
                    )
                    camera = boundCamera
                    imageCapture = useCases.imageCapture
                    imageAnalysis = useCases.imageAnalysis
                    captureOutputFormat = requestedOutputFormat
                    if (useCases.imageAnalysis == null) {
                        onHistogramChanged(IntArray(256))
                    }

                    val hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit()
                    onTorchAvailabilityChanged(hasFlashUnit)
                    if (!hasFlashUnit) {
                        boundCamera.cameraControl.enableTorch(false)
                    }

                    onProfessionalCapabilitiesChanged(
                        settingsManager.buildProfessionalCapabilities(boundCamera)
                    )
                    settingsManager.applyCurrentSettings(boundCamera)

                    zoomObserver = Observer<ZoomState> { zoomState ->
                        onZoomChanged(
                            zoomState.zoomRatio,
                            zoomState.minZoomRatio,
                            zoomState.maxZoomRatio
                        )
                    }.also { observer ->
                        boundCamera.cameraInfo.zoomState.observe(lifecycleOwner, observer)
                    }

                    torchObserver = Observer<Int> { torchState ->
                        onTorchStateChanged(torchState == TorchState.ON)
                    }.also { observer ->
                        boundCamera.cameraInfo.torchState.observe(lifecycleOwner, observer)
                    }
                } catch (error: Exception) {
                    camera = null
                    imageCapture = null
                    imageAnalysis = null
                    captureOutputFormat = ImageCapture.OUTPUT_FORMAT_JPEG
                    onError(error.message ?: "Camera binding failed")
                }
            },
            mainExecutor
        )
    }

    fun updateProfessionalSettings(settings: ProfessionalCameraSettings) {
        settingsManager.updateProfessionalSettings(camera, settings)
    }

    fun capturePhoto(
        onSaved: (PhotoCaptureResult) -> Unit,
        onError: (String) -> Unit
    ) {
        captureManager.capturePhoto(
            imageCapture = imageCapture,
            captureOutputFormat = captureOutputFormat,
            onSaved = onSaved,
            onError = onError
        )
    }

    fun saveGrayscaleCopy(
        sourceUriString: String,
        onSaved: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        captureManager.saveGrayscaleCopy(
            sourceUriString = sourceUriString,
            onSaved = onSaved,
            onError = onError
        )
    }

    fun setZoomRatio(zoomRatio: Float) {
        val zoomState = camera?.cameraInfo?.zoomState?.value ?: return
        val clampedZoom = zoomRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        camera?.cameraControl?.setZoomRatio(clampedZoom)
    }

    fun zoomBy(scaleFactor: Float) {
        val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: return
        setZoomRatio(currentZoomRatio * scaleFactor)
    }

    fun setTorchEnabled(enabled: Boolean) {
        val currentCamera = camera ?: return
        if (!currentCamera.cameraInfo.hasFlashUnit()) {
            return
        }
        currentCamera.cameraControl.enableTorch(enabled)
    }

    fun focusAt(x: Float, y: Float): Boolean {
        val currentPreviewView = previewView ?: return false
        val currentCamera = camera ?: return false
        val meteringPoint = currentPreviewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            meteringPoint,
            FocusMeteringAction.FLAG_AF or
                FocusMeteringAction.FLAG_AE or
                FocusMeteringAction.FLAG_AWB
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()

        currentCamera.cameraControl.startFocusAndMetering(action)
        return true
    }

    fun unbind() {
        clearObservers()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                providerFuture.get().unbindAll()
                camera = null
                imageCapture = null
                imageAnalysis = null
                captureOutputFormat = ImageCapture.OUTPUT_FORMAT_JPEG
            },
            mainExecutor
        )
    }

    private fun clearObservers() {
        val currentCamera = camera ?: return
        zoomObserver?.let { currentCamera.cameraInfo.zoomState.removeObserver(it) }
        torchObserver?.let { currentCamera.cameraInfo.torchState.removeObserver(it) }
        zoomObserver = null
        torchObserver = null
    }
}
