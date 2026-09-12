#include "native_playback_engine.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <limits>

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
bool has_cause(
    const PlaybackRecoveryState::RecoveryCauseMask mask,
    const PlaybackRecoveryState::RecoveryCause cause) {
    return (mask & static_cast<PlaybackRecoveryState::RecoveryCauseMask>(cause)) != 0;
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
void NativePlaybackEngine::set_network_available(const bool available) {
    network_available_.store(available, std::memory_order_release);
}
void NativePlaybackEngine::suspend_for_focus() {
    focus_suspended_.store(true, std::memory_order_release);
}
void NativePlaybackEngine::resume_from_focus() {
    focus_suspended_.store(false, std::memory_order_release);
}
NativePlaybackEngine::State NativePlaybackEngine::state() const { return state_.load(); }
NativePlaybackEngine::Diagnostics NativePlaybackEngine::diagnostics() const {
    std::lock_guard lock(diagnostics_mutex_);
    return diagnostics_;
}
bool NativePlaybackEngine::is_network_ready() {
    return network_available_.load(std::memory_order_acquire);
}
void NativePlaybackEngine::on_time_sync_updated(const float error) {
    recovery_state_.synchronising();
    recovery_state_.ready();
    reconnect_policy_.record_convergence();
    if (reconnect_attempt_active_) {
        record_reconnect_completion();
        reconnect_attempt_active_ = false;
    }
    {
        std::lock_guard lock(diagnostics_mutex_);
        diagnostics_.clock_converged = true;
        if (diagnostics_.clock_samples != std::numeric_limits<uint32_t>::max()) {
            ++diagnostics_.clock_samples;
        }
        if (std::isfinite(error) && error >= 0.0F) {
            diagnostics_.clock_error_us = static_cast<int64_t>(error);
        }
    }
    publish_state();
}
void NativePlaybackEngine::on_frames_played(void* context, uint32_t frames) {
    auto* engine = static_cast<NativePlaybackEngine*>(context);
    engine->pending_audio_played_frames_.fetch_add(frames, std::memory_order_release);
    engine->playing_requested_.store(true, std::memory_order_release);
}
void NativePlaybackEngine::on_stream_started(void* context) {
    static_cast<NativePlaybackEngine*>(context)->buffering_requested_.store(
        true, std::memory_order_release);
}
void NativePlaybackEngine::publish_state() {
    const auto next_state = recovery_state_.state();
    state_.store(next_state, std::memory_order_release);
    std::lock_guard lock(diagnostics_mutex_);
    diagnostics_.state = next_state;
    diagnostics_.generation = recovery_state_.recovery_generation();
    refresh_output_diagnostics_locked();
}
void NativePlaybackEngine::refresh_output_diagnostics() {
    std::lock_guard lock(diagnostics_mutex_);
    refresh_output_diagnostics_locked();
}
void NativePlaybackEngine::refresh_output_diagnostics_locked() {
    diagnostics_.queued_frames = output_.queued_frames();
    diagnostics_.underruns = output_.underruns();
    diagnostics_.output_latency_us = output_.latency_us();
    const auto output_diagnostics = output_.diagnostics();
    diagnostics_.output_stream_open = output_diagnostics.open;
    diagnostics_.output_stream_state = output_diagnostics.state;
    diagnostics_.output_sample_rate = output_diagnostics.sample_rate;
    diagnostics_.output_channel_count = output_diagnostics.channel_count;
    diagnostics_.output_format = output_diagnostics.format;
    diagnostics_.output_performance_mode = output_diagnostics.performance_mode;
    diagnostics_.output_sharing_mode = output_diagnostics.sharing_mode;
    diagnostics_.output_device_id = output_diagnostics.device_id;
    diagnostics_.output_session_id = output_diagnostics.session_id;
    diagnostics_.output_frames_per_burst = output_diagnostics.frames_per_burst;
    diagnostics_.output_buffer_size_frames = output_diagnostics.buffer_size_frames;
    diagnostics_.output_buffer_capacity_frames = output_diagnostics.buffer_capacity_frames;
    diagnostics_.output_xrun_count = output_diagnostics.xrun_count;
}
void NativePlaybackEngine::record_failure(const Failure failure) {
    std::lock_guard lock(diagnostics_mutex_);
    diagnostics_.last_failure = failure;
}
void NativePlaybackEngine::record_hard_resync() {
    std::lock_guard lock(diagnostics_mutex_);
    ++diagnostics_.hard_resyncs;
}
void NativePlaybackEngine::record_output_restart() {
    std::lock_guard lock(diagnostics_mutex_);
    ++diagnostics_.output_restarts;
}
void NativePlaybackEngine::record_reconnect_attempt() {
    std::lock_guard lock(diagnostics_mutex_);
    ++diagnostics_.reconnect_attempts;
}
void NativePlaybackEngine::record_reconnect_completion() {
    std::lock_guard lock(diagnostics_mutex_);
    ++diagnostics_.reconnect_completions;
}
void NativePlaybackEngine::drain_playback_feedback() {
    const auto frames = pending_audio_played_frames_.exchange(0, std::memory_order_acq_rel);
    if (frames == 0) return;

    const auto now_us = monotonic_us();
    const auto latency_us = output_.latency_us();
    const auto finish_timestamp = now_us + std::max<int64_t>(0, latency_us);
    uint64_t remaining = frames;
    while (remaining != 0) {
        const auto batch = static_cast<uint32_t>(std::min<uint64_t>(
            remaining, std::numeric_limits<uint32_t>::max()));
        player_.notify_audio_played(batch, finish_timestamp);
        remaining -= batch;
    }
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
    bool had_connection = false;
    bool was_network_available = network_available_.load(std::memory_order_acquire);
    while (running_.load(std::memory_order_acquire)) {
        const auto now_us = monotonic_us();
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
            reconnect_policy_.cancel();
            active_url.clear();
            had_connection = false;
            recovery_state_.stop();
            publish_state();
            output_.stop();
            client_.disconnect(SendspinGoodbyeReason::USER_REQUEST);
        }
        const bool focus_suspend_requested = focus_suspended_.load(std::memory_order_acquire);
        if (focus_suspend_requested && !focus_suspended) {
            focus_suspended = true;
            if (recovery_state_.suspend_for_focus()) {
                reconnect_policy_.cancel();
                reconnect_attempt_active_ = false;
                output_.stop();
                client_.disconnect(SendspinGoodbyeReason::RESTART);
                publish_state();
            }
        } else if (!focus_suspend_requested && focus_suspended) {
            focus_suspended = false;
            recovery_state_.request_recovery(
                PlaybackRecoveryState::RecoveryCause::FocusResume);
        }
        const bool network_available = network_available_.load(std::memory_order_acquire);
        if (!network_available && was_network_available) {
            recovery_state_.request_recovery(
                PlaybackRecoveryState::RecoveryCause::NetworkLost);
        }
        was_network_available = network_available;
        if (!url.empty()) {
            active_url = std::move(url);
            reconnect_policy_.cancel();
            recovery_state_.connect();
            reconnect_policy_.request(now_us);
            {
                std::lock_guard lock(diagnostics_mutex_);
                diagnostics_.clock_converged = false;
                diagnostics_.clock_error_us = -1;
                diagnostics_.clock_samples = 0;
            }
            publish_state();
        }
        const auto recovery_causes = focus_suspended
            ? PlaybackRecoveryState::RecoveryCauseMask{0}
            : recovery_state_.take_recovery_causes();
        if (!disconnect && recovery_causes != 0) {
            const bool already_recovering =
                recovery_state_.state() == PlaybackRecoveryState::State::Recovering;
            const bool began_recovery = recovery_state_.begin_recovery();
            if (began_recovery || already_recovering) {
                if (has_cause(recovery_causes,
                              PlaybackRecoveryState::RecoveryCause::NetworkLost)) {
                    record_failure(Failure::NetworkUnavailable);
                    if (began_recovery) record_hard_resync();
                } else if (has_cause(recovery_causes,
                                     PlaybackRecoveryState::RecoveryCause::TransportLost)) {
                    record_failure(Failure::TransportLost);
                    if (began_recovery) record_hard_resync();
                } else if (has_cause(recovery_causes,
                                     PlaybackRecoveryState::RecoveryCause::OutputError)) {
                    record_failure(Failure::OutputError);
                } else if (has_cause(recovery_causes,
                                     PlaybackRecoveryState::RecoveryCause::RouteChange)) {
                    record_failure(Failure::RouteChange);
                } else if (has_cause(recovery_causes,
                                     PlaybackRecoveryState::RecoveryCause::FocusResume)) {
                    record_failure(Failure::FocusResume);
                }
                if (reconnect_policy_.attempt_in_flight()) {
                    reconnect_policy_.record_failure(now_us);
                    reconnect_attempt_active_ = false;
                }
                publish_state();
                const bool output_restarted = output_.restart();
                record_output_restart();
                client_.disconnect(SendspinGoodbyeReason::RESTART);
                had_connection = false;
                if (output_restarted) {
                    reconnect_policy_.request(now_us);
                } else {
                    reconnect_policy_.cancel();
                    record_failure(Failure::OutputRestartFailed);
                    recovery_state_.fail();
                    publish_state();
                }
            }
        }
        if (!disconnect && !focus_suspended && network_available && !active_url.empty() &&
            reconnect_policy_.take_due_attempt(now_us)) {
            const bool retrying =
                recovery_state_.state() == PlaybackRecoveryState::State::Recovering;
            bool can_connect = false;
            if (retrying) {
                can_connect = recovery_state_.reconnect();
            } else if (recovery_state_.state() == PlaybackRecoveryState::State::Connecting) {
                can_connect = true;
            }
            if (can_connect) {
                if (retrying) {
                    record_reconnect_attempt();
                    reconnect_attempt_active_ = true;
                }
                {
                    std::lock_guard lock(diagnostics_mutex_);
                    diagnostics_.clock_converged = false;
                    diagnostics_.clock_error_us = -1;
                    diagnostics_.clock_samples = 0;
                }
                publish_state();
                client_.connect_to(active_url);
            } else {
                reconnect_policy_.record_failure(now_us);
                publish_state();
            }
        }
        client_.loop();
        drain_playback_feedback();
        if (buffering_requested_.exchange(false, std::memory_order_acq_rel)) {
            recovery_state_.buffering();
            publish_state();
        }
        if (playing_requested_.exchange(false, std::memory_order_acq_rel)) {
            recovery_state_.playing();
            publish_state();
        }
        const bool connected = client_.is_connected();
        if (had_connection && !connected) {
            recovery_state_.request_recovery(
                PlaybackRecoveryState::RecoveryCause::TransportLost);
        }
        had_connection = connected;
        if (!focus_suspended && !connected && reconnect_policy_.attempt_timed_out(now_us)) {
            client_.disconnect(SendspinGoodbyeReason::RESTART);
            output_.clear();
            const bool began_recovery = recovery_state_.begin_recovery();
            if (began_recovery) {
                record_hard_resync();
                publish_state();
            }
            record_failure(Failure::HandshakeTimeout);
            reconnect_policy_.record_failure(now_us);
            reconnect_attempt_active_ = false;
            publish_state();
        }
        refresh_output_diagnostics();
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
    client_.disconnect(SendspinGoodbyeReason::USER_REQUEST);
}
}  // namespace sendspin
