#pragma once

#include <cstddef>
#include <cstdint>
#include <mutex>
#include <optional>
#include <vector>

namespace sendspin {

class ArtworkState final {
public:
    static constexpr size_t kMaxEncodedBytes = 2 * 1024 * 1024;

    struct Snapshot {
        uint64_t revision{0};
        uint32_t generation{0};
        std::vector<uint8_t> encoded_jpeg;
    };

    void stage(uint32_t generation, const uint8_t* data, size_t length, bool valid = true);
    void display(uint32_t generation);
    void clear(uint32_t generation);
    void clear_all(uint32_t generation);
    void clear_all_current_generation();

    [[nodiscard]] std::optional<Snapshot> snapshot_after(uint64_t known_revision) const;

private:
    bool accept_generation_locked(uint32_t generation);
    void clear_locked();
    void advance_revision_locked();

    mutable std::mutex mutex_;
    Snapshot snapshot_;
    std::vector<uint8_t> staged_jpeg_;
    bool staged_present_{false};
    bool staged_valid_{false};
    uint64_t lifecycle_epoch_{0};
};

}  // namespace sendspin
