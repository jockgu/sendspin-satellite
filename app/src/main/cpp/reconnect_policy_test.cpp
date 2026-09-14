#include <cassert>
#include <cstdint>

#include "reconnect_policy.h"

namespace {

using sendspin::ReconnectPolicy;

void first_attempt_is_immediate_and_only_scheduled_once() {
    ReconnectPolicy policy;

    policy.request(5'000);
    policy.request(9'000);

    assert(policy.pending());
    assert(policy.next_attempt_us() == 5'000);
    assert(policy.take_due_attempt(5'000));
    assert(policy.attempt_in_flight());
    assert(!policy.take_due_attempt(5'001));
    assert(!policy.attempt_timed_out(5'000'000));
}

void failures_back_off_and_cap() {
    ReconnectPolicy::Config config;
    config.initial_delay_us = 1'000;
    config.maximum_delay_us = 8'000;
    config.maximum_jitter_us = 0;
    ReconnectPolicy policy(config);

    policy.request(0);
    assert(policy.take_due_attempt(0));
    policy.record_failure(0);
    assert(policy.next_attempt_us() == 1'000);
    assert(policy.take_due_attempt(1'000));
    policy.record_failure(1'000);
    assert(policy.next_attempt_us() == 3'000);
    assert(policy.take_due_attempt(3'000));
    policy.record_failure(3'000);
    assert(policy.next_attempt_us() == 7'000);
    assert(policy.take_due_attempt(7'000));
    policy.record_failure(7'000);
    assert(policy.next_attempt_us() == 15'000);
    assert(policy.take_due_attempt(15'000));
    policy.record_failure(15'000);
    assert(policy.next_attempt_us() == 23'000);
}

void jitter_is_bounded_and_success_resets_the_policy() {
    ReconnectPolicy::Config config;
    config.initial_delay_us = 1'000;
    config.maximum_delay_us = 2'000;
    config.maximum_jitter_us = 100;
    ReconnectPolicy policy(config, [](const ReconnectPolicy::TimeUs) {
        return ReconnectPolicy::TimeUs{10'000};
    });

    policy.request(0);
    assert(policy.take_due_attempt(0));
    policy.record_failure(0);
    assert(policy.next_attempt_us() == 1'100);
    assert(policy.failure_count() == 1);

    policy.record_convergence();
    assert(!policy.pending());
    assert(policy.failure_count() == 0);
    policy.request(4'000);
    assert(policy.next_attempt_us() == 4'000);
}

void an_attempt_times_out_without_a_transport_callback() {
    ReconnectPolicy::Config config;
    config.initial_delay_us = 1'000;
    config.maximum_jitter_us = 0;
    config.attempt_timeout_us = 2'000;
    ReconnectPolicy policy(config);

    policy.request(100);
    assert(policy.take_due_attempt(100));
    assert(!policy.attempt_timed_out(2'099));
    assert(policy.attempt_timed_out(2'100));
    policy.record_failure(2'100);
    assert(policy.next_attempt_us() == 3'100);
}

void cancellation_wins_over_a_pending_retry() {
    ReconnectPolicy::Config config;
    config.initial_delay_us = 1'000;
    config.maximum_jitter_us = 0;
    ReconnectPolicy policy(config);

    policy.request(0);
    assert(policy.take_due_attempt(0));
    policy.record_failure(0);
    policy.cancel();

    assert(!policy.pending());
    assert(!policy.take_due_attempt(1'000'000));
    assert(policy.failure_count() == 0);
}

}  // namespace

int main() {
    first_attempt_is_immediate_and_only_scheduled_once();
    failures_back_off_and_cap();
    jitter_is_bounded_and_success_resets_the_policy();
    an_attempt_times_out_without_a_transport_callback();
    cancellation_wins_over_a_pending_retry();
}