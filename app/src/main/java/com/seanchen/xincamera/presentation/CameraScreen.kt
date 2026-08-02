package com.seanchen.xincamera.presentation

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.seanchen.xincamera.R
import com.seanchen.xincamera.camera.CameraController
import com.seanchen.xincamera.core.designsystem.component.CameraIconButton
import com.seanchen.xincamera.core.designsystem.component.CameraHorizontalScale
import com.seanchen.xincamera.core.designsystem.component.CameraInfoBar
import com.seanchen.xincamera.core.designsystem.component.CameraInfoItem
import com.seanchen.xincamera.core.designsystem.component.CameraScaleTick
import com.seanchen.xincamera.domain.model.ProfessionalCameraCapabilities
import com.seanchen.xincamera.domain.model.ProfessionalCameraSettings
import com.seanchen.xincamera.domain.model.WhiteBalancePreset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CameraApp(
    nativeStatus: String
) {
    val context = LocalContext.current
    var destination by rememberSaveable { mutableStateOf(CameraAppDestination.Camera) }
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
        when (destination) {
            CameraAppDestination.Camera -> CameraScreen(
                nativeStatus = nativeStatus,
                onOpenSettings = { destination = CameraAppDestination.Settings }
            )

            CameraAppDestination.Settings -> SettingRoute(
                onBackClick = { destination = CameraAppDestination.Camera }
            )
        }
    }
}

private enum class CameraAppDestination {
    Camera,
    Settings
}

private enum class ExposureControl {
    ISO,
    SHUTTER
}

/**
 * 主相机页由 CameraX 预览和 Compose 覆盖层组成。
 *
 * 覆盖层固定放置顶部 Setting 入口、底部拍摄按钮、变焦和第一部分控制按钮。
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun CameraScreen(
    nativeStatus: String,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember(context) {
        CameraController(context.applicationContext)
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
    var rawCaptureEnabled by rememberSaveable { mutableStateOf(false) }
    var rawCaptureAvailable by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf("") }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var selectedExposureControl by rememberSaveable {
        mutableStateOf<ExposureControl?>(null)
    }
    var isCapturing by rememberSaveable { mutableStateOf(false) }
    var isProcessingGrayscale by rememberSaveable { mutableStateOf(false) }
    var lastCapturedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var manualExposureEnabled by rememberSaveable { mutableStateOf(false) }
    var professionalCapabilities by remember {
        mutableStateOf(ProfessionalCameraCapabilities())
    }
    var selectedIso by rememberSaveable { mutableIntStateOf(100) }
    var selectedExposureTimeNs by rememberSaveable { mutableStateOf(16_666_667L) }
    var histogramBins by remember { mutableStateOf(IntArray(256)) }
    val focusScope = rememberCoroutineScope()

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

    DisposableEffect(lifecycleOwner, lensFacing, rawCaptureEnabled) {
        cameraController.bindToLifecycle(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            lensFacing = lensFacing,
            rawCaptureRequested = rawCaptureEnabled,
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
                selectedExposureTimeNs = selectedExposureTimeNs.coerceIn(
                    capabilities.exposureTimeMinNs,
                    capabilities.exposureTimeMaxNs
                )
                if (!capabilities.supportsManualExposure) {
                    manualExposureEnabled = false
                    selectedExposureControl = null
                }
            },
            onRawAvailabilityChanged = { available ->
                rawCaptureAvailable = available
                if (!available) rawCaptureEnabled = false
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
        selectedExposureTimeNs,
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
                    selectedExposureTimeNs.coerceIn(
                        professionalCapabilities.exposureTimeMinNs,
                        professionalCapabilities.exposureTimeMaxNs
                    )
                } else {
                    null
                },
                whiteBalancePreset = WhiteBalancePreset.AUTO
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

        // 亮度直方图
        HistogramOverlay(
            histogram = histogramBins,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(top = 142.dp, end = 16.dp)
        )

        CameraHud(
            modifier = Modifier.align(Alignment.TopCenter),
            torchEnabled = torchEnabled,
            torchAvailable = torchAvailable,
            rawCaptureEnabled = rawCaptureEnabled,
            rawCaptureAvailable = rawCaptureAvailable,
            showSettingsPanel = false,
            onToggleSettings = onOpenSettings,
            onToggleTorch = {
                if (torchAvailable) {
                    cameraController.setTorchEnabled(!torchEnabled)
                } else {
                    statusMessage = "无闪光灯"
                }
            },
            onToggleRaw = {
                if (rawCaptureAvailable) {
                    rawCaptureEnabled = !rawCaptureEnabled
                    statusMessage = if (rawCaptureEnabled) {
                        "RAW 已开启"
                    } else {
                        "JPEG"
                    }
                } else {
                    statusMessage = "当前镜头不支持 RAW"
                }
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

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.navigationBars.asPaddingValues())
        ) {
            ProfessionalExposureControls(
                capabilities = professionalCapabilities,
                manualExposureEnabled = manualExposureEnabled,
                selectedIso = selectedIso,
                selectedExposureTimeNs = selectedExposureTimeNs,
                selectedControl = selectedExposureControl,
                onControlSelected = { control ->
                    if (professionalCapabilities.supportsManualExposure) {
                        selectedExposureControl = if (selectedExposureControl == control) {
                            null
                        } else {
                            control
                        }
                    } else {
                        statusMessage = "当前镜头不支持手动 ISO / 快门控制"
                    }
                },
                onAutoSelected = {
                    manualExposureEnabled = false
                    statusMessage = "自动曝光"
                },
                onIsoChanged = { iso ->
                    if (professionalCapabilities.supportsManualExposure) {
                        manualExposureEnabled = true
                        selectedIso = iso
                        statusMessage = "手动曝光"
                    }
                },
                onExposureTimeChanged = { exposureTimeNs ->
                    if (professionalCapabilities.supportsManualExposure) {
                        manualExposureEnabled = true
                        selectedExposureTimeNs = exposureTimeNs
                        statusMessage = "手动曝光"
                    }
                },
                isCapturing = isCapturing,
                onCapture = {
                    if (!isCapturing) {
                        isCapturing = true
                        statusMessage = "正在拍摄"
                        cameraController.capturePhoto(
                            onSaved = { result ->
                                isCapturing = false
                                lastCapturedUri = result.jpegUri ?: result.rawUri
                                statusMessage = if (result.rawUri != null) {
                                    val sizeMb = (result.rawSizeBytes ?: 0L) /
                                        (1024.0 * 1024.0)
                                    val outputLabel = if (result.jpegUri != null) {
                                        "RAW + JPEG"
                                    } else {
                                        "RAW"
                                    }
                                    String.format(
                                        Locale.US,
                                        "$outputLabel 已保存 · %.1f MB",
                                        sizeMb
                                    )
                                } else {
                                    ""
                                }
                            },
                            onError = { error ->
                                isCapturing = false
                                statusMessage = error
                            }
                        )
                    }
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

/**
 * 亮度直方图
 */
@Composable
private fun HistogramOverlay(
    histogram: IntArray,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(width = 180.dp, height = 104.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0x6611161C),
        border = BorderStroke(1.dp, Color(0x24FFFFFF))
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
/**
 * 底部控制栏
 */
@Composable
private fun CameraBottomControls(
    isCapturing: Boolean,
    onCapture: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 34.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CameraModeLabel(text = "PHOTO", isActive = true)
        CaptureButton(
            isCapturing = isCapturing,
            onClick = onCapture
        )
        CameraModeLabel(text = "VIDEO", isActive = false)
    }
}

@Composable
private fun CameraModeLabel(
    text: String,
    isActive: Boolean
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isActive) Color(0x22FFFFFF) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = if (isActive) Color.White else Color(0x88FFFFFF),
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1
    )
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
private fun SwitchCameraGlyph() {
    Canvas(modifier = Modifier.size(26.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = Color.White,
            startAngle = 35f,
            sweepAngle = 230f,
            useCenter = false,
            topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.64f),
            style = stroke
        )
        drawArc(
            color = Color.White,
            startAngle = 215f,
            sweepAngle = 230f,
            useCenter = false,
            topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.64f),
            style = stroke
        )
        drawCircle(
            color = Color.White,
            radius = 2.8.dp.toPx(),
            center = Offset(size.width * 0.78f, size.height * 0.34f)
        )
        drawCircle(
            color = Color.White,
            radius = 2.8.dp.toPx(),
            center = Offset(size.width * 0.22f, size.height * 0.66f)
        )
    }
}

/**
 * 顶部Bar
 */
@Composable
private fun CameraHud(
    modifier: Modifier = Modifier,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    rawCaptureEnabled: Boolean,
    rawCaptureAvailable: Boolean,
    showSettingsPanel: Boolean,
    onToggleSettings: () -> Unit,
    onToggleTorch: () -> Unit,
    onToggleRaw: () -> Unit,
    onSwitchLens: () -> Unit
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 设置按钮
            SettingPill(
                isActive = showSettingsPanel,
                onClick = onToggleSettings
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RawTopButton(
                    isActive = rawCaptureEnabled,
                    isEnabled = rawCaptureAvailable,
                    onClick = onToggleRaw
                )
                // 闪光灯按钮
                FlashTopButton(
                    isActive = torchEnabled,
                    isEnabled = torchAvailable,
                    onClick = onToggleTorch
                )
                // 切换摄像头
                SwitchLensButton(onClick = onSwitchLens)
            }
        }
    }
}

@Composable
private fun RawTopButton(
    isActive: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    CameraIconButton(
        size = 42.dp,
        enabled = isEnabled,
        isActive = isActive,
        shape = RoundedCornerShape(12.dp),
        activeBackgroundColor = Color(0xFFFFD39A),
        inactiveBackgroundColor = Color(0x6611161C),
        disabledBackgroundColor = Color(0x3311161C),
        onClick = onClick
    ) {
        Text(
            text = "RAW",
            color = when {
                !isEnabled -> Color(0x55FFFFFF)
                isActive -> Color(0xFF11161C)
                else -> Color.White
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

// 闪光灯按钮
@Composable
private fun FlashTopButton(
    isActive: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    CameraIconButton(
        size = 42.dp,
        enabled = isEnabled,
        isActive = isActive,
        inactiveBackgroundColor = Color.Transparent,
        disabledBackgroundColor = Color.Transparent,
        onClick = onClick
    ) {
        FlashGlyph(isActive = isActive, isEnabled = isEnabled)
    }
}

// 设置按钮
@Composable
private fun SettingPill(
    isActive: Boolean,
    onClick: () -> Unit
) {
    CameraIconButton(
        size = 52.dp,
        isActive = isActive,
        shape = RoundedCornerShape(50),
        activeBackgroundColor = Color(0xFFFFD39A),
        inactiveBackgroundColor = Color(0x6611161C),
        onClick = onClick
    ) {
        SettingsGlyph(isActive = isActive)
    }
}

@Composable
private fun SwitchLensButton(
    onClick: () -> Unit
) {
    CameraIconButton(
        size = 42.dp,
        inactiveBackgroundColor = Color(0x6611161C),
        onClick = onClick
    ) {
        SwitchCameraGlyph()
    }
}

// 拍摄按钮
@Composable
private fun CaptureButton(
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    CameraIconButton(
        modifier = Modifier.padding(horizontal = 14.dp),
        size = 88.dp,
        enabled = !isCapturing,
        inactiveBackgroundColor = Color(0x33FFFFFF),
        disabledBackgroundColor = Color(0x33FFFFFF),
        onClick = onClick
    ) {
        CaptureRingGlyph(isCapturing = isCapturing)
    }
}

@Composable
private fun ProfessionalExposureControls(
    capabilities: ProfessionalCameraCapabilities,
    manualExposureEnabled: Boolean,
    selectedIso: Int,
    selectedExposureTimeNs: Long,
    selectedControl: ExposureControl?,
    onControlSelected: (ExposureControl) -> Unit,
    onAutoSelected: () -> Unit,
    onIsoChanged: (Int) -> Unit,
    onExposureTimeChanged: (Long) -> Unit,
    isCapturing: Boolean,
    onCapture: () -> Unit
) {
    val isoValues = remember(capabilities) { buildIsoValues(capabilities) }
    val exposureValues = remember(capabilities) { buildExposureTimeValues(capabilities) }
    val isoIndex = isoValues.indexOfNearestIso(selectedIso)
    val exposureIndex = exposureValues.indexOfNearestExposure(selectedExposureTimeNs)

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = selectedControl != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            when (selectedControl) {
                ExposureControl.ISO -> CameraHorizontalScale(
                    parameterLabel = "ISO 感光度",
                    valueLabel = selectedIso.toString(),
                    ticks = isoValues.mapIndexed { index, value ->
                        CameraScaleTick(
                            ratio = index.toScaleRatio(isoValues.size),
                            label = value.toString(),
                            isMajor = value == capabilities.isoMin ||
                                value == capabilities.isoMax ||
                                value in setOf(100, 200, 400, 800, 1600, 3200, 6400)
                        )
                    },
                    currentRatio = isoIndex.toScaleRatio(isoValues.size),
                    onRatioChanged = { ratio ->
                        onIsoChanged(isoValues[ratio.toScaleIndex(isoValues.size)])
                    },
                    enabled = capabilities.supportsManualExposure,
                    actionActive = !manualExposureEnabled,
                    onActionClick = onAutoSelected
                )

                ExposureControl.SHUTTER -> CameraHorizontalScale(
                    parameterLabel = "快门速度",
                    valueLabel = formatExposureTime(selectedExposureTimeNs),
                    ticks = exposureValues.mapIndexed { index, value ->
                        CameraScaleTick(
                            ratio = index.toScaleRatio(exposureValues.size),
                            label = formatExposureTime(value),
                            isMajor = index % 2 == 0 ||
                                value == exposureValues.first() ||
                                value == exposureValues.last()
                        )
                    },
                    currentRatio = exposureIndex.toScaleRatio(exposureValues.size),
                    onRatioChanged = { ratio ->
                        onExposureTimeChanged(
                            exposureValues[ratio.toScaleIndex(exposureValues.size)]
                        )
                    },
                    enabled = capabilities.supportsManualExposure,
                    actionActive = !manualExposureEnabled,
                    onActionClick = onAutoSelected
                )

                null -> Unit
            }
        }

        CameraInfoBar(
            items = listOf(
                CameraInfoItem(
                    label = "ISO",
                    value = if (manualExposureEnabled) selectedIso.toString() else "AUTO",
                    autoLabel = if (manualExposureEnabled) null else "A",
                    isActive = selectedControl == ExposureControl.ISO,
                    isMuted = !capabilities.supportsManualExposure
                ),
                CameraInfoItem(
                    label = "S",
                    value = if (manualExposureEnabled) {
                        formatExposureTime(selectedExposureTimeNs)
                    } else {
                        "AUTO"
                    },
                    autoLabel = if (manualExposureEnabled) null else "A",
                    isActive = selectedControl == ExposureControl.SHUTTER,
                    isMuted = !capabilities.supportsManualExposure
                )
            ),
            modifier = Modifier.padding(horizontal = 16.dp),
            onItemClick = { index ->
                onControlSelected(
                    if (index == 0) ExposureControl.ISO else ExposureControl.SHUTTER
                )
            }
        )

        CameraBottomControls(
            isCapturing = isCapturing,
            onCapture = onCapture
        )
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

private fun buildIsoValues(capabilities: ProfessionalCameraCapabilities): List<Int> {
    val standardValues = listOf(
        25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400,
        500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000,
        5000, 6400, 8000, 10000, 12800, 16000, 25600, 51200, 102400
    )
    return (standardValues + capabilities.isoMin + capabilities.isoMax)
        .filter { it in capabilities.isoMin..capabilities.isoMax }
        .distinct()
        .sorted()
        .ifEmpty { listOf(capabilities.isoMin) }
}

private fun buildExposureTimeValues(
    capabilities: ProfessionalCameraCapabilities
): List<Long> {
    val reciprocalValues = listOf(
        16000, 12000, 8000, 6400, 4000, 3200, 2000, 1600, 1000, 800,
        640, 500, 400, 320, 250, 200, 160, 125, 100, 80, 60, 50, 40, 30,
        25, 20, 15, 13, 10, 8, 6, 5, 4, 3, 2
    ).map { denominator -> 1_000_000_000L / denominator }
    val wholeSecondValues = listOf(1L, 2L, 4L, 8L, 15L, 30L)
        .map { seconds -> seconds * 1_000_000_000L }

    return (reciprocalValues + wholeSecondValues +
        capabilities.exposureTimeMinNs + capabilities.exposureTimeMaxNs)
        .filter {
            it in capabilities.exposureTimeMinNs..capabilities.exposureTimeMaxNs
        }
        .distinct()
        .sorted()
        .ifEmpty { listOf(capabilities.exposureTimeMinNs) }
}

private fun List<Int>.indexOfNearestIso(value: Int): Int =
    indices.minByOrNull { index -> abs(this[index].toLong() - value.toLong()) } ?: 0

private fun List<Long>.indexOfNearestExposure(value: Long): Int =
    indices.minByOrNull { index -> abs(this[index] - value) } ?: 0

private fun Int.toScaleRatio(itemCount: Int): Float =
    if (itemCount <= 1) 0f else toFloat() / (itemCount - 1).toFloat()

private fun Float.toScaleIndex(itemCount: Int): Int =
    if (itemCount <= 1) 0 else (coerceIn(0f, 1f) * (itemCount - 1)).roundToInt()

@SuppressLint("DefaultLocale")
private fun formatExposureTime(
    exposureTimeNs: Long
): String {
    val seconds = exposureTimeNs / 1_000_000_000.0
    return if (seconds >= 1.0) {
        if (seconds % 1.0 < 0.05) {
            "${seconds.toInt()}s"
        } else {
            String.format("%.1fs", seconds)
        }
    } else {
        val reciprocal = (1.0 / seconds).roundToInt().coerceAtLeast(1)
        "1/$reciprocal"
    }
}
