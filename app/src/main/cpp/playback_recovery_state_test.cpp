#include <cassert>

#include "playback_recovery_state.h"

namespace {

using State = sendspin::PlaybackRecoveryState::State;
using RecoveryCause = sendspin::PlaybackRecoveryState::RecoveryCause;

void reaches_playback_in_order() {
    sendspin::PlaybackRecoveryState recovery;
    recovery.connect();
    recovery.synchronising();
    recovery.ready();
    recovery.buffering();
    recovery.playing();
    assert(recovery.state() == State::Playing);
}

void stop_wins_over_a_queued_reconnect() {
    sendspin::PlaybackRecoveryState recovery;
    recovery.connect();
    recovery.synchronising();
    recovery.ready();
    assert(recovery.begin_recovery());
    recovery.request_recovery(RecoveryCause::RouteChange);
    recovery.stop();
    assert(recovery.take_recovery_causes() == 0);
    assert(!recovery.reconnect());
    assert(recovery.state() == State::Stopped);
}

void recovery_causes_are_coalesced() {
    sendspin::PlaybackRecoveryState recovery;
    recovery.request_recovery(RecoveryCause::OutputError);
    recovery.request_recovery(RecoveryCause::RouteChange);
    recovery.request_recovery(RecoveryCause::OutputError);

    const auto causes = recovery.take_recovery_causes();
    assert(causes == (static_cast<uint32_t>(RecoveryCause::OutputError) |
                      static_cast<uint32_t>(RecoveryCause::RouteChange)));
    assert(recovery.take_recovery_causes() == 0);
}

void focus_suspension_invalidates_active_and_connecting_output() {
    sendspin::PlaybackRecoveryState recovery;
    recovery.connect();
    assert(recovery.suspend_for_focus());
    assert(recovery.state() == State::Recovering);
    assert(recovery.recovery_generation() == 1);
    assert(recovery.suspend_for_focus());
    assert(recovery.recovery_generation() == 1);
}

void only_active_playback_can_recover() {
    sendspin::PlaybackRecoveryState recovery;
    assert(!recovery.begin_recovery());
    recovery.connect();
    assert(!recovery.begin_recovery());
    recovery.synchronising();
    recovery.ready();
    assert(recovery.begin_recovery());
    assert(recovery.state() == State::Recovering);
    assert(recovery.recovery_generation() == 1);
    assert(recovery.reconnect());
    recovery.synchronising();
    recovery.ready();
    assert(recovery.begin_recovery());
    assert(recovery.recovery_generation() == 2);
}

}  // namespace

int main() {
    reaches_playback_in_order();
    stop_wins_over_a_queued_reconnect();
    recovery_causes_are_coalesced();
    focus_suspension_invalidates_active_and_connecting_output();
    only_active_playback_can_recover();
}
