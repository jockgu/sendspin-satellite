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

private:
    OboePcmOutput& output_;
    StreamObserver stream_observer_;
    void* stream_observer_context_;
};

}  // namespace sendspin
