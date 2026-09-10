#pragma once

#include <cstdint>

namespace sendspin {

class PlaybackRecoveryState final {
public:
    enum class State : int32_t {
        Stopped,
        Connecting,
        Synchronising,
        Ready,
        Buffering,
        Playing,
        Recovering,
        Error,
    };

    [[nodiscard]] State state() const { return state_; }
    [[nodiscard]] uint32_t recovery_generation() const { return recovery_generation_; }
    void connect();
    void synchronising();
    void ready();
    void buffering();
    void playing();
    bool begin_recovery();
    bool reconnect();
    void stop();
    void fail();

private:
    State state_{State::Stopped};
    uint32_t recovery_generation_{0};
};

}  // namespace sendspin
