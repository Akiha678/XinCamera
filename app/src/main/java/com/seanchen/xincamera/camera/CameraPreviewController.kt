package com.seanchen.xincamera.camera

/**
 * 旧类名兼容层。
 *
 * 新架构中统一使用 `CameraController` 作为相机模块入口；保留 typealias 可以降低迁移成本。
 */
typealias CameraPreviewController = CameraController
