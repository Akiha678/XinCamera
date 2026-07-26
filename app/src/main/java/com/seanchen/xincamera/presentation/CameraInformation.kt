package com.seanchen.xincamera.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.seanchen.xincamera.core.designsystem.component.CameraHorizontalScale
import com.seanchen.xincamera.core.designsystem.component.CameraInfoBar
import com.seanchen.xincamera.core.designsystem.component.CameraInfoItem
import com.seanchen.xincamera.core.designsystem.component.CameraScaleTick
import com.seanchen.xincamera.domain.model.ProfessionalCameraCapabilities
import kotlin.math.ln

/**
 * 主界面相机信息条。
 *
 * 第一张参考图里有帧率，但当前需求不展示帧率，所以这里只保留：
 * 镜头、快门、光圈、ISO、白平衡、色调。
 */
@Composable
fun CameraInformation(
    modifier: Modifier = Modifier,
    lensLabel: String,
    shutterLabel: String,
    apertureLabel: String,
    isoLabel: String,
    whiteBalanceLabel: String,
    tintLabel: String,
    activeType: CameraInfoType
) {
    CameraInfoBar(
        modifier = modifier,
        items = listOf(
            CameraInfoItem(
                label = "镜头",
                value = lensLabel,
                isMuted = activeType != CameraInfoType.Lens
            ),
            CameraInfoItem(
                label = "快门",
                value = shutterLabel,
                autoLabel = "A",
                isActive = activeType == CameraInfoType.Shutter
            ),
            CameraInfoItem(
                label = "光圈",
                value = apertureLabel,
                isMuted = activeType != CameraInfoType.Aperture
            ),
            CameraInfoItem(
                label = "ISO",
                value = isoLabel,
                autoLabel = "A",
                isActive = activeType == CameraInfoType.Iso
            ),
            CameraInfoItem(
                label = "白平衡",
                value = whiteBalanceLabel,
                autoLabel = "A",
                isActive = activeType == CameraInfoType.WhiteBalance
            ),
            CameraInfoItem(
                label = "色调",
                value = tintLabel,
                isMuted = activeType != CameraInfoType.Tint
            )
        )
    )
}

@Composable
fun CameraShutterScale(
    modifier: Modifier = Modifier,
    capabilities: ProfessionalCameraCapabilities,
    exposureRatio: Float,
    valueLabel: String,
    enabled: Boolean,
    onExposureRatioChanged: (Float) -> Unit
) {
    val ticks = remember(capabilities) {
        buildShutterScaleTicks(
            minNs = capabilities.exposureTimeMinNs,
            maxNs = capabilities.exposureTimeMaxNs
        )
    }
    CameraHorizontalScale(
        modifier = modifier,
        valueLabel = valueLabel,
        ticks = ticks,
        currentRatio = exposureRatio,
        enabled = enabled,
        onRatioChanged = onExposureRatioChanged
    )
}

enum class CameraInfoType {
    Lens,
    Shutter,
    Aperture,
    Iso,
    WhiteBalance,
    Tint
}

private fun buildShutterScaleTicks(
    minNs: Long,
    maxNs: Long
): List<CameraScaleTick> {
    if (minNs <= 0L || maxNs <= 0L || minNs >= maxNs) {
        return emptyList()
    }
    return SHUTTER_DENOMINATORS.mapNotNull { denominator ->
        val exposureNs = (1_000_000_000.0 / denominator).toLong()
        if (exposureNs !in minNs..maxNs) {
            null
        } else {
            CameraScaleTick(
                ratio = exposureTimeNsToRatio(
                    exposureTimeNs = exposureNs,
                    minNs = minNs,
                    maxNs = maxNs
                ),
                label = "1/$denominator",
                isMajor = denominator in SHUTTER_MAJOR_DENOMINATORS
            )
        }
    }
}

private fun exposureTimeNsToRatio(
    exposureTimeNs: Long,
    minNs: Long,
    maxNs: Long
): Float {
    if (minNs >= maxNs) {
        return 0f
    }
    val minLog = ln(minNs.toDouble())
    val maxLog = ln(maxNs.toDouble())
    return ((ln(exposureTimeNs.toDouble()) - minLog) / (maxLog - minLog))
        .toFloat()
        .coerceIn(0f, 1f)
}

private val SHUTTER_DENOMINATORS = listOf(
    4000,
    2000,
    1000,
    500,
    250,
    120,
    100,
    96,
    60,
    50,
    48,
    33,
    30,
    25,
    15,
    8,
    4,
    2,
    1
)

private val SHUTTER_MAJOR_DENOMINATORS = setOf(120, 100, 96, 60, 50, 48, 33, 30, 25)
