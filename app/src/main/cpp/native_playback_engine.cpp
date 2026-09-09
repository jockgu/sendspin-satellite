#include "native_playback_engine.h"

#include <chrono>

namespace sendspin {
namespace {
SendspinClientConfig client_config(const std::string& client_id) {
    SendspinClientConfig config;
    config.client_id = client_id;
    config.name = "Sendspin Satellite";
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

NativePlaybackEngine::NativePlaybackEngine(std::string client_id)
    : listener_(output_),
      client_(client_config(client_id)),
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
    state_.store(State::Disconnected, std::memory_order_release);
}
NativePlaybackEngine::State NativePlaybackEngine::state() const { return state_.load(); }
bool NativePlaybackEngine::is_network_ready() { return true; }
void NativePlaybackEngine::on_time_sync_updated(float) { state_.store(State::Ready); }
void NativePlaybackEngine::on_frames_played(void* context, uint32_t frames) {
    static_cast<NativePlaybackEngine*>(context)->player_.notify_audio_played(frames, monotonic_us());
}
void NativePlaybackEngine::run() {
    if (!client_.start_server()) {
        state_.store(State::Error);
        running_.store(false, std::memory_order_release);
        return;
    }
    while (running_.load(std::memory_order_acquire)) {
        std::string url;
        bool disconnect = false;
        {
            std::lock_guard lock(control_mutex_);
            url.swap(pending_url_);
            disconnect = disconnect_requested_;
            disconnect_requested_ = false;
        }
        if (disconnect) client_.disconnect(SendspinGoodbyeReason::USER_REQUEST);
        if (!url.empty()) client_.connect_to(url);
        client_.loop();
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
    client_.disconnect(SendspinGoodbyeReason::USER_REQUEST);
}
}  // namespace sendspin
