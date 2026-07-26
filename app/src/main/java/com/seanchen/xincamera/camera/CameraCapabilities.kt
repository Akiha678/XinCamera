package com.seanchen.xincamera.camera

import com.seanchen.xincamera.domain.model.ProfessionalCameraCapabilities

/**
 * Camera 层对外暴露的能力模型别名。
 *
 * 真实数据结构放在 domain/model，camera 包只保留这个类型入口，避免 UI 依赖 Camera2 细节。
 */
typealias CameraCapabilities = ProfessionalCameraCapabilities
