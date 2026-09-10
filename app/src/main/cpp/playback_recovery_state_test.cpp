#include <cassert>

#include "playback_recovery_state.h"

namespace {

using State = sendspin::PlaybackRecoveryState::State;

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
    recovery.stop();
    assert(!recovery.reconnect());
    assert(recovery.state() == State::Stopped);
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
    only_active_playback_can_recover();
}
