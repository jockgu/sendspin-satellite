#include "playback_recovery_state.h"

namespace sendspin {

void PlaybackRecoveryState::connect() {
    state_ = State::Connecting;
}

void PlaybackRecoveryState::synchronising() {
    if (state_ == State::Connecting) state_ = State::Synchronising;
}

void PlaybackRecoveryState::ready() {
    if (state_ == State::Synchronising) state_ = State::Ready;
}

void PlaybackRecoveryState::buffering() {
    if (state_ == State::Ready || state_ == State::Playing) state_ = State::Buffering;
}

void PlaybackRecoveryState::playing() {
    if (state_ == State::Buffering) state_ = State::Playing;
}

void PlaybackRecoveryState::request_recovery(const RecoveryCause cause) {
    pending_recovery_causes_.fetch_or(static_cast<RecoveryCauseMask>(cause),
                                      std::memory_order_release);
}

PlaybackRecoveryState::RecoveryCauseMask PlaybackRecoveryState::take_recovery_causes() {
    return pending_recovery_causes_.exchange(0, std::memory_order_acq_rel);
}

bool PlaybackRecoveryState::suspend_for_focus() {
    switch (state_) {
        case State::Connecting:
        case State::Synchronising:
        case State::Ready:
        case State::Buffering:
        case State::Playing:
            state_ = State::Recovering;
            ++recovery_generation_;
            return true;
        case State::Recovering:
            return true;
        default:
            return false;
    }
}

bool PlaybackRecoveryState::begin_recovery() {
    switch (state_) {
        case State::Ready:
        case State::Buffering:
        case State::Playing:
            state_ = State::Recovering;
            ++recovery_generation_;
            return true;
        default:
            return false;
    }
}

bool PlaybackRecoveryState::reconnect() {
    if (state_ != State::Recovering) return false;
    state_ = State::Connecting;
    return true;
}

void PlaybackRecoveryState::stop() {
    state_ = State::Stopped;
    pending_recovery_causes_.store(0, std::memory_order_release);
}

void PlaybackRecoveryState::fail() {
    state_ = State::Error;
}

}  // namespace sendspin
