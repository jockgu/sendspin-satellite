#include "now_playing_state.h"

#include <algorithm>
#include <utility>

namespace sendspin {

void NowPlayingState::update_metadata(const uint32_t generation, Metadata metadata) {
    std::lock_guard lock(mutex_);
    if (!accept_generation_locked(generation)) return;

    if (metadata.progress.has_value()) {
        metadata.progress->interpolated_position_ms =
            metadata.progress->reported_position_ms;
    }

    snapshot_.title = std::move(metadata.title);
    snapshot_.artist = std::move(metadata.artist);
    snapshot_.album_artist = std::move(metadata.album_artist);
    snapshot_.album = std::move(metadata.album);
    snapshot_.progress = metadata.progress;
    advance_revision_locked();
}

void NowPlayingState::update_interpolated_progress(
    const uint32_t generation, const uint32_t position_ms) {
    std::lock_guard lock(mutex_);
    if (!accept_generation_locked(generation)) return;
    if (!snapshot_.progress.has_value() ||
        snapshot_.progress->duration_ms == 0 ||
        snapshot_.progress->playback_speed_milli == 0) {
        return;
    }

    const auto bounded_position = std::min(
        position_ms, snapshot_.progress->duration_ms);
    if (snapshot_.progress->interpolated_position_ms == bounded_position) return;

    snapshot_.progress->interpolated_position_ms = bounded_position;
    advance_revision_locked();
}

void NowPlayingState::clear_metadata(const uint32_t generation) {
    std::lock_guard lock(mutex_);
    if (!accept_generation_locked(generation)) return;

    snapshot_.title.reset();
    snapshot_.artist.reset();
    snapshot_.album_artist.reset();
    snapshot_.album.reset();
    snapshot_.progress.reset();
    advance_revision_locked();
}

void NowPlayingState::update_group(const uint32_t generation, Group group) {
    std::lock_guard lock(mutex_);
    if (!accept_generation_locked(generation)) return;

    snapshot_.group = std::move(group);
    advance_revision_locked();
}

void NowPlayingState::clear_all(const uint32_t generation) {
    std::lock_guard lock(mutex_);
    if (!accept_generation_locked(generation)) return;

    clear_locked();
    advance_revision_locked();
}

void NowPlayingState::clear_all_current_generation() {
    std::lock_guard lock(mutex_);
    clear_locked();
    advance_revision_locked();
}

std::optional<NowPlayingState::Snapshot> NowPlayingState::snapshot_after(
    const uint64_t known_revision) const {
    std::lock_guard lock(mutex_);
    if (snapshot_.revision == known_revision) return std::nullopt;
    return snapshot_;
}

bool NowPlayingState::accept_generation_locked(const uint32_t generation) {
    if (generation < snapshot_.generation) return false;
    if (generation > snapshot_.generation) {
        clear_locked();
        snapshot_.generation = generation;
    }
    return true;
}

void NowPlayingState::clear_locked() {
    snapshot_.title.reset();
    snapshot_.artist.reset();
    snapshot_.album_artist.reset();
    snapshot_.album.reset();
    snapshot_.progress.reset();
    snapshot_.group.reset();
}

void NowPlayingState::advance_revision_locked() {
    ++snapshot_.revision;
}

}  // namespace sendspin
