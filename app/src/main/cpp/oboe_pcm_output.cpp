#include "oboe_pcm_output.h"

namespace sendspin {

OboePcmOutput::~OboePcmOutput() {
    stop();
}

bool OboePcmOutput::start() {
    if (stream_) return true;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Shared);
    builder.setFormat(oboe::AudioFormat::I16);
    builder.setChannelCount(PcmRenderFifo::kChannels);
    builder.setSampleRate(PcmRenderFifo::kSampleRate);
    builder.setDataCallback(this);
    if (builder.openStream(stream_) != oboe::Result::OK || !stream_ ||
        stream_->requestStart() != oboe::Result::OK) {
        stop();
        return false;
    }
    return true;
}

void OboePcmOutput::stop() {
    if (!stream_) return;
    stream_->stop();
    stream_->close();
    stream_.reset();
    clear();
}

uint32_t OboePcmOutput::write(const int16_t* samples, const uint32_t frames) {
    return fifo_.write(samples, frames);
}

void OboePcmOutput::clear() {
    fifo_.clear();
}

void OboePcmOutput::set_playback_observer(PlaybackObserver observer, void* context) {
    playback_observer_ = observer;
    playback_observer_context_ = context;
}

uint32_t OboePcmOutput::queued_frames() const {
    return fifo_.queued_frames();
}

uint32_t OboePcmOutput::take_underruns() {
    return underruns_.exchange(0, std::memory_order_acq_rel);
}

oboe::DataCallbackResult OboePcmOutput::onAudioReady(
    oboe::AudioStream*,
    void* audio_data,
    const int32_t num_frames) {
    const auto result = fifo_.pull(static_cast<int16_t*>(audio_data), num_frames);
    if (result.underrun) {
        underruns_.fetch_add(1, std::memory_order_relaxed);
    }
    if (result.frames_from_fifo != 0 && playback_observer_ != nullptr) {
        playback_observer_(playback_observer_context_, result.frames_from_fifo);
    }
    return oboe::DataCallbackResult::Continue;
}

}  // namespace sendspin
