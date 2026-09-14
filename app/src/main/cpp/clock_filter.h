#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

namespace sendspin {

struct ClockDiagnostics {
    std::int64_t round_trip_us = 0;
    std::int64_t offset_us = 0;
    std::int64_t samples = 0;
    bool converged = false;
};

class ClockFilter {
public:
    void reset();
    void update(
        std::int64_t client_transmitted_us,
        std::int64_t server_received_us,
        std::int64_t server_transmitted_us,
        std::int64_t client_received_us);
    [[nodiscard]] ClockDiagnostics diagnostics() const;

private:
    struct Sample {
        std::int64_t offset_us;
        std::int64_t round_trip_us;
    };

    static constexpr std::size_t kMaxSamples = 16;
    static constexpr std::size_t kRequiredSamples = 8;
    static constexpr std::int64_t kMaxOffsetDeviationUs = 5'000;

    std::array<Sample, kMaxSamples> samples_{};
    std::size_t first_ = 0;
    std::size_t size_ = 0;
};

}  // namespace sendspin
