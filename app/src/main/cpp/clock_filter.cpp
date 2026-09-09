#include "clock_filter.h"

#include <algorithm>
#include <array>
#include <cstdlib>

namespace sendspin {

void ClockFilter::reset() {
    first_ = 0;
    size_ = 0;
}

void ClockFilter::update(
    const std::int64_t client_transmitted_us,
    const std::int64_t server_received_us,
    const std::int64_t server_transmitted_us,
    const std::int64_t client_received_us) {
    const auto round_trip_us = (client_received_us - client_transmitted_us) -
        (server_transmitted_us - server_received_us);
    if (round_trip_us < 0) return;

    const auto offset_us = ((server_received_us - client_transmitted_us) +
        (server_transmitted_us - client_received_us)) / 2;
    const auto index = (first_ + size_) % kMaxSamples;
    samples_[index] = {offset_us, round_trip_us};
    if (size_ < kMaxSamples) {
        ++size_;
    } else {
        first_ = (first_ + 1) % kMaxSamples;
    }
}

ClockDiagnostics ClockFilter::diagnostics() const {
    if (size_ == 0) return {};

    std::array<std::int64_t, kMaxSamples> offsets{};
    auto round_trip_us = samples_[first_].round_trip_us;
    for (std::size_t index = 0; index < size_; ++index) {
        const auto& sample = samples_[(first_ + index) % kMaxSamples];
        offsets[index] = sample.offset_us;
        round_trip_us = std::min(round_trip_us, sample.round_trip_us);
    }
    std::sort(offsets.begin(), offsets.begin() + static_cast<std::ptrdiff_t>(size_));
    const auto offset_us = offsets[size_ / 2];

    auto max_deviation_us = std::int64_t{0};
    for (std::size_t index = 0; index < size_; ++index) {
        const auto deviation_us = static_cast<std::int64_t>(std::llabs(
            samples_[(first_ + index) % kMaxSamples].offset_us - offset_us));
        max_deviation_us = std::max(
            max_deviation_us,
            deviation_us);
    }
    return {round_trip_us, offset_us, static_cast<std::int64_t>(size_),
        size_ >= kRequiredSamples && max_deviation_us <= kMaxOffsetDeviationUs};
}

}  // namespace sendspin
