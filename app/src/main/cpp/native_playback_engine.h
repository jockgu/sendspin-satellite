#pragma once

#include <atomic>
#include <mutex>
#include <string>
#include <thread>

#include <sendspin/client.h>
#include <sendspin/player_role.h>

#include "oboe_pcm_output.h"
#include "sendspin_pcm_listener.h"

namespace sendspin {

class NativePlaybackEngine final : public SendspinClientListener, public SendspinNetworkProvider {
public:
    enum class State : int32_t { Disconnected, Connecting, Ready, Error };
    explicit NativePlaybackEngine(std::string client_id);
    ~NativePlaybackEngine();
    bool connect(std::string url);
    void disconnect();
    [[nodiscard]] State state() const;
    bool is_network_ready() override;
    void on_time_sync_updated(float) override;

private:
    static void on_frames_played(void* context, uint32_t frames);
    void run();

    OboePcmOutput output_;
    SendspinPcmListener listener_;
    SendspinClient client_;
    PlayerRole& player_;
    std::atomic<State> state_{State::Disconnected};
    std::atomic<bool> running_{false};
    std::mutex control_mutex_;
    std::string pending_url_;
    bool disconnect_requested_{false};
    std::thread loop_thread_;
};

}  // namespace sendspin
