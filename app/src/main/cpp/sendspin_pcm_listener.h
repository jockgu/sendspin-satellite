#pragma once

#include <cstddef>
#include <cstdint>

#include <sendspin/player_role.h>

#include "oboe_pcm_output.h"

namespace sendspin {

class SendspinPcmListener final : public PlayerRoleListener {
public:
    explicit SendspinPcmListener(OboePcmOutput& output) : output_(output) {}

    size_t on_audio_write(uint8_t* data, size_t length, uint32_t timeout_ms) override;
    void on_stream_clear() override;

private:
    OboePcmOutput& output_;
};

}  // namespace sendspin
