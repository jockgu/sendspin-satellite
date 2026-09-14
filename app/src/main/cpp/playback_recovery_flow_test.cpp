#include <array>
#include <cassert>
#include <cstdint>

#include "pcm_render_fifo.h"
#include "playback_recovery_state.h"

namespace {

using RecoveryCause = sendspin::PlaybackRecoveryState::RecoveryCause;
using RecoveryState = sendspin::PlaybackRecoveryState;
using State = RecoveryState::State;

class DeterministicOutput final {
public:
    bool start() {
        started_ = can_start;
        return started_;
    }

    void stop() {
        started_ = false;
        fifo_.clear();
    }

    bool restart() {
        ++restart_count_;
        stop();
        return start();
    }

    uint32_t write(const int16_t* samples, uint32_t frames) {
        assert(started_);
        return fifo_.write(samples, frames);
    }

    sendspin::PcmRenderFifo::PullResult pull(int16_t* samples, uint32_t frames) {
        return fifo_.pull(samples, frames);
    }

    [[nodiscard]] uint32_t queued_frames() const { return fifo_.queued_frames(); }
    [[nodiscard]] uint32_t restart_count() const { return restart_count_; }

    bool can_start{true};

private:
    sendspin::PcmRenderFifo fifo_;
    bool started_{false};
    uint32_t restart_count_{0};
};

void enter_playing(RecoveryState& recovery) {
    recovery.connect();
    recovery.synchronising();
    recovery.ready();
    recovery.buffering();
    recovery.playing();
    assert(recovery.state() == State::Playing);
}

void output_error_recovery_discards_queued_pcm() {
    RecoveryState recovery;
    DeterministicOutput output;
    const std::array<int16_t, 4> old_audio{1, 1, 1, 1};
    const std::array<int16_t, 4> new_audio{2, 2, 2, 2};
    std::array<int16_t, 2> rendered{};

    assert(output.start());
    assert(output.write(old_audio.data(), 2) == 2);
    enter_playing(recovery);
    recovery.request_recovery(RecoveryCause::OutputError);

    assert(recovery.take_recovery_causes() != 0);
    assert(recovery.begin_recovery());
    assert(output.restart());
    assert(output.queued_frames() == 0);
    assert(recovery.reconnect());

    assert(output.write(new_audio.data(), 2) == 2);
    const auto result = output.pull(rendered.data(), 2);
    assert(!result.underrun);
    assert((rendered == std::array<int16_t, 2>{2, 2}));
    assert(output.restart_count() == 1);
}

void focus_loss_and_gain_perform_one_fresh_recovery() {
    RecoveryState recovery;
    DeterministicOutput output;
    const std::array<int16_t, 2> old_audio{1, 1};
    const std::array<int16_t, 2> new_audio{2, 2};
    std::array<int16_t, 2> rendered{};

    assert(output.start());
    assert(output.write(old_audio.data(), 1) == 1);
    enter_playing(recovery);
    assert(recovery.suspend_for_focus());
    output.stop();
    assert(recovery.state() == State::Recovering);
    assert(recovery.recovery_generation() == 1);
    assert(output.queued_frames() == 0);
    assert(recovery.suspend_for_focus());
    assert(recovery.recovery_generation() == 1);

    recovery.request_recovery(RecoveryCause::FocusResume);
    assert(recovery.take_recovery_causes() != 0);
    assert(output.restart());
    assert(recovery.reconnect());
    assert(output.write(new_audio.data(), 1) == 1);
    const auto result = output.pull(rendered.data(), 1);
    assert(!result.underrun);
    assert(rendered[0] == 2);
    assert(output.restart_count() == 1);
}

void duplicate_recovery_requests_have_one_restart() {
    RecoveryState recovery;
    DeterministicOutput output;
    enter_playing(recovery);
    recovery.request_recovery(RecoveryCause::OutputError);
    recovery.request_recovery(RecoveryCause::RouteChange);

    assert(recovery.take_recovery_causes() ==
           (static_cast<uint32_t>(RecoveryCause::OutputError) |
            static_cast<uint32_t>(RecoveryCause::RouteChange)));
    assert(recovery.begin_recovery());
    assert(output.restart());
    assert(recovery.reconnect());
    assert(recovery.take_recovery_causes() == 0);
    assert(output.restart_count() == 1);
}

void failed_output_restart_is_terminal() {
    RecoveryState recovery;
    DeterministicOutput output;
    enter_playing(recovery);
    recovery.request_recovery(RecoveryCause::OutputError);
    assert(recovery.take_recovery_causes() != 0);
    assert(recovery.begin_recovery());

    output.can_start = false;
    assert(!output.restart());
    recovery.fail();
    assert(recovery.state() == State::Error);
    assert(!recovery.reconnect());
}

void stop_cancels_recovery_before_output_restart() {
    RecoveryState recovery;
    DeterministicOutput output;
    enter_playing(recovery);
    recovery.request_recovery(RecoveryCause::RouteChange);
    recovery.stop();

    assert(recovery.take_recovery_causes() == 0);
    assert(!recovery.begin_recovery());
    assert(output.restart_count() == 0);
    assert(recovery.state() == State::Stopped);
}

}  // namespace

int main() {
    output_error_recovery_discards_queued_pcm();
    focus_loss_and_gain_perform_one_fresh_recovery();
    duplicate_recovery_requests_have_one_restart();
    failed_output_restart_is_terminal();
    stop_cancels_recovery_before_output_restart();
}