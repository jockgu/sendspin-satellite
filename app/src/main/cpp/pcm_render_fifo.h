#pragma once

#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>

namespace sendspin {

class PcmRenderFifo {
public:
    static constexpr uint32_t kSampleRate = 48'000;
    static constexpr uint32_t kChannels = 2;
    static constexpr uint32_t kBlockFrames = 480;
    static constexpr uint32_t kBlockCount = 200;

    struct PullResult {
        uint32_t frames_from_fifo;
        bool underrun;
    };

    [[nodiscard]] uint32_t write(const int16_t* samples, uint32_t frames);
    PullResult pull(int16_t* output, uint32_t frames);
    void clear();
    [[nodiscard]] uint32_t queued_frames() const;

private:
    struct Block {
        std::array<int16_t, kBlockFrames * kChannels> samples{};
        uint32_t frames{};
        uint32_t generation{};
    };

    std::array<Block, kBlockCount> blocks_{};
    std::atomic<uint32_t> write_block_{0};
    std::atomic<uint32_t> read_block_{0};
    std::atomic<uint32_t> generation_{1};
    std::atomic<uint32_t> consumer_offset_{0};
};

}  // namespace sendspin
