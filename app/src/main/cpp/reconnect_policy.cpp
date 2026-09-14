#include "reconnect_policy.h"

#include <algorithm>
#include <atomic>
#include <limits>
#include <utility>

namespace sendspin {

ReconnectPolicy::ReconnectPolicy() : ReconnectPolicy(Config{}, {}) {}

ReconnectPolicy::ReconnectPolicy(Config config, JitterSource jitter_source)
    : config_(config), jitter_source_(std::move(jitter_source)) {
    config_.initial_delay_us = std::max<TimeUs>(0, config_.initial_delay_us);
    config_.maximum_delay_us = std::max(config_.initial_delay_us, config_.maximum_delay_us);
    config_.maximum_jitter_us = std::max<TimeUs>(0, config_.maximum_jitter_us);
    config_.attempt_timeout_us = std::max<TimeUs>(0, config_.attempt_timeout_us);
    if (!jitter_source_) {
        jitter_source_ = [](const TimeUs maximum_abs_us) {
            static std::atomic<uint64_t> state{0x9E3779B97F4A7C15ULL};
            const uint64_t next = state.fetch_add(0x9E3779B97F4A7C15ULL,
                                                  std::memory_order_relaxed);
            const uint64_t span = static_cast<uint64_t>(maximum_abs_us) * 2 + 1;
            return static_cast<TimeUs>(next % span) - maximum_abs_us;
        };
    }
}

void ReconnectPolicy::request(const TimeUs now_us) {
    if (status_ != Status::Idle) return;
    status_ = Status::Scheduled;
    next_attempt_us_ = now_us;
}

bool ReconnectPolicy::take_due_attempt(const TimeUs now_us) {
    if (status_ != Status::Scheduled || now_us < next_attempt_us_) return false;
    status_ = Status::InFlight;
    attempt_started_us_ = now_us;
    return true;
}

bool ReconnectPolicy::attempt_timed_out(const TimeUs now_us) const {
    return status_ == Status::InFlight && now_us >= attempt_started_us_ &&
           now_us - attempt_started_us_ >= config_.attempt_timeout_us;
}

void ReconnectPolicy::record_failure(const TimeUs now_us) {
    if (status_ == Status::Idle) return;
    if (failure_count_ != std::numeric_limits<uint32_t>::max()) ++failure_count_;
    status_ = Status::Scheduled;
    next_attempt_us_ = now_us + retry_delay_us();
}

void ReconnectPolicy::record_convergence() {
    status_ = Status::Idle;
    failure_count_ = 0;
    next_attempt_us_ = 0;
    attempt_started_us_ = 0;
}

void ReconnectPolicy::cancel() {
    record_convergence();
}

ReconnectPolicy::TimeUs ReconnectPolicy::retry_delay_us() const {
    TimeUs base_delay = config_.initial_delay_us;
    for (uint32_t index = 1; index < failure_count_; ++index) {
        if (base_delay >= config_.maximum_delay_us - base_delay) {
            base_delay = config_.maximum_delay_us;
            break;
        }
        base_delay *= 2;
    }

    base_delay = std::min(base_delay, config_.maximum_delay_us);
    TimeUs jitter = 0;
    if (jitter_source_ && config_.maximum_jitter_us > 0) {
        jitter = clamp(jitter_source_(config_.maximum_jitter_us),
                       -config_.maximum_jitter_us,
                       config_.maximum_jitter_us);
    }
    return clamp(base_delay + jitter, 0, config_.maximum_delay_us);
}

ReconnectPolicy::TimeUs ReconnectPolicy::clamp(
    const TimeUs value,
    const TimeUs minimum,
    const TimeUs maximum) {
    return std::min(std::max(value, minimum), maximum);
}

}  // namespace sendspin