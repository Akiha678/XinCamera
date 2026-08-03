package com.seanchen.xincamera.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * 相机界面通用图标按钮容器。
 *
 * designsystem 只封装交互容器和视觉规则，具体图标由业务层传入，避免设计系统依赖相机业务。
 */
@Composable
fun CameraIconButton(
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false,
    shape: Shape = CircleShape,
    activeBackgroundColor: Color = Color(0xFFFFB04C),
    inactiveBackgroundColor: Color = Color(0x9911161C),
    disabledBackgroundColor: Color = Color(0x4411161C),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                when {
                    !enabled -> disabledBackgroundColor
                    isActive -> activeBackgroundColor
                    else -> inactiveBackgroundColor
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
