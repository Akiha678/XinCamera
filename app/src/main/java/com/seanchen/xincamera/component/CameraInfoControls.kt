package com.seanchen.xincamera.component

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier,
    onItemClick: (Int) -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0x6611161C))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            CameraInfoTile(
                item = item,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        enabled = !item.isMuted,
                        onClick = { onItemClick(index) }
                    )
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
        item.isActive -> Color(0xFFFFD39A)
        item.isMuted -> Color(0x88FFFFFF)
        else -> Color(0xFFEFF3F8)
    }
    val labelColor = if (item.isActive) Color(0xFFFFD39A) else Color(0xB3FFFFFF)
    Box(
        modifier = modifier
            .height(54.dp)
            .padding(horizontal = 2.dp)
            .background(
                color = if (item.isActive) Color(0x22FFD39A) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
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
    parameterLabel: String,
    valueLabel: String,
    ticks: List<CameraScaleTick>,
    currentRatio: Float,
    onRatioChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    actionLabel: String = "AUTO",
    actionActive: Boolean = false,
    onActionClick: () -> Unit = {}
) {
    val latestRatio by rememberUpdatedState(currentRatio)
    val latestOnRatioChanged by rememberUpdatedState(onRatioChanged)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .pointerInput(enabled, ticks.size) {
                if (enabled) {
                    var gestureRatio = latestRatio
                    val totalDragRange = (ticks.lastIndex.coerceAtLeast(1) * 34.dp.toPx())
                    detectHorizontalDragGestures(
                        onDragStart = { gestureRatio = latestRatio },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            gestureRatio = (gestureRatio - dragAmount / totalDragRange)
                                .coerceIn(0f, 1f)
                            latestOnRatioChanged(gestureRatio)
                        }
                    )
                }
            },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xF20A0F17)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 18.dp)
            ) {
                Text(
                    modifier = Modifier.align(Alignment.CenterStart),
                    text = parameterLabel,
                    color = Color(0x99FFFFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = valueLabel,
                    color = Color(0xFFFFD39A),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(width = 68.dp, height = 34.dp),
                    shape = RoundedCornerShape(17.dp),
                    color = when {
                        !enabled -> Color(0x774A5260)
                        actionActive -> Color(0xFF339AF0)
                        else -> Color(0xFF252D38)
                    },
                    onClick = onActionClick,
                    enabled = enabled
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = actionLabel,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
            ) {
                val centerX = size.width / 2f
                val baselineY = size.height * 0.65f
                val tickBottomY = baselineY
                val labelY = size.height * 0.28f
                val tickSpacing = 34.dp.toPx()
                val ratioSpan = ticks.lastIndex.coerceAtLeast(1)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 11.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT,
                        android.graphics.Typeface.NORMAL
                    )
                }
                drawLine(
                    color = Color(0x18FFFFFF),
                    start = Offset(16.dp.toPx(), baselineY),
                    end = Offset(size.width - 16.dp.toPx(), baselineY),
                    strokeWidth = 1.dp.toPx()
                )
                ticks.forEach { tick ->
                    val x = centerX +
                        (tick.ratio - currentRatio) * ratioSpan * tickSpacing
                    if (x in 0f..size.width) {
                        val isSelected = kotlin.math.abs(tick.ratio - currentRatio) <
                            0.45f / ratioSpan
                        val tickHeight = when {
                            isSelected -> 26.dp.toPx()
                            tick.isMajor -> 18.dp.toPx()
                            else -> 10.dp.toPx()
                        }
                        drawLine(
                            color = when {
                                isSelected -> Color(0xFFFFD39A)
                                tick.isMajor -> Color(0xA6FFFFFF)
                                else -> Color(0x4DFFFFFF)
                            },
                            start = Offset(x, tickBottomY - tickHeight),
                            end = Offset(x, tickBottomY),
                            strokeWidth = if (isSelected) 2.5.dp.toPx() else 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        if (tick.isMajor) {
                            textPaint.color = if (isSelected) {
                                android.graphics.Color.rgb(255, 211, 154)
                            } else {
                                android.graphics.Color.WHITE
                            }
                            textPaint.alpha = if (enabled) 210 else 80
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
                    color = Color(0xFFFFD39A),
                    start = Offset(centerX, baselineY - 31.dp.toPx()),
                    end = Offset(centerX, baselineY + 10.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val pointer = androidx.compose.ui.graphics.Path().apply {
                    moveTo(centerX, baselineY + 14.dp.toPx())
                    lineTo(centerX - 5.dp.toPx(), baselineY + 7.dp.toPx())
                    lineTo(centerX + 5.dp.toPx(), baselineY + 7.dp.toPx())
                    close()
                }
                drawPath(pointer, Color(0xFFFFD39A))
            }
        }
    }
}
