package com.seanchen.xincamera.camera

/**
 * 专业模式下的三项核心设置。
 *
 * `iso` 和 `exposureTimeNs` 为 null 时，表示继续使用自动曝光；
 * 白平衡使用兼容性更好的 AWB 预设模式。
 */
data class ProfessionalCameraSettings(
    val iso: Int? = null,
    val exposureTimeNs: Long? = null,
    val whiteBalancePreset: WhiteBalancePreset = WhiteBalancePreset.AUTO
)

/**
 * 当前镜头支持的专业参数范围。
 *
 * 不是所有镜头都支持手动 ISO / 快门，所以 UI 需要读取这里决定是否可调。
 */
data class ProfessionalCameraCapabilities(
    val supportsManualExposure: Boolean = false,
    val isoMin: Int = 100,
    val isoMax: Int = 100,
    val exposureTimeMinNs: Long = 1_000_000L,
    val exposureTimeMaxNs: Long = 1_000_000L
)

/**
 * 白平衡预设。
 *
 * 当前版本先走 Camera2 的 AWB 预设，稳定性更高；
 * 后续如果要继续做 Kelvin 级别调色，再把它替换成手动 gains 即可。
 */
enum class WhiteBalancePreset(
    val awbMode: Int
) {
    AUTO(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO),
    INCANDESCENT(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT),
    FLUORESCENT(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT),
    DAYLIGHT(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT),
    CLOUDY(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
    SHADE(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_SHADE)
}
