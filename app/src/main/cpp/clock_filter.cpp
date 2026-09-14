#include "clock_filter.h"

#include <algorithm>
#include <array>

namespace sendspin {

namespace {

bool subtract(std::int64_t left, std::int64_t right, std::int64_t* result) {
    return !__builtin_sub_overflow(left, right, result);
}

bool add(std::int64_t left, std::int64_t right, std::int64_t* result) {
    return !__builtin_add_overflow(left, right, result);
}

bool exceeds_deviation(std::int64_t left, std::int64_t right, std::int64_t limit) {
    const auto difference = left >= right
        ? static_cast<std::uint64_t>(left) - static_cast<std::uint64_t>(right)
        : static_cast<std::uint64_t>(right) - static_cast<std::uint64_t>(left);
    return difference > static_cast<std::uint64_t>(limit);
}

}  // namespace

void ClockFilter::reset() {
    first_ = 0;
    size_ = 0;
}

void ClockFilter::update(
    const std::int64_t client_transmitted_us,
    const std::int64_t server_received_us,
    const std::int64_t server_transmitted_us,
    const std::int64_t client_received_us) {
    std::int64_t client_leg_us;
    std::int64_t server_leg_us;
    std::int64_t round_trip_us;
    std::int64_t offset_first_half;
    std::int64_t offset_second_half;
    std::int64_t offset_sum;
    if (!subtract(client_received_us, client_transmitted_us, &client_leg_us) ||
        !subtract(server_transmitted_us, server_received_us, &server_leg_us) ||
        !subtract(client_leg_us, server_leg_us, &round_trip_us) ||
        round_trip_us < 0 ||
        !subtract(server_received_us, client_transmitted_us, &offset_first_half) ||
        !subtract(server_transmitted_us, client_received_us, &offset_second_half) ||
        !add(offset_first_half, offset_second_half, &offset_sum)) return;
    const std::int64_t offset_us = offset_sum / 2;
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

    bool offsets_are_stable = true;
    for (std::size_t index = 0; index < size_; ++index) {
        if (exceeds_deviation(
                samples_[(first_ + index) % kMaxSamples].offset_us,
                offset_us,
                kMaxOffsetDeviationUs)) {
            offsets_are_stable = false;
            break;
        }
    }
    return {round_trip_us, offset_us, static_cast<std::int64_t>(size_),
        size_ >= kRequiredSamples && offsets_are_stable};
}

}  // namespace sendspin
