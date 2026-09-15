#pragma once

#include <cstdint>
#include <mutex>
#include <optional>
#include <string>

namespace sendspin {

class NowPlayingState final {
public:
    enum class GroupPlaybackState : int32_t {
        Playing,
        Stopped,
    };

    struct Progress {
        uint32_t reported_position_ms{0};
        uint32_t duration_ms{0};
        uint32_t playback_speed_milli{0};
    };

    struct Metadata {
        std::optional<std::string> title;
        std::optional<std::string> artist;
        std::optional<std::string> album_artist;
        std::optional<std::string> album;
        std::optional<Progress> progress;
    };

    struct Group {
        std::optional<std::string> name;
        std::optional<GroupPlaybackState> playback_state;
    };

    struct Snapshot {
        uint64_t revision{0};
        uint32_t generation{0};
        std::optional<std::string> title;
        std::optional<std::string> artist;
        std::optional<std::string> album_artist;
        std::optional<std::string> album;
        std::optional<Progress> progress;
        std::optional<Group> group;
    };

    void update_metadata(uint32_t generation, Metadata metadata);
    void clear_metadata(uint32_t generation);
    void update_group(uint32_t generation, Group group);
    void clear_all(uint32_t generation);
    void clear_all_current_generation();

    [[nodiscard]] std::optional<Snapshot> snapshot_after(uint64_t known_revision) const;

private:
    bool accept_generation_locked(uint32_t generation);
    void clear_locked();
    void advance_revision_locked();

    mutable std::mutex mutex_;
    Snapshot snapshot_;
};

}  // namespace sendspin
