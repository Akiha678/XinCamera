package com.seanchen.xincamera.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Environment
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 预览控制器把 CameraX 的能力整理成 UI 可以直接调用的接口。
 *
 * 当前负责：
 * 1. 预览绑定
 * 2. 变焦 / 点击对焦 / 手电筒
 * 3. 拍照
 * 4. 第二部分专业模式参数下发
 */
class CameraPreviewController(
    private val context: Context
) {
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private var zoomObserver: Observer<ZoomState>? = null
    private var torchObserver: Observer<Int>? = null
    private var professionalSettings = ProfessionalCameraSettings()

    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int,
        onZoomChanged: (zoomRatio: Float, minZoomRatio: Float, maxZoomRatio: Float) -> Unit,
        onTorchAvailabilityChanged: (Boolean) -> Unit,
        onTorchStateChanged: (Boolean) -> Unit,
        onProfessionalCapabilitiesChanged: (ProfessionalCameraCapabilities) -> Unit,
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
                val captureUseCase = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                try {
                    val boundCamera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build(),
                        preview,
                        captureUseCase
                    )
                    camera = boundCamera
                    imageCapture = captureUseCase

                    val hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit()
                    onTorchAvailabilityChanged(hasFlashUnit)
                    if (!hasFlashUnit) {
                        boundCamera.cameraControl.enableTorch(false)
                    }

                    onProfessionalCapabilitiesChanged(
                        buildProfessionalCapabilities(boundCamera)
                    )
                    applyProfessionalSettings(boundCamera)

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
                    onError(error.message ?: "Camera binding failed")
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    /**
     * UI 修改设置后先缓存一份。
     *
     * 这样即使用户切镜头或者页面重绑，相机重新就绪后也能恢复当前面板值。
     */
    fun updateProfessionalSettings(settings: ProfessionalCameraSettings) {
        professionalSettings = settings
        camera?.let(::applyProfessionalSettings)
    }

    /**
     * 拍照保存到应用专属图片目录，避免为了演示版本再额外申请存储权限。
     */
    fun capturePhoto(
        onSaved: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val captureUseCase = imageCapture ?: run {
            onError("ImageCapture not ready")
            return
        }
        val pictureDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "captures"
        ).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        if (!pictureDir.exists()) {
            onError("Failed to create capture directory")
            return
        }

        val outputFile = File(
            pictureDir,
            "xin_${TIMESTAMP_FORMAT.format(Date())}.jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        captureUseCase.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(
                    outputFileResults: ImageCapture.OutputFileResults
                ) {
                    onSaved(outputFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "Capture failed")
                }
            }
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

    @OptIn(ExperimentalCamera2Interop::class)
    private fun buildProfessionalCapabilities(
        targetCamera: Camera
    ): ProfessionalCameraCapabilities {
        val cameraInfo = Camera2CameraInfo.from(targetCamera.cameraInfo)
        val sensitivityRange = cameraInfo.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
        )
        val exposureTimeRange = cameraInfo.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
        )
        val capabilities = cameraInfo.getCameraCharacteristic(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
        ) ?: intArrayOf()
        val supportsManualExposure = capabilities.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        ) && sensitivityRange != null && exposureTimeRange != null

        return ProfessionalCameraCapabilities(
            supportsManualExposure = supportsManualExposure,
            isoMin = sensitivityRange?.lower ?: 100,
            isoMax = sensitivityRange?.upper ?: 100,
            exposureTimeMinNs = exposureTimeRange?.lower ?: 1_000_000L,
            exposureTimeMaxNs = exposureTimeRange?.upper ?: 1_000_000L
        )
    }

    /**
     * 专业参数通过 Camera2Interop 写到重复请求中。
     *
     * ISO 和快门只有在用户明确打开手动曝光时才关闭 AE，
     * 白平衡则始终可以按预设切换。
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyProfessionalSettings(
        targetCamera: Camera
    ) {
        val camera2Control = Camera2CameraControl.from(targetCamera.cameraControl)
        val iso = professionalSettings.iso
        val exposureTimeNs = professionalSettings.exposureTimeNs
        val manualExposureEnabled = iso != null && exposureTimeNs != null

        val requestOptions = CaptureRequestOptions.Builder().apply {
            if (manualExposureEnabled) {
                setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )
                setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    iso
                )
                setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    exposureTimeNs
                )
            } else {
                setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
            }

            setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                professionalSettings.whiteBalancePreset.awbMode
            )
        }.build()

        camera2Control.setCaptureRequestOptions(requestOptions)
    }

    private companion object {
        val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    }
}
