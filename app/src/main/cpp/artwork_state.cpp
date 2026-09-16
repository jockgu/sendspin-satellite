#include "artwork_state.h"

#include <new>
#include <utility>

namespace sendspin {

void ArtworkState::stage(
    const uint32_t generation, const uint8_t* data, const size_t length, const bool valid) {
    uint64_t lifecycle_epoch;
    {
        std::lock_guard lock(mutex_);
        if (!accept_generation_locked(generation)) return;
        lifecycle_epoch = lifecycle_epoch_;
    }

    const bool accepted = valid && data != nullptr && length != 0 && length <= kMaxEncodedBytes;
    if (!accepted) {
        std::lock_guard lock(mutex_);
        if (lifecycle_epoch != lifecycle_epoch_ || !accept_generation_locked(generation)) return;
        staged_jpeg_.clear();
        staged_present_ = true;
        staged_valid_ = false;
        return;
    }

    std::vector<uint8_t> next;
    try {
        next.assign(data, data + length);
    } catch (const std::bad_alloc&) {
        std::lock_guard lock(mutex_);
        if (lifecycle_epoch != lifecycle_epoch_ || !accept_generation_locked(generation)) return;
        staged_jpeg_.clear();
        staged_present_ = true;
        staged_valid_ = false;
        return;
    }
    std::lock_guard lock(mutex_);
    if (lifecycle_epoch != lifecycle_epoch_ || !accept_generation_locked(generation)) return;
    staged_jpeg_ = std::move(next);
    staged_present_ = true;
    staged_valid_ = true;
}

void ArtworkState::display(const uint32_t generation) {
    std::lock_guard lock(mutex_);
    if (!accept_generation_locked(generation) || !staged_present_) return;

    const bool changed = staged_valid_
        ? snapshot_.encoded_jpeg != staged_jpeg_
        : !snapshot_.encoded_jpeg.empty();
    if (staged_valid_) {
        snapshot_.encoded_jpeg = std::move(staged_jpeg_);
    } else {
        snapshot_.encoded_jpeg.clear();
    }
    staged_jpeg_.clear();
    staged_present_ = false;
    staged_valid_ = false;
    if (changed) advance_revision_locked();
}

void ArtworkState::clear(const uint32_t generation) {
    std::lock_guard lock(mutex_);
    if (!accept_generation_locked(generation)) return;

    const bool changed = staged_present_ || !snapshot_.encoded_jpeg.empty();
    ++lifecycle_epoch_;
    clear_locked();
    if (changed) advance_revision_locked();
}

void ArtworkState::clear_all(const uint32_t generation) {
    std::lock_guard lock(mutex_);
    if (generation < snapshot_.generation) return;

    ++lifecycle_epoch_;
    clear_locked();
    snapshot_.generation = generation;
    advance_revision_locked();
}

void ArtworkState::clear_all_current_generation() {
    std::lock_guard lock(mutex_);
    ++lifecycle_epoch_;
    clear_locked();
    advance_revision_locked();
}

std::optional<ArtworkState::Snapshot> ArtworkState::snapshot_after(
    const uint64_t known_revision) const {
    std::lock_guard lock(mutex_);
    if (snapshot_.revision == known_revision) return std::nullopt;

    try {
        return snapshot_;
    } catch (const std::bad_alloc&) {
        return std::nullopt;
    }
}

bool ArtworkState::accept_generation_locked(const uint32_t generation) {
    if (generation < snapshot_.generation) return false;
    if (generation > snapshot_.generation) {
        clear_locked();
        snapshot_.generation = generation;
    }
    return true;
}

void ArtworkState::clear_locked() {
    snapshot_.encoded_jpeg.clear();
    staged_jpeg_.clear();
    staged_present_ = false;
    staged_valid_ = false;
}

void ArtworkState::advance_revision_locked() {
    ++snapshot_.revision;
}

}  // namespace sendspin
