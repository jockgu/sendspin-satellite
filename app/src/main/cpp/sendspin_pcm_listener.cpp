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
}

}  // namespace sendspin
