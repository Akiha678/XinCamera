#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

namespace xincamera::histogram {

constexpr std::size_t kHistogramBucketCount = 256;

/**
 * 计算 Y 平面的亮度直方图。
 *
 * 该模块不依赖 JNI，便于后续接入 native 单测或复用到 OpenGL/RenderScript 替代管线。
 */
std::array<int32_t, kHistogramBucketCount> ComputeLumaHistogram(
        const uint8_t* yPlane,
        int32_t width,
        int32_t height,
        int32_t rowStride,
        int32_t pixelStride,
        std::size_t planeLength);

} // namespace xincamera::histogram
