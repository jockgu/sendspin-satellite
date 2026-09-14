#pragma once

#include <cstdint>
#include <functional>

namespace sendspin {

class ReconnectPolicy final {
public:
    using TimeUs = int64_t;
    using JitterSource = std::function<TimeUs(TimeUs maximum_abs_us)>;

    struct Config {
        TimeUs initial_delay_us{1'000'000};
        TimeUs maximum_delay_us{30'000'000};
        TimeUs maximum_jitter_us{250'000};
        TimeUs attempt_timeout_us{5'000'000};
    };

    ReconnectPolicy();
    explicit ReconnectPolicy(Config config, JitterSource jitter_source = {});

    void request(TimeUs now_us);
    [[nodiscard]] bool take_due_attempt(TimeUs now_us);
    [[nodiscard]] bool attempt_timed_out(TimeUs now_us) const;
    void record_failure(TimeUs now_us);
    void record_convergence();
    void cancel();

    [[nodiscard]] bool pending() const { return status_ != Status::Idle; }
    [[nodiscard]] bool attempt_in_flight() const { return status_ == Status::InFlight; }
    [[nodiscard]] uint32_t failure_count() const { return failure_count_; }
    [[nodiscard]] TimeUs next_attempt_us() const { return next_attempt_us_; }

private:
    enum class Status {
        Idle,
        Scheduled,
        InFlight,
    };

    [[nodiscard]] TimeUs retry_delay_us() const;
    [[nodiscard]] static TimeUs clamp(TimeUs value, TimeUs minimum, TimeUs maximum);

    Config config_;
    JitterSource jitter_source_;
    Status status_{Status::Idle};
    uint32_t failure_count_{0};
    TimeUs next_attempt_us_{0};
    TimeUs attempt_started_us_{0};
};

}  // namespace sendspin