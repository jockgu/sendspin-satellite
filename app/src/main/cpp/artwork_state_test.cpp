#include "artwork_state.h"

#include <cassert>
#include <cstdint>
#include <limits>
#include <vector>

using sendspin::ArtworkState;

int main() {
    ArtworkState state;

    const auto initial = state.snapshot_after(std::numeric_limits<uint64_t>::max());
    assert(initial.has_value());
    assert(initial->revision == 0);
    assert(initial->generation == 0);
    assert(initial->encoded_jpeg.empty());
    assert(!state.snapshot_after(initial->revision).has_value());

    const std::vector<uint8_t> first{1, 2, 3};
    state.stage(1, first.data(), first.size());
    assert(!state.snapshot_after(initial->revision).has_value());
    state.display(1);
    const auto displayed = state.snapshot_after(initial->revision);
    assert(displayed.has_value());
    assert(displayed->generation == 1);
    assert(displayed->encoded_jpeg == first);

    const std::vector<uint8_t> second{4, 5};
    state.stage(1, first.data(), first.size());
    state.stage(1, second.data(), second.size());
    state.display(1);
    const auto latest = state.snapshot_after(displayed->revision);
    assert(latest.has_value());
    assert(latest->encoded_jpeg == second);

    state.stage(1, second.data(), second.size());
    state.display(1);
    assert(!state.snapshot_after(latest->revision).has_value());

    state.stage(1, first.data(), first.size());
    state.stage(1, first.data(), first.size(), false);
    state.display(1);
    const auto invalid = state.snapshot_after(latest->revision);
    assert(invalid.has_value());
    assert(invalid->encoded_jpeg.empty());

    state.stage(1, first.data(), first.size());
    state.display(1);
    const auto restored = state.snapshot_after(invalid->revision);
    assert(restored.has_value());
    assert(restored->encoded_jpeg == first);

    const uint8_t marker = 9;
    state.stage(1, &marker, ArtworkState::kMaxEncodedBytes + 1);
    state.display(1);
    const auto oversized = state.snapshot_after(restored->revision);
    assert(oversized.has_value());
    assert(oversized->encoded_jpeg.empty());

    state.stage(1, first.data(), first.size());
    state.display(1);
    const auto before_generation_change = state.snapshot_after(oversized->revision);
    assert(before_generation_change.has_value());
    state.clear_all(2);
    const auto cleared = state.snapshot_after(before_generation_change->revision);
    assert(cleared.has_value());
    assert(cleared->generation == 2);
    assert(cleared->encoded_jpeg.empty());

    state.stage(1, first.data(), first.size());
    state.display(1);
    assert(!state.snapshot_after(cleared->revision).has_value());

    state.clear_all_current_generation();
    const auto synchronously_cleared = state.snapshot_after(cleared->revision);
    assert(synchronously_cleared.has_value());
    assert(synchronously_cleared->generation == 2);
    assert(synchronously_cleared->encoded_jpeg.empty());

    return 0;
}
