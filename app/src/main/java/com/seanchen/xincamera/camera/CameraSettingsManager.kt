package com.seanchen.xincamera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import com.seanchen.xincamera.domain.model.ProfessionalCameraCapabilities
import com.seanchen.xincamera.domain.model.ProfessionalCameraSettings

/**
 * 专业参数管理器。
 *
 * Camera2Interop 属于底层相机细节，集中在这里可以让 Controller 保持轻量。
 */
class CameraSettingsManager {
    private var professionalSettings = ProfessionalCameraSettings()

    fun updateProfessionalSettings(
        camera: Camera?,
        settings: ProfessionalCameraSettings
    ) {
        professionalSettings = settings
        camera?.let(::applyProfessionalSettings)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun buildProfessionalCapabilities(camera: Camera): ProfessionalCameraCapabilities {
        val cameraInfo = Camera2CameraInfo.from(camera.cameraInfo)
        val sensitivityRange = cameraInfo.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
        )
        val exposureTimeRange = cameraInfo.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
        )
        val capabilities = cameraInfo.getCameraCharacteristic(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
        ) ?: intArrayOf()
        val supportsManualExposure = capabilities.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        ) && sensitivityRange != null && exposureTimeRange != null

        return ProfessionalCameraCapabilities(
            supportsManualExposure = supportsManualExposure,
            isoMin = sensitivityRange?.lower ?: 100,
            isoMax = sensitivityRange?.upper ?: 100,
            exposureTimeMinNs = exposureTimeRange?.lower ?: 1_000_000L,
            exposureTimeMaxNs = exposureTimeRange?.upper ?: 1_000_000L
        )
    }

    fun applyCurrentSettings(camera: Camera) {
        applyProfessionalSettings(camera)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyProfessionalSettings(camera: Camera) {
        val camera2Control = Camera2CameraControl.from(camera.cameraControl)
        val iso = professionalSettings.iso
        val exposureTimeNs = professionalSettings.exposureTimeNs
        val manualExposureEnabled = iso != null && exposureTimeNs != null

        val requestOptions = CaptureRequestOptions.Builder().apply {
            if (manualExposureEnabled) {
                setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )
                setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    iso
                )
                setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    exposureTimeNs
                )
            } else {
                setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
            }

            setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                professionalSettings.whiteBalancePreset.awbMode
            )
        }.build()

        camera2Control.setCaptureRequestOptions(requestOptions)
    }
}
