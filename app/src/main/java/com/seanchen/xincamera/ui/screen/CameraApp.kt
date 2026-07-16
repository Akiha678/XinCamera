package com.seanchen.xincamera.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.seanchen.xincamera.R
import com.seanchen.xincamera.camera.CameraPreviewController
import com.seanchen.xincamera.camera.ProfessionalCameraCapabilities
import com.seanchen.xincamera.camera.ProfessionalCameraSettings
import com.seanchen.xincamera.camera.WhiteBalancePreset
import kotlin.math.exp
import kotlin.math.ln
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CameraApp(
    nativeStatus: String
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraScreen(nativeStatus = nativeStatus)
    } else {
        PermissionScreen(
            onGrantPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
        )
    }
}

@Composable
private fun PermissionScreen(
    onGrantPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xCC11161C))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.camera_permission_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Text(
                text = stringResource(R.string.camera_permission_message),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD8DEE5)
            )
            Button(onClick = onGrantPermission) {
                Text(text = stringResource(R.string.camera_permission_action))
            }
        }
    }
}

/**
 * 主相机页由 CameraX 预览和 Compose 覆盖层组成。
 *
 * 覆盖层固定放置顶部 Setting 入口、底部拍摄按钮、变焦和第一部分控制按钮。
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun CameraScreen(
    nativeStatus: String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember(context) {
        CameraPreviewController(context.applicationContext)
    }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var zoomRatio by rememberSaveable { mutableFloatStateOf(1f) }
    var minZoomRatio by rememberSaveable { mutableFloatStateOf(1f) }
    var maxZoomRatio by rememberSaveable { mutableFloatStateOf(1f) }
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var torchAvailable by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf("") }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showSettingsPanel by rememberSaveable { mutableStateOf(false) }
    var isCapturing by rememberSaveable { mutableStateOf(false) }
    var isProcessingGrayscale by rememberSaveable { mutableStateOf(false) }
    var lastCapturedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var manualExposureEnabled by rememberSaveable { mutableStateOf(false) }
    var professionalCapabilities by remember {
        mutableStateOf(ProfessionalCameraCapabilities())
    }
    var selectedIso by rememberSaveable { mutableIntStateOf(100) }
    var selectedExposureRatio by rememberSaveable { mutableFloatStateOf(0.45f) }
    var selectedWhiteBalanceIndex by rememberSaveable {
        mutableIntStateOf(WhiteBalancePreset.AUTO.ordinal)
    }
    var histogramBins by remember { mutableStateOf(IntArray(256)) }
    val focusScope = rememberCoroutineScope()
    val whiteBalancePreset = WhiteBalancePreset.entries[selectedWhiteBalanceIndex]

    val gestureDetector = remember(previewView, cameraController) {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    val width = previewView.width.toFloat().takeIf { it > 0f } ?: return false
                    val height = previewView.height.toFloat().takeIf { it > 0f } ?: return false
                    val didFocus = cameraController.focusAt(event.x, event.y)
                    if (didFocus) {
                        focusPoint = Offset(event.x / width, event.y / height)
                        statusMessage = "对焦完成"
                        focusScope.launch {
                            delay(900)
                            focusPoint = null
                        }
                    }
                    return didFocus
                }
            }
        )
    }
    val scaleGestureDetector = remember(previewView, cameraController) {
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    cameraController.zoomBy(detector.scaleFactor)
                    return true
                }
            }
        )
    }

    DisposableEffect(previewView, gestureDetector, scaleGestureDetector) {
        previewView.setOnTouchListener { _, motionEvent ->
            val scaleHandled = scaleGestureDetector.onTouchEvent(motionEvent)
            val gestureHandled = if (!scaleGestureDetector.isInProgress) {
                gestureDetector.onTouchEvent(motionEvent)
            } else {
                false
            }
            scaleHandled || gestureHandled || motionEvent.actionMasked == MotionEvent.ACTION_DOWN
        }
        onDispose {
            previewView.setOnTouchListener(null)
        }
    }

    DisposableEffect(lifecycleOwner, lensFacing) {
        cameraController.bindToLifecycle(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            lensFacing = lensFacing,
            onZoomChanged = { currentZoomRatio, minZoom, maxZoom ->
                zoomRatio = currentZoomRatio
                minZoomRatio = minZoom
                maxZoomRatio = maxZoom
            },
            onTorchAvailabilityChanged = { available ->
                torchAvailable = available
                if (!available) {
                    torchEnabled = false
                }
            },
            onTorchStateChanged = { enabled ->
                torchEnabled = enabled
            },
            onProfessionalCapabilitiesChanged = { capabilities ->
                professionalCapabilities = capabilities
                selectedIso = selectedIso.coerceIn(capabilities.isoMin, capabilities.isoMax)
                if (!capabilities.supportsManualExposure) {
                    manualExposureEnabled = false
                }
            },
            onHistogramChanged = { histogram ->
                histogramBins = histogram
            },
            onError = { error ->
                statusMessage = error
            }
        )
        onDispose {
            cameraController.unbind()
        }
    }

    LaunchedEffect(
        manualExposureEnabled,
        selectedIso,
        selectedExposureRatio,
        selectedWhiteBalanceIndex,
        professionalCapabilities
    ) {
        cameraController.updateProfessionalSettings(
            ProfessionalCameraSettings(
                iso = if (manualExposureEnabled && professionalCapabilities.supportsManualExposure) {
                    selectedIso.coerceIn(
                        professionalCapabilities.isoMin,
                        professionalCapabilities.isoMax
                    )
                } else {
                    null
                },
                exposureTimeNs = if (manualExposureEnabled && professionalCapabilities.supportsManualExposure) {
                    ratioToExposureTimeNs(
                        ratio = selectedExposureRatio,
                        minNs = professionalCapabilities.exposureTimeMinNs,
                        maxNs = professionalCapabilities.exposureTimeMaxNs
                    )
                } else {
                    null
                },
                whiteBalancePreset = whiteBalancePreset
            )
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        TopSafeGradient()
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomSafeGradient()
        }

        HistogramOverlay(
            histogram = histogramBins,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(top = 96.dp, end = 16.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            CameraHud(
                nativeStatus = nativeStatus,
                zoomRatio = zoomRatio,
                lensFacing = lensFacing,
                manualExposureEnabled = manualExposureEnabled,
                whiteBalancePreset = whiteBalancePreset,
                showSettingsPanel = showSettingsPanel,
                statusMessage = statusMessage,
                onToggleSettings = { showSettingsPanel = !showSettingsPanel }
            )

            CameraBottomControls(
                zoomRatio = zoomRatio,
                minZoomRatio = minZoomRatio,
                maxZoomRatio = maxZoomRatio,
                torchEnabled = torchEnabled,
                torchAvailable = torchAvailable,
                isCapturing = isCapturing,
                isProcessingGrayscale = isProcessingGrayscale,
                hasCapturedPhoto = lastCapturedUri != null,
                onZoomChanged = cameraController::setZoomRatio,
                onToggleTorch = {
                    if (torchAvailable) {
                        cameraController.setTorchEnabled(!torchEnabled)
                    } else {
                        statusMessage = "无闪光灯"
                    }
                },
                onCapture = {
                    if (isCapturing) {
                        return@CameraBottomControls
                    }
                    isCapturing = true
                    statusMessage = "正在拍摄"
                    cameraController.capturePhoto(
                        onSaved = { outputPath ->
                            isCapturing = false
                            lastCapturedUri = outputPath.takeIf { it.startsWith("content://") }
                            statusMessage = ""
                        },
                        onError = { error ->
                            isCapturing = false
                            statusMessage = error
                        }
                    )
                },
                onCreateGrayscale = {
                    val sourceUri = lastCapturedUri
                    if (sourceUri == null) {
                        statusMessage = "请先拍摄照片"
                        return@CameraBottomControls
                    }
                    if (isProcessingGrayscale) {
                        return@CameraBottomControls
                    }
                    isProcessingGrayscale = true
                    statusMessage = "正在生成灰度图"
                    cameraController.saveGrayscaleCopy(
                        sourceUriString = sourceUri,
                        onSaved = {
                            isProcessingGrayscale = false
                            statusMessage = "灰度图已保存到相册"
                        },
                        onError = { error ->
                            isProcessingGrayscale = false
                            statusMessage = error
                        }
                    )
                },
                onSwitchLens = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                    statusMessage = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        "已切换至后置摄像头"
                    } else {
                        "已切换至前置摄像头"
                    }
                }
            )
        }

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 142.dp),
            visible = showSettingsPanel,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            ProfessionalSettingsPanel(
                capabilities = professionalCapabilities,
                manualExposureEnabled = manualExposureEnabled,
                selectedIso = selectedIso,
                exposureRatio = selectedExposureRatio,
                whiteBalancePreset = whiteBalancePreset,
                statusMessage = statusMessage,
                onDismiss = { showSettingsPanel = false },
                onManualExposureEnabledChange = { enabled ->
                    if (professionalCapabilities.supportsManualExposure) {
                        manualExposureEnabled = enabled
                        statusMessage = if (enabled) {
                            "手动曝光"
                        } else {
                            "自动曝光"
                        }
                    }
                },
                onIsoChanged = { iso ->
                    if (professionalCapabilities.supportsManualExposure) {
                        manualExposureEnabled = true
                        selectedIso = iso
                    }
                },
                onExposureRatioChanged = { ratio ->
                    if (professionalCapabilities.supportsManualExposure) {
                        manualExposureEnabled = true
                        selectedExposureRatio = ratio
                    }
                },
                onWhiteBalanceChanged = { preset ->
                    selectedWhiteBalanceIndex = preset.ordinal
                }
            )
        }

        focusPoint?.let { normalizedPoint ->
            FocusRing(
                x = (maxWidth * normalizedPoint.x) - 28.dp,
                y = (maxHeight * normalizedPoint.y) - 28.dp
            )
        }
    }
}

@Composable
private fun HistogramOverlay(
    histogram: IntArray,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(width = 180.dp, height = 104.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xB311161C),
        border = BorderStroke(1.dp, Color(0x66FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.camera_histogram_title),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
            ) {
                val maxCount = histogram.maxOrNull()?.takeIf { it > 0 } ?: 1
                val barWidth = size.width / histogram.size
                histogram.forEachIndexed { index, count ->
                    val normalizedHeight = size.height * count / maxCount
                    val x = index * barWidth
                    drawLine(
                        color = Color(0xFFFFD39A),
                        start = Offset(x, size.height),
                        end = Offset(x, size.height - normalizedHeight),
                        strokeWidth = (barWidth * 0.8f).coerceAtLeast(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraBottomControls(
    zoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    isCapturing: Boolean,
    isProcessingGrayscale: Boolean,
    hasCapturedPhoto: Boolean,
    onZoomChanged: (Float) -> Unit,
    onToggleTorch: () -> Unit,
    onCapture: () -> Unit,
    onCreateGrayscale: () -> Unit,
    onSwitchLens: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xB30B1016),
        border = BorderStroke(1.dp, Color(0x22FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${String.format("%.1f", zoomRatio)}x",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    modifier = Modifier.weight(1f),
                    value = zoomRatio.coerceIn(minZoomRatio, maxZoomRatio),
                    onValueChange = onZoomChanged,
                    valueRange = minZoomRatio..maxZoomRatio
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolIconButton(
                    isActive = torchEnabled,
                    isEnabled = torchAvailable,
                    onClick = onToggleTorch
                ) {
                    FlashGlyph(isActive = torchEnabled, isEnabled = torchAvailable)
                }

                CaptureButton(
                    isCapturing = isCapturing,
                    onClick = onCapture
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolIconButton(
                        isActive = isProcessingGrayscale,
                        isEnabled = hasCapturedPhoto && !isProcessingGrayscale,
                        onClick = onCreateGrayscale
                    ) {
                        GrayscaleGlyph(isActive = isProcessingGrayscale)
                    }

                    ToolIconButton(
                        isActive = false,
                        isEnabled = true,
                        onClick = onSwitchLens
                    ) {
                        SwitchCameraGlyph()
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolIconButton(
    isActive: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                when {
                    !isEnabled -> Color(0x552A313A)
                    isActive -> Color(0xFFFFD39A)
                    else -> Color(0xCC1B222B)
                }
            )
            .clickable(enabled = isEnabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun FlashGlyph(
    isActive: Boolean,
    isEnabled: Boolean
) {
    val color = when {
        !isEnabled -> Color(0x77FFFFFF)
        isActive -> Color(0xFF11161C)
        else -> Color.White
    }
    Canvas(modifier = Modifier.size(26.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.58f, 0f)
            lineTo(size.width * 0.22f, size.height * 0.54f)
            lineTo(size.width * 0.50f, size.height * 0.54f)
            lineTo(size.width * 0.38f, size.height)
            lineTo(size.width * 0.78f, size.height * 0.40f)
            lineTo(size.width * 0.50f, size.height * 0.40f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun GrayscaleGlyph(
    isActive: Boolean
) {
    Canvas(modifier = Modifier.size(28.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val frameColor = if (isActive) Color(0xFF11161C) else Color.White
        val darkTone = if (isActive) Color(0xFF11161C) else Color(0xFF8F99A6)
        val lightTone = if (isActive) Color(0xFF4D5560) else Color(0xFFE7EBEF)
        val circleTopLeft = Offset(size.width * 0.08f, size.height * 0.08f)
        val circleSize = Size(size.width * 0.84f, size.height * 0.84f)

        drawArc(
            color = darkTone,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = circleTopLeft,
            size = circleSize
        )
        drawArc(
            color = lightTone,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = circleTopLeft,
            size = circleSize
        )
        drawCircle(
            color = frameColor,
            radius = size.minDimension * 0.46f,
            center = center,
            style = stroke
        )
        drawLine(
            color = frameColor,
            start = Offset(size.width * 0.50f, size.height * 0.12f),
            end = Offset(size.width * 0.50f, size.height * 0.88f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SwitchCameraGlyph() {
    Canvas(modifier = Modifier.size(28.dp)) {
        val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        val arcSize = Size(size.width * 0.70f, size.height * 0.70f)
        drawArc(
            color = Color.White,
            startAngle = 210f,
            sweepAngle = 245f,
            useCenter = false,
            topLeft = Offset(size.width * 0.12f, size.height * 0.08f),
            size = arcSize,
            style = stroke
        )
        drawArc(
            color = Color.White,
            startAngle = 30f,
            sweepAngle = 245f,
            useCenter = false,
            topLeft = Offset(size.width * 0.18f, size.height * 0.20f),
            size = arcSize,
            style = stroke
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.72f, size.height * 0.10f),
            end = Offset(size.width * 0.88f, size.height * 0.18f),
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.88f, size.height * 0.18f),
            end = Offset(size.width * 0.78f, size.height * 0.34f),
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.28f, size.height * 0.90f),
            end = Offset(size.width * 0.12f, size.height * 0.82f),
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.12f, size.height * 0.82f),
            end = Offset(size.width * 0.22f, size.height * 0.66f),
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SettingsGlyph(
    isActive: Boolean
) {
    val color = if (isActive) Color(0xFF11161C) else Color.White
    Canvas(modifier = Modifier.size(26.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        val yPositions = listOf(0.28f, 0.50f, 0.72f)
        val knobPositions = listOf(0.66f, 0.34f, 0.58f)
        yPositions.forEachIndexed { index, yFactor ->
            val y = size.height * yFactor
            drawLine(
                color = color,
                start = Offset(size.width * 0.18f, y),
                end = Offset(size.width * 0.82f, y),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = color,
                radius = 3.4.dp.toPx(),
                center = Offset(size.width * knobPositions[index], y)
            )
        }
    }
}

@Composable
private fun CaptureRingGlyph(
    isCapturing: Boolean
) {
    Canvas(modifier = Modifier.size(78.dp)) {
        drawCircle(
            color = Color.White,
            radius = size.minDimension / 2f,
            center = center
        )
        drawCircle(
            color = if (isCapturing) Color(0xFFFFD39A) else Color(0xFF05070A),
            radius = size.minDimension * 0.40f,
            center = center
        )
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.30f,
            center = center
        )
        if (isCapturing) {
            drawCircle(
                color = Color(0xFF11161C),
                radius = size.minDimension * 0.16f,
                center = center
            )
        }
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xB311161C),
        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
        content = content
    )
}

@Composable
private fun MiniMeterLabel(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = Color(0x99FFFFFF),
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun CameraModeStrip(
    manualExposureEnabled: Boolean,
    whiteBalancePreset: WhiteBalancePreset
) {
    GlassPanel {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniMeterLabel(label = "MODE", value = if (manualExposureEnabled) "PRO" else "AUTO")
            MiniMeterLabel(label = "WB", value = whiteBalancePreset.name.take(4))
        }
    }
}

@Composable
private fun StatusText(
    statusMessage: String
) {
    Text(
        text = statusMessage.ifBlank {
            stringResource(R.string.camera_tap_focus_hint)
        },
        color = Color(0xDDEAF0F6),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun CameraHud(
    nativeStatus: String,
    zoomRatio: Float,
    lensFacing: Int,
    manualExposureEnabled: Boolean,
    whiteBalancePreset: WhiteBalancePreset,
    showSettingsPanel: Boolean,
    statusMessage: String,
    onToggleSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            GlassPanel {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%.1fx", zoomRatio),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 16.dp)
                            .background(Color(0x33FFFFFF))
                    )
                    Text(
                        text = if (lensFacing == CameraSelector.LENS_FACING_BACK) "REAR" else "FRONT",
                        color = Color(0xCCFFFFFF),
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (manualExposureEnabled) {
                        Text(
                            text = "PRO",
                            color = Color(0xFFFFD39A),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            SettingPill(
                isActive = showSettingsPanel,
                onClick = onToggleSettings
            )
        }
        CameraModeStrip(
            manualExposureEnabled = manualExposureEnabled,
            whiteBalancePreset = whiteBalancePreset
        )
        StatusText(statusMessage = statusMessage.ifBlank { nativeStatus })
    }
}

@Composable
private fun BottomSafeGradient() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xF0000000))
                )
            )
    )
}

@Composable
private fun TopSafeGradient() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xCC000000), Color.Transparent)
                )
            )
    )
}

@Composable
private fun ProfessionalSettingsPanel(
    capabilities: ProfessionalCameraCapabilities,
    manualExposureEnabled: Boolean,
    selectedIso: Int,
    exposureRatio: Float,
    whiteBalancePreset: WhiteBalancePreset,
    statusMessage: String,
    onDismiss: () -> Unit,
    onManualExposureEnabledChange: (Boolean) -> Unit,
    onIsoChanged: (Int) -> Unit,
    onExposureRatioChanged: (Float) -> Unit,
    onWhiteBalanceChanged: (WhiteBalancePreset) -> Unit
) {
    val context = LocalContext.current
    val exposureTimeNs = ratioToExposureTimeNs(
        ratio = exposureRatio,
        minNs = capabilities.exposureTimeMinNs,
        maxNs = capabilities.exposureTimeMaxNs
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeightIn(min = 320.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xF011161C)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.camera_setting_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.camera_setting_close))
                }
            }
            Text(
                text = statusMessage.ifBlank {
                    stringResource(R.string.camera_setting_hint)
                },
                color = Color(0xFFD8DEE5),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = { onManualExposureEnabledChange(!manualExposureEnabled) }
            ) {
                Text(
                    text = if (manualExposureEnabled) {
                        stringResource(R.string.camera_manual_enabled)
                    } else {
                        stringResource(R.string.camera_manual_disabled)
                    }
                )
            }
            ProSliderSection(
                title = stringResource(R.string.camera_iso_title),
                valueLabel = if (capabilities.supportsManualExposure) {
                    "ISO $selectedIso"
                } else {
                    stringResource(R.string.camera_setting_not_supported)
                },
                enabled = capabilities.supportsManualExposure,
                value = selectedIso.toFloat().coerceIn(
                    capabilities.isoMin.toFloat(),
                    capabilities.isoMax.toFloat()
                ),
                range = capabilities.isoMin.toFloat()..capabilities.isoMax.toFloat(),
                onValueChange = { onIsoChanged(it.toInt()) }
            )
            ProSliderSection(
                title = stringResource(R.string.camera_shutter_title),
                valueLabel = if (capabilities.supportsManualExposure) {
                    formatExposureTime(exposureTimeNs)
                } else {
                    stringResource(R.string.camera_setting_not_supported)
                },
                enabled = capabilities.supportsManualExposure,
                value = exposureRatio,
                range = 0f..1f,
                onValueChange = onExposureRatioChanged
            )
            ProSliderSection(
                title = stringResource(R.string.camera_white_balance_title),
                valueLabel = whiteBalanceLabel(context, whiteBalancePreset),
                enabled = true,
                value = whiteBalancePreset.ordinal.toFloat(),
                range = 0f..WhiteBalancePreset.entries.lastIndex.toFloat(),
                steps = WhiteBalancePreset.entries.size - 2,
                onValueChange = { sliderValue ->
                    onWhiteBalanceChanged(
                        WhiteBalancePreset.entries[
                            sliderValue.toInt().coerceIn(0, WhiteBalancePreset.entries.lastIndex)
                        ]
                    )
                }
            )
        }
    }
}

@Composable
private fun ProSliderSection(
    title: String,
    valueLabel: String,
    enabled: Boolean,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    steps: Int = 0
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = valueLabel,
                color = Color(0xFFFFD39A),
                style = MaterialTheme.typography.labelMedium
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            steps = steps
        )
    }
}

@Composable
private fun CaptureButton(
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .size(92.dp)
            .clip(CircleShape)
            .background(Color(0x3311161C))
            .clickable(enabled = !isCapturing, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CaptureRingGlyph(isCapturing = isCapturing)
    }
}

@Composable
private fun SettingPill(
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(50))
            .background(if (isActive) Color(0xFFFFB04C) else Color(0x9911161C))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        SettingsGlyph(isActive = isActive)
    }
}

@Composable
private fun FocusRing(
    x: Dp,
    y: Dp
) {
    Surface(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(56.dp),
        shape = CircleShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(2.dp, Color(0xFFFFB04C))
    ) {}
}

private fun ratioToExposureTimeNs(
    ratio: Float,
    minNs: Long,
    maxNs: Long
): Long {
    if (minNs >= maxNs) {
        return minNs
    }
    val clampedRatio = ratio.coerceIn(0f, 1f).toDouble()
    val minLog = ln(minNs.toDouble())
    val maxLog = ln(maxNs.toDouble())
    return exp(minLog + (maxLog - minLog) * clampedRatio).toLong()
}

private fun formatExposureTime(
    exposureTimeNs: Long
): String {
    val seconds = exposureTimeNs / 1_000_000_000.0
    return if (seconds >= 1.0) {
        String.format("%.1fs", seconds)
    } else {
        val reciprocal = (1.0 / seconds).toInt().coerceAtLeast(1)
        "1/$reciprocal s"
    }
}

private fun whiteBalanceLabel(
    context: Context,
    preset: WhiteBalancePreset
): String {
    return when (preset) {
        WhiteBalancePreset.AUTO -> context.getString(R.string.camera_white_balance_auto)
        WhiteBalancePreset.INCANDESCENT -> context.getString(
            R.string.camera_white_balance_incandescent
        )
        WhiteBalancePreset.FLUORESCENT -> context.getString(
            R.string.camera_white_balance_fluorescent
        )
        WhiteBalancePreset.DAYLIGHT -> context.getString(R.string.camera_white_balance_daylight)
        WhiteBalancePreset.CLOUDY -> context.getString(R.string.camera_white_balance_cloudy)
        WhiteBalancePreset.SHADE -> context.getString(R.string.camera_white_balance_shade)
    }
}
