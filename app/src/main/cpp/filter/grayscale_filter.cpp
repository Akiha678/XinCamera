#include "filter/grayscale_filter.h"

namespace xincamera::filter {

std::vector<int32_t> ApplyGrayscaleArgb8888(
        const int32_t* pixels,
        std::size_t pixelCount) {
    std::vector<int32_t> grayPixels(pixelCount);
    if (pixels == nullptr || pixelCount == 0) {
        return grayPixels;
    }

    for (std::size_t index = 0; index < pixelCount; ++index) {
        // Android Bitmap.Config.ARGB_8888 在 IntArray 中按 0xAARRGGBB 存储。
        // 转成 uint32_t 后再位移，避免有符号 int32_t 右移时出现符号扩展。
        const auto argb = static_cast<uint32_t>(pixels[index]);
        const uint32_t alpha = argb & 0xFF000000u;
        const uint32_t red = (argb >> 16u) & 0xFFu;
        const uint32_t green = (argb >> 8u) & 0xFFu;
        const uint32_t blue = argb & 0xFFu;

        // Rec.601 亮度近似公式：gray = 0.299R + 0.587G + 0.114B。
        // 77、150、29 的总和为 256，所以右移 8 位即可完成归一化，比浮点计算更适合像素循环。
        const uint32_t gray = (77u * red + 150u * green + 29u * blue) >> 8u;

        // 保留原 alpha，把灰度值同时写回 R/G/B 三个通道。
        grayPixels[index] = static_cast<int32_t>(
                alpha | (gray << 16u) | (gray << 8u) | gray
        );
    }

    return grayPixels;
}

} // namespace xincamera::filter
