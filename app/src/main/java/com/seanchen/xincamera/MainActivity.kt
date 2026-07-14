package com.seanchen.xincamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.seanchen.xincamera.image.NativeBridge
import com.seanchen.xincamera.ui.CameraApp
import com.seanchen.xincamera.ui.theme.XinCameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
}
