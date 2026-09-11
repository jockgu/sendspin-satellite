#pragma once

#include <atomic>
#include <mutex>
#include <string>
#include <thread>

#include <sendspin/client.h>
#include <sendspin/player_role.h>

#include "oboe_pcm_output.h"
#include "playback_recovery_state.h"
#include "sendspin_pcm_listener.h"

namespace sendspin {

class NativePlaybackEngine final : public SendspinClientListener, public SendspinNetworkProvider {
public:
    using State = PlaybackRecoveryState::State;
    NativePlaybackEngine(std::string client_id, std::string player_name);
    ~NativePlaybackEngine();
    bool connect(std::string url);
    void disconnect();
    void request_recovery(
        PlaybackRecoveryState::RecoveryCause cause =
            PlaybackRecoveryState::RecoveryCause::OutputError);
    void suspend_for_focus();
    void resume_from_focus();
    [[nodiscard]] State state() const;
    bool is_network_ready() override;
    void on_time_sync_updated(float) override;

private:
    static void on_frames_played(void* context, uint32_t frames);
    static void on_stream_started(void* context);
    void publish_state();
    void run();

    OboePcmOutput output_;
    SendspinPcmListener listener_;
    SendspinClient client_;
    PlayerRole& player_;
    PlaybackRecoveryState recovery_state_;
    std::atomic<State> state_{State::Stopped};
    std::atomic<bool> running_{false};
    std::mutex control_mutex_;
    std::string pending_url_;
    bool disconnect_requested_{false};
    std::atomic<bool> focus_suspended_{false};
    std::atomic<bool> buffering_requested_{false};
    std::atomic<bool> playing_requested_{false};
    std::thread loop_thread_;
};

}  // namespace sendspin
