#pragma once

#include <cstdint>
#include <string>

namespace xincamera::raw {

struct DngSummary {
    bool is_valid = false;
    std::uint64_t size_bytes = 0;
    std::uint64_t fingerprint = 0;
};

/** 校验 TIFF/DNG 头与 DNGVersion 标签，并计算 FNV-1a 64 位文件指纹。 */
DngSummary InspectDngFile(const std::string& path);

}  // namespace xincamera::raw
