package com.seanchen.xincamera.core.designsystem.component

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CameraInfoItem(
    val label: String,
    val value: String,
    val autoLabel: String? = null,
    val isActive: Boolean = false,
    val isMuted: Boolean = false
)

data class CameraScaleTick(
    val ratio: Float,
    val label: String,
    val isMajor: Boolean = true
)

@Composable
fun CameraInfoBar(
    items: List<CameraInfoItem>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF20A0F17))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            CameraInfoTile(
                item = item,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CameraInfoTile(
    item: CameraInfoItem,
    modifier: Modifier = Modifier
) {
    val foreground = when {
        item.isActive -> Color.White
        item.isMuted -> Color(0x88FFFFFF)
        else -> Color(0xFFEFF3F8)
    }
    val labelColor = if (item.isActive) Color.White else Color(0xCCFFFFFF)
    Box(
        modifier = modifier
            .height(58.dp)
            .padding(horizontal = 3.dp)
            .background(
                color = if (item.isActive) Color(0xFF339AF0) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 7.dp, vertical = 6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.label,
                color = labelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            item.autoLabel?.let { autoLabel ->
                Text(
                    modifier = Modifier
                        .background(Color(0xFF4DB2FF), RoundedCornerShape(2.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                    text = autoLabel,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
        Text(
            modifier = Modifier.align(Alignment.BottomStart),
            text = item.value,
            color = foreground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun CameraHorizontalScale(
    valueLabel: String,
    ticks: List<CameraScaleTick>,
    currentRatio: Float,
    onRatioChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dragRangePx: Float = 320f
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(102.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        onRatioChanged((currentRatio + dragAmount / dragRangePx).coerceIn(0f, 1f))
                    }
                }
            },
        shape = RoundedCornerShape(0.dp),
        color = Color(0xE80A0F17)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val baselineY = size.height * 0.70f
                val tickTopY = size.height * 0.48f
                val tickBottomY = size.height * 0.70f
                val labelY = size.height * 0.34f
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 13.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    alpha = if (enabled) 200 else 90
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT,
                        android.graphics.Typeface.BOLD
                    )
                }
                drawLine(
                    color = Color(0x22FFFFFF),
                    start = Offset(0f, baselineY),
                    end = Offset(size.width, baselineY),
                    strokeWidth = 1.dp.toPx()
                )
                ticks.forEach { tick ->
                    val x = centerX + (tick.ratio - currentRatio) * size.width * 0.95f
                    if (x in 0f..size.width) {
                        drawLine(
                            color = if (tick.isMajor) Color(0xBBFFFFFF) else Color(0x55FFFFFF),
                            start = Offset(x, if (tick.isMajor) tickTopY else tickTopY + 9.dp.toPx()),
                            end = Offset(x, tickBottomY),
                            strokeWidth = if (tick.isMajor) 2.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        if (tick.isMajor) {
                            drawContext.canvas.nativeCanvas.drawText(
                                tick.label,
                                x,
                                labelY,
                                textPaint
                            )
                        }
                    }
                }
                drawLine(
                    color = Color(0xFF36A3FF),
                    start = Offset(centerX, size.height * 0.44f),
                    end = Offset(centerX, size.height * 0.82f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val pointer = androidx.compose.ui.graphics.Path().apply {
                    moveTo(centerX, size.height * 0.82f)
                    lineTo(centerX - 5.dp.toPx(), size.height * 0.90f)
                    lineTo(centerX + 5.dp.toPx(), size.height * 0.90f)
                    close()
                }
                drawPath(pointer, Color(0xFF36A3FF))
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(88.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color(0xEE111722),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = valueLabel,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(width = 66.dp, height = 44.dp),
                shape = RoundedCornerShape(9.dp),
                color = if (enabled) Color(0xFF339AF0) else Color(0x774A5260)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = valueLabel,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
