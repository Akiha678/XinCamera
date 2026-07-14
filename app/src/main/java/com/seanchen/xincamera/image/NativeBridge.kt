package com.seanchen.xincamera.image

object NativeBridge {
    init {
        System.loadLibrary("xincamera")
    }

    external fun nativePreviewPipelineName(): String
}
