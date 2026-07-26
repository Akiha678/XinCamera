#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace xincamera::filter {

/**
 * 将 Android ARGB_8888 像素转换为灰度像素。
 *
 * 输入和输出都使用 0xAARRGGBB 排列；alpha 原样保留，RGB 写入同一个灰度值。
 */
std::vector<int32_t> ApplyGrayscaleArgb8888(
        const int32_t* pixels,
        std::size_t pixelCount);

} // namespace xincamera::filter
