package com.seanchen.xincamera.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import java.util.concurrent.TimeUnit

class CameraPreviewController(
    private val context: Context
) {
    private var camera: Camera? = null
    private var previewView: PreviewView? = null
    private var zoomObserver: Observer<androidx.camera.core.ZoomState>? = null
    private var torchObserver: Observer<Int>? = null

    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int,
        onZoomChanged: (zoomRatio: Float, minZoomRatio: Float, maxZoomRatio: Float) -> Unit,
        onTorchAvailabilityChanged: (Boolean) -> Unit,
        onTorchStateChanged: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        this.previewView = previewView
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                val provider = providerFuture.get()
                clearObservers()
                provider.unbindAll()

                val preview = Preview.Builder().build().also { useCase ->
                    useCase.surfaceProvider = previewView.surfaceProvider
                }

                try {
                    val boundCamera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build(),
                        preview
                    )
                    camera = boundCamera

                    val hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit()
                    onTorchAvailabilityChanged(hasFlashUnit)
                    if (!hasFlashUnit) {
                        boundCamera.cameraControl.enableTorch(false)
                    }

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
                    onError(error.message ?: "Camera binding failed")
                }
            },
            ContextCompat.getMainExecutor(context)
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
            },
            ContextCompat.getMainExecutor(context)
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
