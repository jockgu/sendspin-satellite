#pragma once

#include <atomic>
#include <cstdint>

namespace sendspin {

class PlaybackRecoveryState final {
public:
    enum class RecoveryCause : uint32_t {
        OutputError = 1u << 0,
        RouteChange = 1u << 1,
        FocusResume = 1u << 2,
        NetworkLost = 1u << 3,
        TransportLost = 1u << 4,
    };
    using RecoveryCauseMask = uint32_t;

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
    [[nodiscard]] uint32_t recovery_generation() const {
        return recovery_generation_.load(std::memory_order_acquire);
    }
    void connect();
    void synchronising();
    void ready();
    void buffering();
    void playing();
    void request_recovery(RecoveryCause cause);
    [[nodiscard]] RecoveryCauseMask take_recovery_causes();
    bool suspend_for_focus();
    bool begin_recovery();
    bool reconnect();
    void stop();
    void fail();

private:
    State state_{State::Stopped};
    std::atomic<uint32_t> recovery_generation_{0};
    std::atomic<RecoveryCauseMask> pending_recovery_causes_{0};
};

}  // namespace sendspin
