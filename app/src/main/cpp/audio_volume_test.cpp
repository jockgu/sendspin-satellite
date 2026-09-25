#include "audio_volume.h"

#include <array>
#include <cassert>
#include <cmath>
#include <cstdint>

int main() {
    using sendspin::PcmVolume;

    assert(PcmVolume::gain_for_volume(0) == 0);
    assert(PcmVolume::gain_for_volume(100) == PcmVolume::kUnityGainQ16);
    const auto half_volume_gain = PcmVolume::gain_for_volume(50);
    assert(std::abs(static_cast<int>(half_volume_gain) - 23170) <= 1);
    assert(PcmVolume::gain_for_volume(255) == PcmVolume::kUnityGainQ16);

    PcmVolume output;
    std::array<int16_t, PcmVolume::kRampFrames * 2> samples{};
    samples.fill(20000);
    output.process(samples.data(), PcmVolume::kRampFrames, 2);
    assert(samples.front() == 20000);

    output.set_state(0, false);
    samples.fill(20000);
    output.process(samples.data(), PcmVolume::kRampFrames, 2);
    assert(samples.back() == 0);
    for (size_t i = 2; i < samples.size(); i += 2) {
        assert(samples[i] <= samples[i - 2]);
    }

    output.set_state(50, true);
    samples.fill(20000);
    output.process(samples.data(), PcmVolume::kRampFrames, 2);
    assert(samples.back() == 0);

    output.set_state(50, false);
    samples.fill(20000);
    output.process(samples.data(), PcmVolume::kRampFrames, 2);
    assert(samples.back() >= 7069 && samples.back() <= 7072);

    output.set_state(100, false);
    samples.fill(32767);
    output.process(samples.data(), PcmVolume::kRampFrames, 2);
    assert(samples.back() == 32767);

    return 0;
}
