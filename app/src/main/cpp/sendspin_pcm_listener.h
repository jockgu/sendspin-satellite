#pragma once

#include <cstddef>
#include <cstdint>

#include <sendspin/player_role.h>

#include "oboe_pcm_output.h"

namespace sendspin {

class SendspinPcmListener final : public PlayerRoleListener {
public:
    using StreamObserver = void (*)(void*);

    explicit SendspinPcmListener(
        OboePcmOutput& output,
        StreamObserver stream_observer = nullptr,
        void* stream_observer_context = nullptr)
        : output_(output),
          stream_observer_(stream_observer),
          stream_observer_context_(stream_observer_context) {}

    size_t on_audio_write(uint8_t* data, size_t length, uint32_t timeout_ms) override;
    void on_stream_clear() override;
    void on_stream_start() override;
    void on_volume_changed(uint8_t volume) override;
    void on_mute_changed(bool muted) override;
    void set_volume_state(uint8_t volume, bool muted);

private:
    void apply_volume_state();

    OboePcmOutput& output_;
    StreamObserver stream_observer_;
    void* stream_observer_context_;
    uint8_t volume_{100};
    bool muted_{false};
};

}  // namespace sendspin
