#include "sendspin_pcm_listener.h"

namespace sendspin {

size_t SendspinPcmListener::on_audio_write(uint8_t* data, const size_t length, uint32_t) {
    constexpr size_t kBytesPerFrame = PcmRenderFifo::kChannels * sizeof(int16_t);
    if (length % kBytesPerFrame != 0) return 0;

    const auto frames = static_cast<uint32_t>(length / kBytesPerFrame);
    return static_cast<size_t>(output_.write(reinterpret_cast<const int16_t*>(data), frames)) *
           kBytesPerFrame;
}

void SendspinPcmListener::on_stream_clear() {
    output_.clear();
    if (stream_observer_ != nullptr) stream_observer_(stream_observer_context_);
}

void SendspinPcmListener::on_stream_start() {
    if (stream_observer_ != nullptr) stream_observer_(stream_observer_context_);
}

void SendspinPcmListener::on_volume_changed(const uint8_t volume) {
    volume_ = volume > 100 ? 100 : volume;
    apply_volume_state();
}

void SendspinPcmListener::on_mute_changed(const bool muted) {
    muted_ = muted;
    apply_volume_state();
}

void SendspinPcmListener::set_volume_state(const uint8_t volume, const bool muted) {
    volume_ = volume > 100 ? 100 : volume;
    muted_ = muted;
    apply_volume_state();
}

void SendspinPcmListener::apply_volume_state() {
    output_.set_volume_state(volume_, muted_);
}

}  // namespace sendspin
