#include "histogram/luma_histogram.h"

namespace xincamera::histogram {

std::array<int32_t, kHistogramBucketCount> ComputeLumaHistogram(
        const uint8_t* yPlane,
        int32_t width,
        int32_t height,
        int32_t rowStride,
        int32_t pixelStride,
        std::size_t planeLength) {
    std::array<int32_t, kHistogramBucketCount> histogram{};
    histogram.fill(0);

    if (yPlane == nullptr || width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) {
        return histogram;
    }

    for (int32_t row = 0; row < height; ++row) {
        const std::size_t rowStart = static_cast<std::size_t>(row) *
                static_cast<std::size_t>(rowStride);
        if (rowStart >= planeLength) {
            break;
        }

        for (int32_t col = 0; col < width; ++col) {
            const std::size_t index = rowStart +
                    static_cast<std::size_t>(col) * static_cast<std::size_t>(pixelStride);
            if (index >= planeLength) {
                break;
            }

            histogram[yPlane[index]] += 1;
        }
    }

    return histogram;
}

} // namespace xincamera::histogram
