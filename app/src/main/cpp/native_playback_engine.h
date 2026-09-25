#pragma once

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <optional>
#include <string>
#include <thread>

#include <sendspin/client.h>
#include <sendspin/artwork_role.h>
#include <sendspin/metadata_role.h>
#include <sendspin/player_role.h>

#include "now_playing_state.h"
#include "artwork_state.h"
#include "oboe_pcm_output.h"
#include "playback_recovery_state.h"
#include "reconnect_policy.h"
#include "sendspin_pcm_listener.h"

namespace sendspin {

class NativePlaybackEngine final : public SendspinClientListener,
                                   public SendspinNetworkProvider,
                                   public MetadataRoleListener,
                                   public ArtworkRoleListener {
public:
    using State = PlaybackRecoveryState::State;
    NativePlaybackEngine(
        std::string client_id,
        std::string player_name,
        uint8_t initial_volume,
        bool initial_muted);
    ~NativePlaybackEngine();

    enum class Failure : int32_t {
        None,
        NetworkUnavailable,
        TransportLost,
        HandshakeTimeout,
        OutputError,
        RouteChange,
        FocusResume,
        OutputRestartFailed,
    };

    struct Diagnostics {
        State state{State::Stopped};
        uint32_t generation{0};
        uint32_t queued_frames{0};
        uint32_t fifo_capacity_frames{PcmRenderFifo::kBlockFrames * PcmRenderFifo::kBlockCount};
        int64_t output_latency_us{-1};
        uint64_t underruns{0};
        uint64_t output_restarts{0};
        uint64_t hard_resyncs{0};
        uint64_t reconnect_attempts{0};
        uint64_t reconnect_completions{0};
        int64_t round_trip_us{-1};
        int64_t clock_offset_us{-1};
        int64_t clock_drift_ppm{-1};
        int64_t clock_error_us{-1};
        uint32_t clock_samples{0};
        bool clock_converged{false};
        Failure last_failure{Failure::None};
        bool output_stream_open{false};
        int32_t output_stream_state{-1};
        int32_t output_sample_rate{-1};
        int32_t output_channel_count{-1};
        int32_t output_format{-1};
        int32_t output_performance_mode{-1};
        int32_t output_sharing_mode{-1};
        int32_t output_device_id{-1};
        int32_t output_session_id{-1};
        int32_t output_frames_per_burst{-1};
        int32_t output_buffer_size_frames{-1};
        int32_t output_buffer_capacity_frames{-1};
        int32_t output_xrun_count{-1};
        uint8_t player_volume{100};
        bool player_muted{false};
    };

    bool connect(std::string url);
    void disconnect();
    void request_recovery(
        PlaybackRecoveryState::RecoveryCause cause =
            PlaybackRecoveryState::RecoveryCause::OutputError);
    void set_network_available(bool available);
    void suspend_for_focus();
    void resume_from_focus();
    [[nodiscard]] State state() const;
    [[nodiscard]] Diagnostics diagnostics() const;
    [[nodiscard]] std::optional<NowPlayingState::Snapshot> now_playing_after(
        uint64_t known_revision) const;
    [[nodiscard]] std::optional<ArtworkState::Snapshot> artwork_after(
        uint64_t known_revision) const;
    bool is_network_ready() override;
    void on_time_sync_updated(float) override;
    void on_group_update(const GroupUpdateObject&) override;
    void on_metadata(const ServerMetadataStateObject& metadata) override;
    void on_metadata_clear() override;
    void on_image_decode(uint8_t slot, const uint8_t* data, size_t length,
                         SendspinImageFormat format) override;
    void on_image_display(uint8_t slot, uint32_t lateness_ms) override;
    void on_image_clear(uint8_t slot) override;

private:
    static void on_frames_played(void* context, uint32_t frames);
    static void on_stream_started(void* context);
    void publish_state();
    void refresh_output_diagnostics();
    void refresh_output_diagnostics_locked();
    void record_failure(Failure failure);
    void record_hard_resync();
    void record_output_restart();
    void record_reconnect_attempt();
    void record_reconnect_completion();
    void drain_playback_feedback();
    void run();

    OboePcmOutput output_;
    SendspinPcmListener listener_;
    SendspinClient client_;
    PlayerRole& player_;
    MetadataRole& metadata_;
    ArtworkRole& artwork_;
    NowPlayingState now_playing_;
    ArtworkState artwork_state_;
    PlaybackRecoveryState recovery_state_;
    ReconnectPolicy reconnect_policy_;
    std::atomic<State> state_{State::Stopped};
    std::atomic<bool> running_{false};
    std::atomic<bool> network_available_{true};
    mutable std::mutex diagnostics_mutex_;
    Diagnostics diagnostics_;
    bool reconnect_attempt_active_{false};
    std::mutex control_mutex_;
    std::string pending_url_;
    bool disconnect_requested_{false};
    std::atomic<bool> focus_suspended_{false};
    std::atomic<bool> buffering_requested_{false};
    std::atomic<bool> playing_requested_{false};
    std::atomic<uint64_t> pending_audio_played_frames_{0};
    std::thread loop_thread_;
};

}  // namespace sendspin
