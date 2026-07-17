package com.seanchen.xincamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.seanchen.xincamera.nativebridge.NativeBridge
import com.seanchen.xincamera.ui.screen.CameraApp
import com.seanchen.xincamera.ui.theme.XinCameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersiveCameraMode()
        setContent {
            val nativeStatus = runCatching {
                NativeBridge.nativePreviewPipelineName()
            }.getOrDefault("JNI pending")

            XinCameraTheme {
                Surface {
                    CameraApp(nativeStatus = nativeStatus)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveCameraMode()
    }

    private fun enterImmersiveCameraMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }
}
