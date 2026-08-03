#include "raw/dng_inspector.h"

#include <array>
#include <fstream>

namespace xincamera::raw {
namespace {

// DNG文件建立在TIFF格式之上，因此DNG文件要符合TIFF的基本结构
constexpr std::uint16_t kTiffMagic = 42;    // 识别TIFF文件格式的魔数
// 这个是TIFF/DNG数据中的标签编号
constexpr std::uint16_t kDngVersionTag = 50706; //普通TIFF图片也拥有TIFF文件头，所以结合TIFF文件头 + DNG标签来判断是否属于DNG文件
// FNV-1a 文件指纹: 用于计算FNV-1a 64位哈希来判断文件内容是否发生改变
constexpr std::uint64_t kFnvOffsetBasis = 14695981039346656037ULL;
constexpr std::uint64_t kFnvPrime = 1099511628211ULL;

// 偏移 0～1：字节序 II 或 MM
// 偏移 2～3：魔数 42
// 偏移 4～7：第一个 IFD 的偏移量

// 小端序
// 从字节数组中读取一个16位无符号整数
std::uint16_t ReadU16(const std::uint8_t* bytes, bool little_endian) {
    if (little_endian) {
        return static_cast<std::uint16_t>(bytes[0]) |
               (static_cast<std::uint16_t>(bytes[1]) << 8U);
    }
    // 2A 00 -> 42
    return (static_cast<std::uint16_t>(bytes[0]) << 8U) |
           static_cast<std::uint16_t>(bytes[1]);
}

// 大端序
// 读取的是32位整数
std::uint32_t ReadU32(const std::uint8_t* bytes, bool little_endian) {
    if (little_endian) {
        return static_cast<std::uint32_t>(bytes[0]) |
               (static_cast<std::uint32_t>(bytes[1]) << 8U) |
               (static_cast<std::uint32_t>(bytes[2]) << 16U) |
               (static_cast<std::uint32_t>(bytes[3]) << 24U);
    }
    // 00 2A -> 42
    return (static_cast<std::uint32_t>(bytes[0]) << 24U) |
           (static_cast<std::uint32_t>(bytes[1]) << 16U) |
           (static_cast<std::uint32_t>(bytes[2]) << 8U) |
           static_cast<std::uint32_t>(bytes[3]);
}

// 遍历IFD，寻找DNGVersion标签
bool HasDngVersionTag(
        std::ifstream& input,
        std::uint64_t file_size,
        std::uint32_t ifd_offset,
        bool little_endian) {
    // 所以在这里判断文件是否损坏
    // 如果文件损坏，里面记录的IFD偏移可能大于实际文件长度
    if (ifd_offset > file_size || static_cast<std::uint64_t>(ifd_offset) + 2 > file_size) {
        return false;
    }

    input.clear();
    input.seekg(ifd_offset, std::ios::beg);
    std::array<std::uint8_t, 2> count_bytes{};
    if (!input.read(reinterpret_cast<char*>(count_bytes.data()), count_bytes.size())) return false;
    const std::uint16_t entry_count = ReadU16(count_bytes.data(), little_endian);
    const std::uint64_t entries_start = static_cast<std::uint64_t>(ifd_offset) + 2;
    if (entry_count > (file_size - entries_start) / 12) return false;

    std::array<std::uint8_t, 12> entry{};
    for (std::uint16_t index = 0; index < entry_count; ++index) {
        if (!input.read(reinterpret_cast<char*>(entry.data()), entry.size())) return false;
        if (ReadU16(entry.data(), little_endian) == kDngVersionTag) return true;
    }
    return false;
}

}  // namespace

DngSummary InspectDngFile(const std::string& path) {
    std::ifstream input(path, std::ios::binary | std::ios::ate);
    if (!input) return {};

    const std::streamsize file_size = input.tellg();
    if (file_size < 8) return {};
    input.seekg(0, std::ios::beg);
    std::array<std::uint8_t, 8> header{};
    if (!input.read(reinterpret_cast<char*>(header.data()), header.size())) return {};

    // DNG文件可能存在两个字节序
    const bool little_endian = header[0] == 'I' && header[1] == 'I';
    const bool big_endian = header[0] == 'M' && header[1] == 'M';
    const bool valid_tiff = (little_endian || big_endian) && ReadU16(header.data() + 2, little_endian) == kTiffMagic;   // 在这里判断是不是标准的TIFF/DNG文件
    const std::uint32_t ifd_offset = ReadU32(header.data() + 4, little_endian);
    const bool valid_dng = valid_tiff && HasDngVersionTag(
            input,
            static_cast<std::uint64_t>(file_size),
            ifd_offset,
            little_endian
    );

    input.clear();
    input.seekg(0, std::ios::beg);
    std::uint64_t fingerprint = kFnvOffsetBasis;
    std::array<char, 64 * 1024> buffer{};
    while (input) {
        input.read(buffer.data(), buffer.size());
        const std::streamsize bytes_read = input.gcount();
        for (std::streamsize index = 0; index < bytes_read; ++index) {
            fingerprint ^= static_cast<std::uint8_t>(buffer[static_cast<std::size_t>(index)]);
            fingerprint *= kFnvPrime;
        }
    }

    DngSummary summary;
    summary.is_valid = valid_dng;
    summary.size_bytes = static_cast<std::uint64_t>(file_size);
    summary.fingerprint = fingerprint;
    return summary;
}

}  // namespace xincamera::raw
