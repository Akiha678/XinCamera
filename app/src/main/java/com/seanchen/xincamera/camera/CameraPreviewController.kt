package com.seanchen.xincamera.camera

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.seanchen.xincamera.nativebridge.NativeBridge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 预览控制器把 CameraX 的能力整理成 UI 可以直接调用的接口。
 *
 * 当前负责：
 * 1. 预览绑定
 * 2. 变焦 / 点击对焦 / 手电筒
 * 3. 拍照
 * 4. 第三部分直方图分析
 * 5. 第二部分专业模式参数下发
 */
class CameraPreviewController(
    private val context: Context
) {
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var previewView: PreviewView? = null
    private var zoomObserver: Observer<ZoomState>? = null
    private var torchObserver: Observer<Int>? = null
    private var professionalSettings = ProfessionalCameraSettings()
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val imageProcessingExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lastHistogramAtMs = 0L

    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int,
        onZoomChanged: (zoomRatio: Float, minZoomRatio: Float, maxZoomRatio: Float) -> Unit,
        onTorchAvailabilityChanged: (Boolean) -> Unit,
        onTorchStateChanged: (Boolean) -> Unit,
        onProfessionalCapabilitiesChanged: (ProfessionalCameraCapabilities) -> Unit,
        onHistogramChanged: (IntArray) -> Unit,
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
                val analysisUseCase = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(analysisExecutor) { imageProxy ->
                            analyzeHistogramFrame(
                                imageProxy = imageProxy,
                                onHistogramChanged = onHistogramChanged,
                                onError = onError
                            )
                        }
                    }

                try {
                    val boundCamera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build(),
                        preview,
                        captureUseCase,
                        analysisUseCase
                    )
                    camera = boundCamera
                    imageCapture = captureUseCase
                    imageAnalysis = analysisUseCase

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
     * 拍照直接写入系统相册。
     *
     * Android 10+ 走 MediaStore，拍完即可出现在图库里；
     * Android 9 及以下因为有 maxSdkVersion 限制，仍然可以写入外部存储。
     */
    fun capturePhoto(
        onSaved: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val captureUseCase = imageCapture ?: run {
            onError("ImageCapture not ready")
            return
        }
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "xin_${TIMESTAMP_FORMAT.format(Date())}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/XinCamera"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        captureUseCase.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(
                    outputFileResults: ImageCapture.OutputFileResults
                ) {
                    val uri = outputFileResults.savedUri
                    if (uri != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val finalizeValues = android.content.ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        }
                        context.contentResolver.update(uri, finalizeValues, null, null)
                    }
                    onSaved(uri?.toString() ?: "saved")
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "Capture failed")
                }
            }
        )
    }

    /**
     * 第四部分灰度图处理。
     *
     * 这里负责相册 URI 的读取和保存，真正的灰度算法在 JNI/C++ 中执行。
     * 处理完成后会写入一张新的 JPEG 到系统相册 Pictures/XinCamera。
     */
    fun saveGrayscaleCopy(
        sourceUriString: String,
        onSaved: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        imageProcessingExecutor.execute {
            try {
                val sourceUri = Uri.parse(sourceUriString)
                val sourceBitmap = decodeBitmap(sourceUri)
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
                val savedUri = saveBitmapToGallery(grayscaleBitmap)

                argbBitmap.recycle()
                grayscaleBitmap.recycle()

                ContextCompat.getMainExecutor(context).execute {
                    onSaved(savedUri.toString())
                }
            } catch (error: Exception) {
                ContextCompat.getMainExecutor(context).execute {
                    onError(error.message ?: "Grayscale processing failed")
                }
            }
        }
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

    private fun analyzeHistogramFrame(
        imageProxy: ImageProxy,
        onHistogramChanged: (IntArray) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHistogramAtMs < HISTOGRAM_INTERVAL_MS) {
                return
            }
            lastHistogramAtMs = now

            val yPlane = imageProxy.planes.firstOrNull() ?: return
            val buffer = yPlane.buffer
            val yBytes = ByteArray(buffer.remaining())
            buffer.get(yBytes)

            val histogram = NativeBridge.computeLumaHistogram(
                yPlane = yBytes,
                width = imageProxy.width,
                height = imageProxy.height,
                rowStride = yPlane.rowStride,
                pixelStride = yPlane.pixelStride
            )
            ContextCompat.getMainExecutor(context).execute {
                onHistogramChanged(histogram)
            }
        } catch (error: Exception) {
            ContextCompat.getMainExecutor(context).execute {
                onError(error.message ?: "Histogram analysis failed")
            }
        } finally {
            imageProxy.close()
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeBitmap(uri: Uri): Bitmap {
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

    private fun saveBitmapToGallery(bitmap: Bitmap): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "xin_gray_${TIMESTAMP_FORMAT.format(Date())}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/XinCamera"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: throw IllegalStateException("Cannot create gallery item")

        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                    throw IllegalStateException("Cannot encode grayscale image")
                }
            } ?: throw IllegalStateException("Cannot open gallery output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finalizeValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, finalizeValues, null, null)
            }
            return uri
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
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
        const val HISTOGRAM_INTERVAL_MS = 120L
        val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    }
}
