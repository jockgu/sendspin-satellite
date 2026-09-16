#include "now_playing_state.h"

#include <cassert>
#include <cstdint>
#include <limits>
#include <string>
#include <utility>

using sendspin::NowPlayingState;

int main() {
    NowPlayingState state;

    const auto initial = state.snapshot_after(std::numeric_limits<uint64_t>::max());
    assert(initial.has_value());
    assert(initial->revision == 0);
    assert(initial->generation == 0);
    assert(!initial->title.has_value());
    assert(!initial->group.has_value());
    assert(!state.snapshot_after(initial->revision).has_value());

    NowPlayingState::Metadata metadata;
    metadata.title = "Track title";
    metadata.artist = "Artist";
    metadata.album_artist = "Album artist";
    metadata.album = "Album";
    metadata.progress = NowPlayingState::Progress{
        .reported_position_ms = 12'345,
        .duration_ms = 234'567,
        .playback_speed_milli = 1'000,
        .interpolated_position_ms = 12'345,
    };
    state.update_metadata(2, std::move(metadata));

    const auto populated = state.snapshot_after(initial->revision);
    assert(populated.has_value());
    assert(populated->generation == 2);
    assert(populated->title == std::optional<std::string>("Track title"));
    assert(populated->artist == std::optional<std::string>("Artist"));
    assert(populated->album_artist == std::optional<std::string>("Album artist"));
    assert(populated->album == std::optional<std::string>("Album"));
    assert(populated->progress.has_value());
    assert(populated->progress->reported_position_ms == 12'345);
    assert(populated->progress->duration_ms == 234'567);
    assert(populated->progress->playback_speed_milli == 1'000);
    assert(populated->progress->interpolated_position_ms == 12'345);

    state.update_interpolated_progress(2, 34'567);
    const auto interpolated = state.snapshot_after(populated->revision);
    assert(interpolated.has_value());
    assert(interpolated->progress->reported_position_ms == 12'345);
    assert(interpolated->progress->interpolated_position_ms == 34'567);
    assert(!state.snapshot_after(interpolated->revision).has_value());

    state.update_interpolated_progress(2, 7'000);
    const auto seek_corrected = state.snapshot_after(interpolated->revision);
    assert(seek_corrected.has_value());
    assert(seek_corrected->progress->reported_position_ms == 12'345);
    assert(seek_corrected->progress->interpolated_position_ms == 7'000);

    state.update_interpolated_progress(2, 300'000);
    const auto bounded = state.snapshot_after(seek_corrected->revision);
    assert(bounded.has_value());
    assert(bounded->progress->interpolated_position_ms == 234'567);

    NowPlayingState::Metadata paused_metadata;
    paused_metadata.title = "Paused track";
    paused_metadata.progress = NowPlayingState::Progress{
        .reported_position_ms = 45'000,
        .duration_ms = 234'567,
        .playback_speed_milli = 0,
        .interpolated_position_ms = 45'000,
    };
    state.update_metadata(2, std::move(paused_metadata));
    const auto paused = state.snapshot_after(bounded->revision);
    assert(paused.has_value());
    assert(paused->progress->interpolated_position_ms == 45'000);
    state.update_interpolated_progress(2, 60'000);
    assert(!state.snapshot_after(paused->revision).has_value());

    NowPlayingState::Metadata replacement_metadata;
    replacement_metadata.title = "New track";
    replacement_metadata.progress = NowPlayingState::Progress{
        .reported_position_ms = 2'000,
        .duration_ms = 90'000,
        .playback_speed_milli = 1'000,
        .interpolated_position_ms = 2'000,
    };
    state.update_metadata(2, std::move(replacement_metadata));
    const auto replacement = state.snapshot_after(paused->revision);
    assert(replacement.has_value());
    assert(replacement->title == std::optional<std::string>("New track"));
    assert(replacement->progress->reported_position_ms == 2'000);
    assert(replacement->progress->interpolated_position_ms == 2'000);

    state.update_group(2, NowPlayingState::Group{
        .name = "Downstairs",
        .playback_state = NowPlayingState::GroupPlaybackState::Playing,
    });
    const auto with_group = state.snapshot_after(replacement->revision);
    assert(with_group.has_value());
    assert(with_group->title == replacement->title);
    assert(with_group->group.has_value());
    assert(with_group->group->name == std::optional<std::string>("Downstairs"));
    assert(with_group->group->playback_state == NowPlayingState::GroupPlaybackState::Playing);

    state.clear_metadata(2);
    const auto metadata_cleared = state.snapshot_after(with_group->revision);
    assert(metadata_cleared.has_value());
    assert(!metadata_cleared->title.has_value());
    assert(!metadata_cleared->artist.has_value());
    assert(!metadata_cleared->album_artist.has_value());
    assert(!metadata_cleared->album.has_value());
    assert(!metadata_cleared->progress.has_value());
    assert(metadata_cleared->group.has_value());

    state.clear_all(3);
    const auto all_cleared = state.snapshot_after(metadata_cleared->revision);
    assert(all_cleared.has_value());
    assert(all_cleared->generation == 3);
    assert(!all_cleared->title.has_value());
    assert(!all_cleared->group.has_value());

    NowPlayingState::Metadata stale_metadata;
    stale_metadata.title = "Old track";
    state.update_metadata(2, std::move(stale_metadata));
    assert(!state.snapshot_after(all_cleared->revision).has_value());

    state.update_group(3, NowPlayingState::Group{
        .name = "Kitchen",
        .playback_state = NowPlayingState::GroupPlaybackState::Stopped,
    });
    const auto stopped_group = state.snapshot_after(all_cleared->revision);
    assert(stopped_group.has_value());
    assert(stopped_group->group.has_value());
    assert(stopped_group->group->playback_state == NowPlayingState::GroupPlaybackState::Stopped);

    state.clear_all_current_generation();
    const auto synchronous_clear = state.snapshot_after(stopped_group->revision);
    assert(synchronous_clear.has_value());
    assert(synchronous_clear->generation == 3);
    assert(!synchronous_clear->group.has_value());

    return 0;
}
