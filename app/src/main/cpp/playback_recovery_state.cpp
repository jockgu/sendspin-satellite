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
}

void PlaybackRecoveryState::fail() {
    state_ = State::Error;
}

}  // namespace sendspin
