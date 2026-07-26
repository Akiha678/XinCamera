package com.seanchen.xincamera.presentation

/**
 * 相机页面状态入口。
 *
 * 当前页面状态仍以 Compose `rememberSaveable` 为主；保留 ViewModel 层是为了后续把拍摄模式、
 * 专业参数、滤镜开关等 UI 状态逐步下沉，避免 CameraScreen 继续膨胀。
 */
class CameraViewModel {
    val defaultMode: CameraMode = CameraMode.Photo
}

enum class CameraMode {
    Photo,
    Video
}
