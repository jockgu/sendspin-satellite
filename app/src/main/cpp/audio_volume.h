#pragma once

#include <atomic>
#include <cstddef>
#include <cstdint>

namespace sendspin {

class PcmVolume {
public:
    static constexpr uint32_t kUnityGainQ16 = 1U << 16;
    static constexpr uint32_t kRampFrames = 480;  // 10 ms at 48 kHz

    void set_state(uint8_t volume, bool muted) noexcept;
    void process(int16_t* samples, uint32_t frames, uint32_t channels) noexcept;

    [[nodiscard]] static uint32_t gain_for_volume(uint8_t volume) noexcept;

private:
    std::atomic<uint32_t> target_gain_q16_{kUnityGainQ16};
    uint32_t current_gain_q16_{kUnityGainQ16};
    uint32_t ramp_target_q16_{kUnityGainQ16};
    uint32_t ramp_remaining_frames_{0};
};

static_assert(std::atomic<uint32_t>::is_always_lock_free);

}  // namespace sendspin
