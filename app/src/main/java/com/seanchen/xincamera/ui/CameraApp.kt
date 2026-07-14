package com.seanchen.xincamera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.seanchen.xincamera.R
import com.seanchen.xincamera.camera.CameraPreviewController
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
                        statusMessage = context.getString(R.string.camera_focus_locked)
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
            onError = { error ->
                statusMessage = error
            }
        )
        onDispose {
            cameraController.unbind()
        }
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xAA000000), Color.Transparent)
                    )
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xD9000000))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusChip(label = stringResource(R.string.camera_preview_label))
                    StatusChip(label = nativeStatus)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusChip(label = "${String.format("%.1f", zoomRatio)}x")
                    StatusChip(
                        label = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            stringResource(R.string.camera_rear_lens)
                        } else {
                            stringResource(R.string.camera_front_lens)
                        }
                    )
                }
                Text(
                    text = statusMessage.ifBlank {
                        stringResource(R.string.camera_tap_focus_hint)
                    },
                    color = Color(0xFFE1E7EE),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = stringResource(R.string.camera_zoom_label),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = zoomRatio.coerceIn(minZoomRatio, maxZoomRatio),
                    onValueChange = { cameraController.setZoomRatio(it) },
                    valueRange = minZoomRatio..maxZoomRatio
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (torchAvailable) {
                                cameraController.setTorchEnabled(!torchEnabled)
                            } else {
                                statusMessage = context.getString(R.string.camera_flash_unavailable)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (torchEnabled) Color(0xFFFFB04C) else Color(0xFF26313A),
                            contentColor = if (torchEnabled) Color(0xFF1B1100) else Color.White
                        )
                    ) {
                        Text(
                            text = when {
                                !torchAvailable -> stringResource(R.string.camera_flash_unavailable)
                                torchEnabled -> stringResource(R.string.camera_flash_on)
                                else -> stringResource(R.string.camera_flash_off)
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                            statusMessage = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                context.getString(R.string.camera_rear_active)
                            } else {
                                context.getString(R.string.camera_front_active)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.camera_switch_lens))
                    }
                }
            }
        }

        focusPoint?.let { normalizedPoint ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = (maxWidth * normalizedPoint.x) - 28.dp,
                        y = (maxHeight * normalizedPoint.y) - 28.dp
                    )
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
            ) {
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.Center),
                    shape = CircleShape,
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFB04C))
                ) {}
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x9911161C))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
