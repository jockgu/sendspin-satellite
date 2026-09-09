# Sendspin Satellite implementation plan

This project should grow through small, verifiable vertical slices. Playback
reliability and timing correctness take priority over UI breadth and feature
count.

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

- [ ] Add Oboe audio output in the native engine.
- [ ] Support PCM only.
- [ ] Add bounded, generation-tagged PCM timeline and render FIFO buffers.
- [ ] Implement `stream/start` and `stream/clear`; stale generations must never
  render.
- [ ] Keep the audio callback allocation-free and non-blocking.
- [ ] Recover from underrun by returning to buffering.

**Acceptance:** A controlled PCM stream plays continuously, starts on schedule,
and never leaks audio across a stream restart.

## Phase 4 — playback resilience

- [ ] Add foreground playback-service ownership.
- [ ] Handle audio focus, output route changes, and audio-device restart.
- [ ] Reconnect after temporary network loss and server restart.
- [ ] Record useful playback diagnostics: buffers, underruns, resyncs, clock
  state, and reconnects.
- [ ] Add deterministic tests for jitter, late packets, stream clears, clock
  drift, and reconnection.
- [ ] Add long-running simulated playback/soak tests.

**Acceptance:** PCM playback recovers predictably from expected failures and
remains measurable over long runs.

## Later codec support

Introduce codecs only after PCM playback is reliable:

1. PCM
2. Opus
3. FLAC

No PCM data should cross JNI during normal playback.
