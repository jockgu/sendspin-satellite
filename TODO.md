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

- [x] Make the service-owned network provider reflect validated Android network
  state and avoid reconnecting while no validated network exists.
- [x] Reconnect after temporary network loss or server restart with bounded
  exponential backoff and jitter; disable transport auto-reconnect.
- [x] Reset clock state and start a new stream generation on every successful
  reconnect; never reuse old clock or PCM buffers.
- [x] Expose one fixed diagnostics snapshot covering generation, buffer depth,
  output latency where available, underruns, output restarts, hard resyncs,
  clock diagnostics, reconnect counters, and the last recoverable failure.
- [x] Read diagnostics at a modest cadence while the service is active and
  retain the latest values for the simple status screen/logging.
- [x] Add deterministic tests for jitter, late PCM, stream clears, clock drift,
  network loss, server restart, retry cancellation, and hard-resync generation
  invalidation.
- [x] Add a host-run simulated playback soak test with bounded queues,
  repeated recovery, no stale generations rendered, and eventual convergence.
- [x] Add the shorter soak version to CI and run the long version manually
  before release.
- [x] Validate on a physical device with the screen off: Wi-Fi loss, server
  restart, and at least one physical route change recover with diagnostics.

The native snapshot now reports output latency when Oboe provides it, clock
error, convergence, samples, recovery counters, and the latest failure. The
pinned public `sendspin-cpp` API does not expose true RTT, clock offset, or
clock drift accessors, so those native snapshot fields remain `-1` until an
upstream API is available. The physical-device release gate is complete.

**Acceptance:** PCM playback recovers predictably from Wi-Fi loss and server
restart, remains measurable over long runs, and never resumes after a user
Stop. The Phase 6 release gate passes on a physical device and in the long
simulated soak.

Detailed implementation order, exclusions, and Phase 4–7 boundaries:
`PHASE_4_PLAN.md`.

## Phase 7 — Home Assistant server autodiscovery

Goal: make the normal one-server Home Assistant installation work without any
server setup, without weakening the proven manual connection and recovery path.

- [x] Browse and resolve `_sendspin-server._tcp` using Android's native `NsdManager`
  only while the service is active on a validated local network.
- [x] Hand the resolved `ws://host:port/sendspin` URL to the existing Phase 6
  service; do not create a second connection path.
- [x] Connect automatically when exactly one compatible server is found.
- [x] Show a simple selector only when multiple compatible servers are found;
  do not build a server browser, catalogue, or management UI.
- [x] Keep manual configuration as the fallback when discovery finds nothing or
  cannot be used on the local network.
- [x] Discard discovery state on network change, service stop, or a manual
  connection, and validate every candidate through the normal handshake.

**Acceptance:** one compatible local Home Assistant Sendspin server is found
and played automatically through the resilient connection path. A selector is
shown only when multiple servers are found, and manual setup remains available.

Phase 7 acceptance is complete: server autodiscovery and playback were
validated on an Amazon Echo device. This completes the initial reliable-player
baseline. The next phase adds a deliberately small now-playing surface without
changing the playback path.

## Phase 8 — focused now-playing screen

Goal: replace the connected "ready" view with a useful, calm now-playing
screen while keeping metadata and artwork failures isolated from audio
playback.

### Research and product decision

Canonical sources:

- [Sendspin protocol specification](https://github.com/Sendspin/spec)
- [Music Assistant Sendspin provider](https://github.com/music-assistant/server/blob/dev/music_assistant/providers/sendspin/README.md)
- [Music Assistant metadata and artwork sender](https://github.com/music-assistant/server/blob/dev/music_assistant/providers/sendspin/player.py)
- The pinned `sendspin-cpp` headers and role tests under
  `app/src/main/cpp/third_party/sendspin-cpp`.

What is available:

- Every connected client can receive `group/update`: the group's friendly
  name, stable ID, and whether the group is playing or stopped. The pinned C++
  library already exposes this callback, but the Android engine currently
  ignores it.
- `metadata@v1` supplies a scheduled, clearable state containing title,
  artist, album artist, album, artwork URL, release year, track number, and
  progress. Progress contains the position, duration, and playback speed, so a
  client can distinguish advancing playback from a paused position. Fields are
  optional and the server may decline to activate the role.
- `artwork@v1` supplies timestamped JPEG or PNG image bytes. The client chooses
  one to four channels, their album/artist source, and their exact dimensions.
  The role defines late-join, replacement, cancellation, and clear behaviour.
- Music Assistant currently populates the metadata fields above, streams album
  artwork even for sources such as radio and Spotify Connect, and can also
  stream artist artwork. It also publishes colour palettes, repeat/shuffle
  state, controller capabilities, and visualizer data when clients request the
  corresponding roles.
- The current app advertises only `player@v1`. Although the pinned
  `sendspin-cpp` version implements metadata and artwork listeners, both roles
  are explicitly disabled in the Android CMake build and neither is bridged to
  Kotlin.

The first now-playing screen should show, in visual priority order:

1. playback/connection status;
2. one album-art image, with a quiet placeholder when absent;
3. title, artist, and album (album artist is only a fallback for a missing
   artist);
4. a non-interactive progress indicator only when duration is known;
5. one compact server/group context line and a Disconnect action.

Do not put group IDs, year, track number, artwork URL, codecs, clock data,
buffers, or recovery counters on the normal screen. Those are either low-value
or already belong in Audio diagnostics. Do not add playback controls,
visualizers, artist art, colour-derived theming, a navigation framework, or a
MediaSession in this phase. They require separate product decisions and roles.

Prefer the Sendspin artwork role over downloading `artwork_url`: it asks the
server for an already-sized image, follows Sendspin's synchronized clear and
replacement rules, and avoids adding an image-loading dependency or a second
HTTP/authentication path. Keep the URL in the protocol model for diagnostics
and future interoperability, but do not fetch it in the first implementation.

### Phase 8.1 — metadata and group-state vertical slice

Goal: make the server's text state observable end-to-end before changing the
screen.

Detailed delivery plan: [PHASE_8_1_PLAN.md](PHASE_8_1_PLAN.md).

- [x] Add one immutable `NowPlaying` model with nullable text fields, progress,
  playback speed, group name/state, and a monotonically increasing revision or
  generation. Keep protocol types out of Compose.
- [x] Enable only `SENDSPIN_ENABLE_METADATA` in the Android CMake build and add
  the metadata role to the existing native client; do not create another
  transport or parse the protocol again in Kotlin.
- [x] Implement `MetadataRoleListener` in the native engine and consume the
  existing `SendspinClientListener.on_group_update` callback.
- [x] Preserve the library's timestamp scheduling and full-state merge
  semantics. Clear the snapshot on metadata clear, disconnect, server change,
  and recovery generation change so old track details cannot leak into a new
  session.
- [x] Expose one coarse-grained native snapshot through JNI, including strings
  and progress. Do not add per-field JNI calls and do not move PCM across JNI.
- [x] Propagate the snapshot through `SendspinSession`, `PlaybackStatus`, and
  `ConnectionUiState` without coupling the Activity to the native engine.
- [x] Add deterministic app-owned tests for full snapshots, explicit clear,
  revision reads, group updates, and reconnect-generation invalidation. Keep
  partial-delta merging and scheduled replacement inside `sendspin-cpp`, where
  the native engine receives only the fully applied callback.

**Gate:** With the existing UI unchanged, a real Music Assistant session
reports the correct track, progress source values, and group state; stopping,
disconnecting, or reconnecting clears stale data. PCM playback and recovery
tests still pass.

Implementation smoke check (2026-09-15): all host and Kotlin tests pass, the
debug APK builds for every configured ABI, and an Echo Show connected to Music
Assistant delivered an empty recovery-generation snapshot followed by the
current title, artist, album, progress tuple, and stopped group state. Explicit
track-change/seek and server-without-metadata scenarios remain part of the
release/device matrix rather than permanent debug UI.

### Phase 8.2 — separate the UI responsibilities

Goal: make room for the screen without turning `SendspinSatelliteApp.kt` into a
second monolith.

- [ ] Keep `SendspinSatelliteApp.kt` as the small root that selects the active
  screen and owns the diagnostics destination and server-selection dialog.
- [ ] Move the existing address, discovery, player-name, and connect controls
  into `ConnectionScreen.kt` with no behavioural redesign.
- [ ] Add `NowPlayingScreen.kt` for an active session. Show it while connecting,
  synchronising, ready, buffering/playing, or recovering so a transient failure
  does not throw the user back into setup.
- [ ] Use a small pure mapper from `ConnectionUiState` to a presentation model
  so missing-field fallbacks and status wording can be unit tested without
  Compose or Android.
- [ ] Add previews for disconnected setup, connecting, complete metadata,
  partial/no metadata, long text, recovery, and a wide/landscape layout.
- [ ] Do not add Navigation Compose or another UI dependency for this two-screen
  switch.

**Gate:** The app looks and behaves exactly as before when disconnected. An
active connection reaches an intentionally plain now-playing shell, diagnostics
and Disconnect remain reachable, and rotation/recreation preserves the screen.

### Phase 8.3 — text-first now-playing UI

Goal: ship useful now-playing information without waiting for image handling.

- [ ] Show a concise status such as Ready, Buffering, Playing, Paused, or
  Recovering. Derive it from native playback state, group state, and metadata
  playback speed rather than inventing another independent state machine.
- [ ] Render title as the primary line, artist as the secondary line, and album
  as a quieter tertiary line. Collapse absent rows; use album artist only when
  artist is absent.
- [ ] When metadata is absent, show a stable "Ready for playback" empty state
  rather than blank labels or stale content.
- [ ] Show group and server names in one compact context line when present. Do
  not show IDs or raw addresses.
- [ ] Keep Disconnect visually secondary and keep the Audio diagnostics icon
  available.
- [ ] Constrain long metadata with sensible line limits and ellipsis; provide
  semantic descriptions and touch targets suitable for accessibility.
- [ ] Adapt the layout for narrow portrait and wide/landscape screens using
  Compose layout primitives already in the project.

**Gate:** Title/artist/album and state remain readable on the smallest supported
display, in landscape, with large font scaling, and for missing or unusually
long metadata. No setup field appears during an active session.

### Phase 8.4 — one bounded album-art channel

Goal: add artwork without adding an independent network path or threatening
long-running playback.

- [ ] Enable `SENDSPIN_ENABLE_ARTWORK` and register exactly one album-art
  channel using JPEG at a fixed, modest size (start with 512 x 512). Do not add
  artist art or responsive renegotiation yet.
- [ ] Implement `ArtworkRoleListener` outside the audio callback. Keep only the
  latest staged image and current displayed image, cap accepted encoded bytes,
  and associate both with the active stream/session generation.
- [ ] Publish a revision plus encoded bytes through a separate coarse JNI read
  only when artwork changes; do not copy image data on every state poll.
- [ ] Decode with Android's `BitmapFactory` off the main thread. Keep only one
  current decoded bitmap and add no image-loading library.
- [ ] Apply display and clear callbacks in their Sendspin-scheduled order. Clear
  current and pending art on stream end, metadata/artwork clear, disconnect,
  reconnect, and decode failure.
- [ ] Add a neutral in-app placeholder that does not imply missing metadata is
  an error. Skip cross-fades and animated backgrounds in the first version.
- [ ] Add tests for late join, rapid track skip, missing artwork, oversized or
  undecodable data, clear, disconnect, and an old generation completing after a
  new connection.

**Gate:** Album art appears and clears correctly against a real Music Assistant
server, including on late join and track skip. Repeated changes do not grow
memory, block the UI, alter audio timing, or reintroduce stale artwork after
recovery.

### Phase 8.5 — progress, only when trustworthy

Goal: add one useful dynamic element after the static screen is stable.

- [ ] Read interpolated progress from the existing native metadata role at a
  modest cadence (about once per second); do not run a frame-rate ticker.
- [ ] Show a slim, non-seekable progress bar and elapsed/duration text only when
  duration is greater than zero. Omit the row for unlimited or unknown
  duration rather than guessing that the source is live.
- [ ] Freeze progress when playback speed is zero and apply server corrections
  after pause, resume, or seek without animating backward through stale values.
- [ ] Keep seeking and transport buttons out of scope; those require the
  controller role and its advertised capabilities.
- [ ] Unit-test duration formatting, bounds, pause/resume, unknown duration,
  seek correction, and track replacement.

**Gate:** Progress tracks a real finite track, pauses, resumes, and corrects
after a seek. Radio/unknown-duration playback stays visually clean, and state
updates do not increase notification churn or harm battery use.

### Phase 8.6 — release and maintainability gate

- [ ] Verify setup, discovery, manual connect, disconnect, diagnostics, recovery,
  and foreground-service behaviour still work with metadata absent or roles
  declined by the server.
- [ ] Exercise track changes, natural gapless transitions, pause/resume, seek,
  playback stop, server restart, Wi-Fi loss, and Activity destruction while
  metadata/artwork is changing.
- [ ] Run native/unit tests plus a physical-device session on an older or
  memory-constrained Android device with the screen on and off.
- [ ] Confirm UI files have single responsibilities: root routing in
  `SendspinSatelliteApp.kt`, setup in `ConnectionScreen.kt`, now playing in
  `NowPlayingScreen.kt`, and diagnostics in `AudioDiagnosticsScreen.kt`. Extract
  another component only if it is independently testable or reused.
- [ ] Revisit deferred fields only with evidence from real use. The likely next
  independent decision is MediaSession/notification integration, not more
  information on the main screen.

**Acceptance:** The connected screen answers "is it playing, and what is it?"
at a glance, remains calm when data is partial, never shows data from an old
session, and leaves audio correctness and recovery behaviour unchanged.

## Later codec support

Introduce codecs only after PCM playback is reliable:

1. PCM
2. Opus
3. FLAC

No PCM data should cross JNI during normal playback.

Phase 9 — Opus codec support

Goal: add reliable Opus playback without compromising the established PCM,
native timing, bounded-buffer, or allocation-free callback guarantees.

### Discovery and decisions

- [ ] Inspect the pinned `sendspin-cpp` revision for supported Opus negotiation,
  decoding, stream-clear, error, and packet-loss behaviour.
- [ ] Verify the exact Sendspin Opus capability fields against a compatible
  server and record the tested server/library versions.
- [ ] Decide whether upstream provides a maintained Android-ready `libopus`;
  document the license, security update owner, ABI support, and dependency
  pinning plan.
- [ ] Confirm that upstream delivers decoded 48 kHz/stereo/signed-16 PCM to
  the listener, or define the bounded native compressed-queue/decode-worker
  design needed instead.
- [ ] Define the initial supported profile and explicitly defer any unsupported
  channel layouts, sample formats, resampling, DSP, or UI controls.

### Native implementation

- [ ] Advertise only verified Opus capabilities, retaining PCM during staged
  interoperability work.
- [ ] Extend the native listener/codec boundary without changing the JNI API or
  allowing PCM through Kotlin.
- [ ] If required, add a preallocated, bounded compressed-packet queue and
  non-real-time decode worker that feeds the existing PCM FIFO.
- [ ] Validate negotiated format metadata and decoder output before render;
  reject unsupported values deterministically.
- [ ] Define full-FIFO backpressure so partial writes cannot silently discard
  a packet tail or corrupt the decoder timeline.
- [ ] Invalidate compressed and decoded data on stream start/clear, reconnect,
  format change, decoder reset, output restart, and hard resync.
- [ ] Route malformed packets and decoder failures through the existing
  observable bounded recovery state machine.

### Diagnostics and validation

- [ ] Add native diagnostics for selected codec, queue depths, decode activity,
  decode failures, late/dropped packets, concealment/reset activity, and
  sampled non-real-time decode cost.
- [ ] Add host tests for valid/invalid negotiation, malformed/truncated data,
  queue saturation, loss/jitter/reordering, stream clear, reconnect, and
  stale-generation rejection.
- [ ] Extend the simulated soak to assert bounded compressed/decoded queues,
  no stale frames, recovery convergence, and no counter overflow under Opus
  traffic.
- [ ] Run existing native host tests, Android unit tests, and a debug build.
- [ ] Interoperate with a real supported Sendspin server and confirm PCM
  fallback still works.
- [ ] Perform physical-device checks on older hardware and wired, Bluetooth,
  and USB outputs, including screen-off, Wi-Fi loss, server restart, and route
  changes.
- [ ] Record tested server/device results and release only when CPU, thermal,
  callback-underrun, latency, and recovery outcomes meet the PCM reliability
  baseline.
