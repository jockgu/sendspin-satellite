#include <array>
#include <algorithm>
#include <cassert>
#include <cstdint>
#include <cstdlib>

#include "pcm_render_fifo.h"
#include "playback_recovery_state.h"
#include "reconnect_policy.h"

namespace {

using RecoveryCause = sendspin::PlaybackRecoveryState::RecoveryCause;
using RecoveryState = sendspin::PlaybackRecoveryState;
using State = RecoveryState::State;
using TimeUs = sendspin::ReconnectPolicy::TimeUs;

constexpr uint32_t kMinutesPerReleaseSoak = 24 * 60;
constexpr TimeUs kMinuteUs = 60'000'000;

void enter_playing(RecoveryState& recovery) {
    recovery.connect();
    recovery.synchronising();
    recovery.ready();
    recovery.buffering();
    recovery.playing();
    assert(recovery.state() == State::Playing);
}

void simulate_network_recovery(
    RecoveryState& recovery,
    sendspin::PcmRenderFifo& fifo,
    sendspin::ReconnectPolicy& policy,
    TimeUs& now_us) {
    const int16_t old_sample = 1;
    const int16_t new_sample = static_cast<int16_t>(now_us / kMinuteUs + 2);
    const std::array<int16_t, 4> old_audio{old_sample, old_sample, old_sample, old_sample};
    const std::array<int16_t, 4> new_audio{new_sample, new_sample, new_sample, new_sample};
    std::array<int16_t, 4> rendered{};

    assert(fifo.write(old_audio.data(), 2) == 2);
    recovery.request_recovery(RecoveryCause::NetworkLost);
    assert(recovery.take_recovery_causes() != 0);
    assert(recovery.begin_recovery());
    fifo.clear();
    policy.request(now_us);

    assert(fifo.write(new_audio.data(), 2) == 2);
    const auto rendered_result = fifo.pull(rendered.data(), 2);
    assert(!rendered_result.underrun);
    assert(rendered == new_audio);

    for (uint32_t failure = 0; failure < 4; ++failure) {
        assert(policy.take_due_attempt(now_us));
        policy.record_failure(now_us);
        const TimeUs retry_time = policy.next_attempt_us();
        assert(!policy.take_due_attempt(retry_time - 1));
        now_us = retry_time;
    }

    assert(policy.take_due_attempt(now_us));
    assert(recovery.reconnect());
    recovery.synchronising();
    recovery.ready();
    recovery.buffering();
    recovery.playing();
    policy.record_convergence();
    assert(recovery.state() == State::Playing);
    assert(!policy.pending());
}

void assert_fifo_stays_bounded(sendspin::PcmRenderFifo& fifo) {
    constexpr uint32_t capacity =
        sendspin::PcmRenderFifo::kBlockFrames * sendspin::PcmRenderFifo::kBlockCount;
    std::array<int16_t, sendspin::PcmRenderFifo::kBlockFrames * sendspin::PcmRenderFifo::kChannels>
        block{};
    for (uint32_t block_index = 0; block_index < sendspin::PcmRenderFifo::kBlockCount;
         ++block_index) {
        assert(fifo.write(block.data(), sendspin::PcmRenderFifo::kBlockFrames) ==
               sendspin::PcmRenderFifo::kBlockFrames);
        assert(fifo.queued_frames() <= capacity);
    }
    assert(fifo.write(block.data(), sendspin::PcmRenderFifo::kBlockFrames) == 0);

    std::array<int16_t, sendspin::PcmRenderFifo::kBlockFrames * sendspin::PcmRenderFifo::kChannels>
        output{};
    for (uint32_t block_index = 0; block_index < sendspin::PcmRenderFifo::kBlockCount;
         ++block_index) {
        const auto result = fifo.pull(output.data(), sendspin::PcmRenderFifo::kBlockFrames);
        assert(!result.underrun);
    }
    assert(fifo.queued_frames() == 0);
}

void run_soak(uint32_t virtual_minutes) {
    assert(virtual_minutes > 0);
    sendspin::ReconnectPolicy::Config policy_config;
    policy_config.maximum_jitter_us = 0;
    sendspin::ReconnectPolicy policy(policy_config);
    RecoveryState recovery;
    sendspin::PcmRenderFifo fifo;
    enter_playing(recovery);

    const uint32_t fault_interval = std::max<uint32_t>(1, virtual_minutes / 24);
    TimeUs now_us = 0;
    TimeUs wall_clock_us = 0;
    uint64_t recoveries = 0;
    for (uint32_t minute = 0; minute < virtual_minutes; ++minute) {
        wall_clock_us = static_cast<TimeUs>(minute) * kMinuteUs;
        if (now_us < wall_clock_us) now_us = wall_clock_us;
        if (minute % fault_interval == 0) {
            assert_fifo_stays_bounded(fifo);
            simulate_network_recovery(recovery, fifo, policy, now_us);
            ++recoveries;
            assert(recovery.recovery_generation() == recoveries);
        }
    }
    assert(recoveries > 0);
    assert(wall_clock_us == static_cast<TimeUs>(virtual_minutes - 1) * kMinuteUs);
}

}  // namespace

int main(int argc, char** argv) {
    uint32_t virtual_minutes = kMinutesPerReleaseSoak;
    if (argc == 2) {
        virtual_minutes = static_cast<uint32_t>(std::strtoul(argv[1], nullptr, 10));
    }
    run_soak(virtual_minutes);
}