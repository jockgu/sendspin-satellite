# Sendspin Satellite implementation plan

This project should grow through small, verifiable vertical slices. Playback
reliability and timing correctness take priority over UI breadth and feature
count.

The intended end state is a thin Android endpoint: after user activation, the
foreground service keeps the configured player ready without the Activity
being open. This does not promise automatic audio restart after a device reboot
or user Force Stop; ordinary Android requires the user to activate it again.

## Phase 0 — application shell ✅

- [x] Compose application with a simple connection screen.
- [x] Explicit user-visible connection-state model.
- [x] Persist the configured server address.
- [x] Launch and verify the app on an Android emulator.
- [x] Establish the final initial application identity:
  `com.nanopixel.sendspinsatellite`.
- [x] Add focused unit tests for the connection state model.

## Phase 1 — Sendspin connection hello world

Goal: become a real, synchronized Sendspin client without attempting playback.

- [x] Confirm the current Sendspin protocol and authentication requirements from
  its canonical specification and reference implementation.
- [x] Define a small Kotlin transport/session boundary, leaving room to move
  transport native later without changing the UI contract.
- [x] Connect to a configured Sendspin server and perform the required client
  handshake.
- [x] Give the player a stable, human-readable device name.
- [x] Implement the `Disconnected → Connecting → Handshaking →
  Synchronising → Ready` state transitions using real protocol events.
- [x] Exchange the Sendspin time messages and surface basic diagnostics: round
  trip time, clock offset, and convergence state.
- [x] Do not advertise the player as playback-available: Phase 1 has no audio
  renderer. It does synchronize its clock and reports an unavailable player
  state after convergence.
- [x] Handle intentional disconnect and server connection loss cleanly.

**Acceptance:** The server lists the Android device as a connected,
synchronized (but intentionally unavailable) player. The app displays genuine
clock diagnostics, and returns to a truthful error/disconnected state when the
server is stopped. This still requires validation against a real Sendspin
server.

## Phase 2 — native clock boundary

Goal: establish the Kotlin/native ownership split before audio is involved.

- [x] Add the CMake/C++20 native module.
- [x] Implement the Sendspin clock filter natively with deterministic tests.
- [x] Add a small JNI facade for engine lifecycle, clock updates, and
  diagnostics only.
- [x] Drive the Kotlin synchronization UI from native diagnostics.

**Acceptance:** Clock convergence can be tested off-device and Kotlin does not
own timing calculations.

## Phase 3 — PCM playback vertical slice

Goal: play scheduled PCM correctly before adding codec complexity.

- [x] Use the official `sendspin-cpp` library for native transport, protocol,
  decoding, and clock scheduling; do not duplicate that data path here.
- [x] Add its minimal `PlayerRoleListener.on_stream_clear()` callback upstream,
  then pin the exact upstream commit until it is included in a release. The
  Android output calls its generation invalidation from this callback, so old
  PCM can never render after a seek or track jump.
- [x] Keep the Android-native code limited to Oboe output and the bounded,
  generation-tagged PCM FIFO.
- [x] Add Oboe audio output in the native engine.
- [x] Support PCM only.
- [x] Add bounded, generation-tagged PCM timeline and render FIFO buffers.
- [x] Implement `stream/start` and `stream/clear`; stale generations must never
  render.
- [x] Keep the audio callback allocation-free and non-blocking.
- [x] Recover from underrun by returning to buffering.

**Acceptance:** A controlled PCM stream plays continuously, starts on schedule,
and never leaks audio across a stream restart.

## Phase 4 — playback resilience

Goal: make playback independent of the Activity lifecycle.

- [x] Move session/engine ownership into a minimal foreground playback service;
  the Activity only observes and controls it.
- [x] Add a functional notification with status and a Stop action. Do not add
  MediaSession, artwork, or playback UI in this phase.
- [ ] Validate on a physical device: background, rotate, and remove the task
  while playing; the service remains in control and Stop ends it cleanly.

**Acceptance:** an explicitly connected PCM session outlives the Activity and
can be ended reliably from the app or notification.

## Phase 5 — audio interruption and output recovery

Goal: keep the output path truthful and recoverable through Android audio
events without performing recovery in the real-time callback.

- [ ] Add one explicit native recovery state machine; recovery always
  invalidates the active PCM generation and user Stop cancels retry.
- [ ] Handle audio focus, output route changes, and Oboe audio-device restart
  outside the real-time callback.
- [ ] Add deterministic tests for focus/output recovery and stream clears
  during a generation change.

**Acceptance:** temporary focus loss and route/device replacement produce a
clean re-buffer or user-visible stop, never stale audio or callback-thread
work.

## Phase 6 — network recovery, diagnostics, and soak hardening

Goal: recover from expected network/server failures and make long-running
behaviour measurable.

- [ ] Reconnect only after validated network availability, with bounded backoff,
  after temporary network loss and server restart.
- [ ] Define and implement process-death/restart behaviour only after persisted
  connection state can resume safely; do not accidentally restart after a user
  Stop.
- [ ] Expose one fixed diagnostics snapshot: buffers, underruns, output
  restarts, resyncs, clock state, and reconnect counters.
- [ ] Add deterministic tests for jitter, late packets, stream clears, clock
  drift, output restart, and reconnection.
- [ ] Add a long-running simulated playback/soak test with bounded queues and
  repeated recovery.

**Acceptance:** PCM playback recovers predictably from Wi-Fi loss and server
restart, remains measurable over long runs, and never resumes after a user
Stop.

Detailed implementation order, exclusions, and Phase 4–7 boundaries:
`PHASE_4_PLAN.md`.

## Phase 7 — Home Assistant server autodiscovery

Goal: make the normal one-server Home Assistant installation work without any
server setup, without weakening the proven manual connection and recovery path.

- [ ] Browse and resolve `_sendspin._tcp.` using Android's native `NsdManager`
  only while the service is active on a validated local network.
- [ ] Hand the resolved `ws://host:port/sendspin` URL to the existing Phase 6
  service; do not create a second connection path.
- [ ] Connect automatically when exactly one compatible server is found.
- [ ] Show a simple selector only when multiple compatible servers are found;
  do not build a server browser, catalogue, or management UI.
- [ ] Keep manual configuration as the fallback when discovery finds nothing or
  cannot be used on the local network.
- [ ] Discard discovery state on network change, service stop, or a manual
  connection, and validate every candidate through the normal handshake.

**Acceptance:** one compatible local Home Assistant Sendspin server is found
and played automatically through the resilient connection path. A selector is
shown only when multiple servers are found, and manual setup remains available.

## Later codec support

Introduce codecs only after PCM playback is reliable:

1. PCM
2. Opus
3. FLAC

No PCM data should cross JNI during normal playback.
