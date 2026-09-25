#include "audio_volume.h"

#include <algorithm>
#include <cmath>

namespace sendspin {

uint32_t PcmVolume::gain_for_volume(const uint8_t volume) noexcept {
    if (volume == 0) return 0;
    if (volume >= 100) return kUnityGainQ16;

    const double normalized = static_cast<double>(volume) / 100.0;
    return static_cast<uint32_t>(std::lround(
        std::pow(normalized, 1.5) * static_cast<double>(kUnityGainQ16)));
}

void PcmVolume::set_state(const uint8_t volume, const bool muted) noexcept {
    target_gain_q16_.store(muted ? 0 : gain_for_volume(volume), std::memory_order_release);
}

void PcmVolume::process(
    int16_t* samples,
    const uint32_t frames,
    const uint32_t channels) noexcept {
    if (samples == nullptr || frames == 0 || channels == 0) return;

    const auto target = target_gain_q16_.load(std::memory_order_acquire);
    if (target != ramp_target_q16_) {
        ramp_target_q16_ = target;
        ramp_remaining_frames_ = kRampFrames;
    }

    for (uint32_t frame = 0; frame < frames; ++frame) {
        if (ramp_remaining_frames_ != 0) {
            if (ramp_remaining_frames_ == 1) {
                current_gain_q16_ = ramp_target_q16_;
            } else {
                const auto difference = static_cast<int64_t>(ramp_target_q16_) -
                                        static_cast<int64_t>(current_gain_q16_);
                current_gain_q16_ = static_cast<uint32_t>(
                    static_cast<int64_t>(current_gain_q16_) +
                    difference / static_cast<int64_t>(ramp_remaining_frames_));
            }
            --ramp_remaining_frames_;
        }

        for (uint32_t channel = 0; channel < channels; ++channel) {
            const size_t index = static_cast<size_t>(frame) * channels + channel;
            const int64_t product = static_cast<int64_t>(samples[index]) * current_gain_q16_;
            const int64_t rounded = product >= 0
                ? product + (kUnityGainQ16 / 2)
                : product - (kUnityGainQ16 / 2);
            const int64_t scaled = rounded / kUnityGainQ16;
            samples[index] = static_cast<int16_t>(std::clamp<int64_t>(scaled, -32768, 32767));
        }
    }
}

}  // namespace sendspin
