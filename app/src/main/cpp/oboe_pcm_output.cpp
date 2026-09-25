#include "oboe_pcm_output.h"

namespace sendspin {

OboePcmOutput::~OboePcmOutput() {
    stop();
}

bool OboePcmOutput::start() {
    if (stream_) return true;

    stream_closed_by_oboe_.store(false, std::memory_order_release);
    error_recovery_requested_.store(false, std::memory_order_release);
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Shared);
    builder.setFormat(oboe::AudioFormat::I16);
    builder.setChannelCount(PcmRenderFifo::kChannels);
    builder.setSampleRate(PcmRenderFifo::kSampleRate);
    builder.setDataCallback(this);
    builder.setErrorCallback(this);
    if (builder.openStream(stream_) != oboe::Result::OK || !stream_ ||
        stream_->requestStart() != oboe::Result::OK) {
        stop();
        return false;
    }
    return true;
}

void OboePcmOutput::stop() {
    clear();
    if (!stream_) return;
    if (!stream_closed_by_oboe_.exchange(false, std::memory_order_acq_rel)) {
        stream_->stop();
        stream_->close();
    }
    stream_.reset();
}

bool OboePcmOutput::restart() {
    stop();
    return start();
}

uint32_t OboePcmOutput::write(const int16_t* samples, const uint32_t frames) {
    return fifo_.write(samples, frames);
}

void OboePcmOutput::clear() {
    fifo_.clear();
}

void OboePcmOutput::set_volume_state(const uint8_t volume, const bool muted) {
    volume_.set_state(volume, muted);
}

void OboePcmOutput::set_playback_observer(PlaybackObserver observer, void* context) {
    playback_observer_ = observer;
    playback_observer_context_ = context;
}

uint32_t OboePcmOutput::queued_frames() const {
    return fifo_.queued_frames();
}

uint64_t OboePcmOutput::underruns() const {
    return underruns_.load(std::memory_order_acquire);
}

int64_t OboePcmOutput::latency_us() const {
    if (!stream_) return -1;
    const auto result = stream_->calculateLatencyMillis();
    if (static_cast<oboe::Result>(result) != oboe::Result::OK) return -1;
    return static_cast<int64_t>(result.value() * 1'000.0);
}

OboePcmOutput::StreamDiagnostics OboePcmOutput::diagnostics() const {
    StreamDiagnostics result;
    if (!stream_) return result;

    result.open = true;
    result.state = static_cast<int32_t>(stream_->getState());
    result.sample_rate = stream_->getSampleRate();
    result.channel_count = stream_->getChannelCount();
    result.format = static_cast<int32_t>(stream_->getFormat());
    result.performance_mode = static_cast<int32_t>(stream_->getPerformanceMode());
    result.sharing_mode = static_cast<int32_t>(stream_->getSharingMode());
    result.device_id = stream_->getDeviceId();
    result.session_id = static_cast<int32_t>(stream_->getSessionId());
    result.frames_per_burst = stream_->getFramesPerBurst();
    result.buffer_size_frames = stream_->getBufferSizeInFrames();
    result.buffer_capacity_frames = stream_->getBufferCapacityInFrames();
    const auto xruns = stream_->getXRunCount();
    if (static_cast<oboe::Result>(xruns) == oboe::Result::OK) {
        result.xrun_count = xruns.value();
    }
    return result;
}

bool OboePcmOutput::take_error_recovery_request() {
    return error_recovery_requested_.exchange(false, std::memory_order_acq_rel);
}

oboe::DataCallbackResult OboePcmOutput::onAudioReady(
    oboe::AudioStream*,
    void* audio_data,
    const int32_t num_frames) {
    const auto result = fifo_.pull(static_cast<int16_t*>(audio_data), num_frames);
    volume_.process(
        static_cast<int16_t*>(audio_data),
        static_cast<uint32_t>(num_frames),
        PcmRenderFifo::kChannels);
    if (result.underrun) {
        underruns_.fetch_add(1, std::memory_order_relaxed);
    }
    if (result.frames_from_fifo != 0 && playback_observer_ != nullptr) {
        playback_observer_(playback_observer_context_, result.frames_from_fifo);
    }
    return oboe::DataCallbackResult::Continue;
}

bool OboePcmOutput::onError(oboe::AudioStream*, oboe::Result) {
    return false;
}

void OboePcmOutput::onErrorAfterClose(oboe::AudioStream*, oboe::Result) {
    stream_closed_by_oboe_.store(true, std::memory_order_release);
    error_recovery_requested_.store(true, std::memory_order_release);
}

}  // namespace sendspin
