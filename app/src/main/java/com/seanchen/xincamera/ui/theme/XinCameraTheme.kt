package com.seanchen.xincamera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val XinCameraDarkColors = darkColorScheme(
    primary = Color(0xFFFFB04C),
    onPrimary = Color(0xFF1B1100),
    secondary = Color(0xFF88D4FF),
    background = Color(0xFF05070A),
    onBackground = Color(0xFFF3F5F7),
    surface = Color(0xFF11161C),
    onSurface = Color(0xFFF3F5F7)
)

private val XinCameraLightColors = lightColorScheme(
    primary = Color(0xFFB86600),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF005D86),
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF101418),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101418)
)

@Composable
fun XinCameraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) XinCameraDarkColors else XinCameraLightColors,
        content = content
    )
}
