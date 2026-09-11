# Sendspin Satellite implementation plan

This project should grow through small, verifiable vertical slices. Playback
reliability and timing correctness take priority over UI breadth and feature
count.

The intended end state is a thin Android endpoint: after user activation, the
foreground service keeps the configured player ready without the Activity
being open. This does not promise automatic audio restart after a device reboot
or user Force Stop; ordinary Android requires the user to activate it again.

## Phase 0 — application shell 

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

Goal: make playback independent of the Activity lifecycle and let users
distinguish multiple Android players on the Sendspin server.

- [x] Move session/engine ownership into a minimal foreground playback service;
  the Activity only observes and controls it.
- [x] Add a functional notification with status and a Stop action. Do not add
  MediaSession, artwork, or playback UI in this phase.
- [x] Persist a user-editable player name alongside the server address, with a
  useful default and validation for blank or overlong names.
- [x] Add the player-name field to the existing connection screen; do not add a
  separate settings/navigation layer for this single option.
- [x] Make the connection screen vertically scrollable and IME-safe so the
  server address and Connect button remain reachable on small displays.
- [x] Validate the scrollable connection screen in portrait, landscape, and
  with the keyboard open on the smallest supported physical display.
- [x] Pass the name through `ConnectionViewModel`, `PlaybackService`, and
  `SendspinSession` into native `SendspinClientConfig.name`.
- [x] Keep `product_name`, manufacturer, software version, and stable device
  client ID application-controlled; changing the display name must not change
  protocol identity.
- [x] Ensure recovery/reconnect reuses the active name and a new connection
  uses the latest persisted name.
- [x] Validate on a physical device: background, rotate, and remove the task
  while playing; the service remains in control and Stop ends it cleanly.
- [x] Verify two devices with distinct names are unambiguous in the Sendspin
  server player list, including after renaming and reconnecting.

**Acceptance:** an explicitly connected PCM session outlives the Activity and
can be ended reliably from the app or notification. Multiple devices can be
identified by their configured names without changing their stable identities.

## Phase 5 — audio interruption and output recovery

Goal: keep the output path truthful and recoverable through Android audio
events without performing recovery in the real-time callback.

- [x] Add one explicit native recovery state machine with
  `Stopped -> Connecting -> Synchronising -> Ready -> Buffering -> Playing`
  and `Recovering -> Connecting` transitions; user Stop cancels retry.
- [x] Ensure every recovery invalidates the active PCM generation before
  reconnecting or resuming output.
- [x] Extend `OboePcmOutput` with an error callback that only records a restart
  request; the engine loop performs close/reopen and returns to buffering.
- [x] Register a service-owned `AudioDeviceCallback` for output route changes;
  tolerate duplicate recovery requests and failed reopen attempts.
- [x] Add service-boundary audio focus using media audio attributes; handle
  transient loss, gain, ducking, and permanent loss without playing without
  focus.
- [x] Add deterministic tests for state transitions, focus/output recovery,
  retry cancellation, and stream clears during generation changes.
- [x] Validate wired, Bluetooth, or USB route replacement where hardware is
  available, including that pre-change PCM is never rendered.

**Acceptance:** temporary focus loss and route/device replacement produce a
clean re-buffer or user-visible stop, never stale audio or callback-thread
work.

## Phase 6 — network recovery, diagnostics, and soak hardening

Goal: recover from expected network/server failures and make long-running
behaviour measurable.

- [ ] Make the service-owned network provider reflect validated Android network
  state and avoid reconnecting while no validated network exists.
- [ ] Reconnect after temporary network loss or server restart with bounded
  exponential backoff and jitter; disable transport auto-reconnect.
- [ ] Reset clock state and start a new stream generation on every successful
  reconnect; never reuse old clock or PCM buffers.
- [ ] Expose one fixed diagnostics snapshot covering generation, buffer depth,
  output latency where available, underruns, output restarts, hard resyncs,
  clock diagnostics, reconnect counters, and the last recoverable failure.
- [ ] Read diagnostics at a modest cadence while the service is active and
  retain the latest values for the simple status screen/logging.
- [ ] Add deterministic tests for jitter, late PCM, stream clears, clock drift,
  network loss, server restart, retry cancellation, and hard-resync generation
  invalidation.
- [ ] Add a host-run simulated playback soak test with bounded queues,
  repeated recovery, no stale generations rendered, and eventual convergence.
- [ ] Run a shorter soak version in CI and the long version manually before
  release.
- [ ] Validate on a physical device with the screen off: Wi-Fi loss, server
  restart, and at least one physical route change recover with diagnostics.

**Acceptance:** PCM playback recovers predictably from Wi-Fi loss and server
restart, remains measurable over long runs, and never resumes after a user
Stop. The Phase 6 release gate passes on a physical device and in the long
simulated soak.

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
