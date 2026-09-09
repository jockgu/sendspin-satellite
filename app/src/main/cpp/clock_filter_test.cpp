#include <cassert>
#include <limits>

#include "clock_filter.h"

namespace {

void stable_samples_converge() {
    sendspin::ClockFilter filter;
    for (int index = 0; index < 8; ++index) {
        const auto sent = 1'000'000LL + index * 100'000LL;
        filter.update(sent, sent + 11'000, sent + 11'100, sent + 2'100);
    }
    const auto diagnostics = filter.diagnostics();
    assert(diagnostics.converged);
    assert(diagnostics.offset_us == 10'000);
    assert(diagnostics.round_trip_us == 2'000);
}

void rejects_invalid_and_resets() {
    sendspin::ClockFilter filter;
    filter.update(100, 200, 600, 300);
    assert(filter.diagnostics().samples == 0);
    filter.update(
        std::numeric_limits<std::int64_t>::min(),
        std::numeric_limits<std::int64_t>::max(),
        std::numeric_limits<std::int64_t>::max(),
        std::numeric_limits<std::int64_t>::min());
    assert(filter.diagnostics().samples == 0);
    filter.update(1'000, 11'000, 11'100, 3'100);
    filter.reset();
    assert(filter.diagnostics().samples == 0);
}

void bounds_history_and_detects_unstable_offsets() {
    sendspin::ClockFilter filter;
    for (int index = 0; index < 20; ++index) {
        const auto sent = static_cast<long long>(index) * 10'000;
        const auto offset = index % 2 == 0 ? 0LL : 6'000LL;
        filter.update(sent, sent + offset + 1'000, sent + offset + 1'100, sent + 2'100);
    }
    const auto diagnostics = filter.diagnostics();
    assert(diagnostics.samples == 16);
    assert(!diagnostics.converged);
}

}  // namespace

int main() {
    stable_samples_converge();
    rejects_invalid_and_resets();
    bounds_history_and_detects_unstable_offsets();
}
