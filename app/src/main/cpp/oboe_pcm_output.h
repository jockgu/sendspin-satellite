#pragma once

#include <atomic>
#include <cstdint>
#include <memory>

#include <oboe/Oboe.h>

#include "pcm_render_fifo.h"

namespace sendspin {

class OboePcmOutput final : public oboe::AudioStreamDataCallback {
public:
    using PlaybackObserver = void (*)(void*, uint32_t);
    OboePcmOutput() = default;
    ~OboePcmOutput();

    bool start();
    void stop();
    uint32_t write(const int16_t* samples, uint32_t frames);
    void clear();
    void set_playback_observer(PlaybackObserver observer, void* context);
    [[nodiscard]] uint32_t queued_frames() const;
    [[nodiscard]] uint32_t take_underruns();

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audio_data,
        int32_t num_frames) override;

private:
    PcmRenderFifo fifo_;
    std::shared_ptr<oboe::AudioStream> stream_;
    PlaybackObserver playback_observer_{nullptr};
    void* playback_observer_context_{nullptr};
    std::atomic<uint32_t> underruns_{0};
};

}  // namespace sendspin
