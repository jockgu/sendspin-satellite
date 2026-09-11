#include "native_playback_engine.h"

#include <chrono>

namespace sendspin {
namespace {
SendspinClientConfig client_config(const std::string& client_id, const std::string& player_name) {
    SendspinClientConfig config;
    config.client_id = client_id;
    config.name = player_name;
    config.product_name = "Sendspin Satellite";
    config.manufacturer = "Nanopixel";
    config.software_version = "0.1-alpha";
    return config;
}
PlayerRoleConfig player_config() {
    PlayerRoleConfig config;
    config.audio_formats = {{SendspinCodecFormat::PCM, PcmRenderFifo::kChannels,
                             PcmRenderFifo::kSampleRate, 16}};
    return config;
}
int64_t monotonic_us() {
    return std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}
}  // namespace

NativePlaybackEngine::NativePlaybackEngine(std::string client_id, std::string player_name)
    : listener_(output_, &NativePlaybackEngine::on_stream_started, this),
    client_(client_config(client_id, player_name)),
      player_(client_.add_player(player_config())) {
    client_.set_listener(this);
    client_.set_network_provider(this);
    player_.set_listener(&listener_);
    output_.set_playback_observer(&NativePlaybackEngine::on_frames_played, this);
}
NativePlaybackEngine::~NativePlaybackEngine() {
    disconnect();
    running_.store(false, std::memory_order_release);
    if (loop_thread_.joinable()) loop_thread_.join();
    output_.stop();
}
bool NativePlaybackEngine::connect(std::string url) {
    if (url.empty() || !output_.start()) {
        state_.store(State::Error, std::memory_order_release);
        return false;
    }
    {
        std::lock_guard lock(control_mutex_);
        pending_url_ = std::move(url);
        disconnect_requested_ = false;
    }
    state_.store(State::Connecting, std::memory_order_release);
    if (!running_.exchange(true, std::memory_order_acq_rel)) {
        loop_thread_ = std::thread(&NativePlaybackEngine::run, this);
    }
    return true;
}
void NativePlaybackEngine::disconnect() {
    std::lock_guard lock(control_mutex_);
    pending_url_.clear();
    disconnect_requested_ = true;
    state_.store(State::Stopped, std::memory_order_release);
}
void NativePlaybackEngine::request_recovery(const PlaybackRecoveryState::RecoveryCause cause) {
    recovery_state_.request_recovery(cause);
}
void NativePlaybackEngine::suspend_for_focus() {
    focus_suspended_.store(true, std::memory_order_release);
}
void NativePlaybackEngine::resume_from_focus() {
    focus_suspended_.store(false, std::memory_order_release);
}
NativePlaybackEngine::State NativePlaybackEngine::state() const { return state_.load(); }
bool NativePlaybackEngine::is_network_ready() { return true; }
void NativePlaybackEngine::on_time_sync_updated(float) {
    recovery_state_.synchronising();
    recovery_state_.ready();
    publish_state();
}
void NativePlaybackEngine::on_frames_played(void* context, uint32_t frames) {
    auto* engine = static_cast<NativePlaybackEngine*>(context);
    engine->playing_requested_.store(true, std::memory_order_release);
    engine->player_.notify_audio_played(frames, monotonic_us());
}
void NativePlaybackEngine::on_stream_started(void* context) {
    static_cast<NativePlaybackEngine*>(context)->buffering_requested_.store(
        true, std::memory_order_release);
}
void NativePlaybackEngine::publish_state() {
    state_.store(recovery_state_.state(), std::memory_order_release);
}
void NativePlaybackEngine::run() {
    if (!client_.start_server()) {
        recovery_state_.fail();
        publish_state();
        running_.store(false, std::memory_order_release);
        return;
    }
    std::string active_url;
    bool focus_suspended = false;
    while (running_.load(std::memory_order_acquire)) {
        if (output_.take_error_recovery_request()) {
            recovery_state_.request_recovery(PlaybackRecoveryState::RecoveryCause::OutputError);
        }
        std::string url;
        bool disconnect = false;
        {
            std::lock_guard lock(control_mutex_);
            url.swap(pending_url_);
            disconnect = disconnect_requested_;
            disconnect_requested_ = false;
        }
        if (disconnect) {
            focus_suspended = false;
            recovery_state_.stop();
            publish_state();
            output_.stop();
            client_.disconnect(SendspinGoodbyeReason::USER_REQUEST);
        }
        const bool focus_suspend_requested = focus_suspended_.load(std::memory_order_acquire);
        if (focus_suspend_requested && !focus_suspended) {
            focus_suspended = true;
            if (recovery_state_.suspend_for_focus()) {
                output_.stop();
                client_.disconnect(SendspinGoodbyeReason::RESTART);
                publish_state();
            }
        } else if (!focus_suspend_requested && focus_suspended) {
            focus_suspended = false;
            recovery_state_.request_recovery(
                PlaybackRecoveryState::RecoveryCause::FocusResume);
        }
        if (!url.empty()) {
            active_url = url;
            recovery_state_.connect();
            publish_state();
            client_.connect_to(url);
        }
        const auto recovery_causes = focus_suspended
            ? PlaybackRecoveryState::RecoveryCauseMask{0}
            : recovery_state_.take_recovery_causes();
        if (!disconnect && recovery_causes != 0 &&
            (recovery_state_.begin_recovery() ||
             recovery_state_.state() == PlaybackRecoveryState::State::Recovering)) {
            publish_state();
            const bool output_restarted = output_.restart();
            client_.disconnect(SendspinGoodbyeReason::RESTART);
            if (output_restarted && !active_url.empty() && recovery_state_.reconnect()) {
                publish_state();
                client_.connect_to(active_url);
            } else {
                recovery_state_.fail();
                publish_state();
            }
        }
        client_.loop();
        if (buffering_requested_.exchange(false, std::memory_order_acq_rel)) {
            recovery_state_.buffering();
            publish_state();
        }
        if (playing_requested_.exchange(false, std::memory_order_acq_rel)) {
            recovery_state_.playing();
            publish_state();
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
    client_.disconnect(SendspinGoodbyeReason::USER_REQUEST);
}
}  // namespace sendspin
