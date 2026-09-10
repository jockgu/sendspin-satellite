# Phases 4–6 plan — resilient PCM playback

## Outcome

An explicitly configured PCM Sendspin player can run for long periods without
the activity being present. It responds predictably to focus loss, output
device replacement, temporary network loss, and a server restart. Its failure
and recovery behaviour is measurable.

This is the foundation for a thin Android endpoint: once activated by the
user, the foreground service keeps the player ready without the UI open. It
does not attempt to start media playback unattended after device reboot or a
user Force Stop.

These phases do not change the playback protocol or add codecs. Manual server
configuration remains the connection method until Phase 7.

## Guardrails

- The foreground service, not `MainActivity` or `ConnectionViewModel`, owns
  the `SendspinSession` and its `NativePlaybackEngine`.
- Kotlin owns Android lifecycle, connectivity, focus, routes, and the
  notification. C++ continues to own the Sendspin clock, decoded PCM queues,
  scheduling, and Oboe callback.
- The Oboe callback stays allocation-free and non-blocking. Error callbacks
  may only record a recovery request; the engine loop performs the close and
  reopen.
- Every recovery invalidates queued output before resuming. Old PCM must never
  cross a reconnect, route restart, or hard resync.
- Recovery attempts are bounded and observable. An intentional user
  disconnect must never auto-reconnect.

## Deliberate Phase 4 exclusions

- No Media3 player, MediaSession, lock-screen transport controls, album art,
  playback screen, or diagnostics dashboard.
- No codec support, volume UI, equalizer, Bluetooth-specific UX, or manual
  route picker.
- No server discovery or mDNS dependency before Phase 7. The existing address
  field remains the setup and recovery source of truth.

The only new UI is the existing status screen reflecting the service state.
The foreground notification is functional: connection/playback status and a
single Stop action.

## Implementation order

### Phase 4. Make the service the lifecycle boundary

Add a `PlaybackService` with the `mediaPlayback` foreground-service type and
the required foreground-service permissions. A user pressing Connect starts
the service and passes the already validated, persisted server address. The
service creates, owns, and closes the session; it publishes one small state
snapshot for the activity to collect.

Use a plain bound-service command surface (`connect`, `disconnect`, `stop`) or
an application-scoped repository backed by the service. Do not introduce a
second playback state machine in the UI. `ConnectionViewModel` becomes a UI
adapter: it persists the address, observes service state, and sends commands.

Show the foreground notification before work that can keep playback alive.
It needs a content intent to the activity and a Stop action only. Start it
from the user-initiated Connect path; automatic recovery runs within an
already-running service.

**Check:** connect, background the app, rotate it, then swipe it from
recents. Playback/session state remains service-owned; Stop ends it cleanly.

### Phase 5. Add one explicit recovery state machine

Replace the native four-state reporting with states that distinguish normal
connection from recovery:

`Stopped -> Connecting -> Synchronising -> Ready -> Buffering -> Playing`

and, on a recoverable fault,

`Playing|Buffering|Ready -> Recovering -> Connecting`.

`Error` is terminal only after the bounded retry policy is exhausted or the
configuration is invalid. `Stopped` is terminal for an explicit Disconnect or
Stop and cancels all pending retries.

Keep state transitions in the native engine loop, where connection and output
state already meet. Expose a compact JNI diagnostics snapshot rather than
polling or manipulating individual native subsystems from Kotlin.

**Check:** unit-test transition sequences, especially that a user Stop wins
over a queued reconnect and that every recovery clears the active generation.

### Phase 5. Recover the Android audio output

Extend `OboePcmOutput` with an Oboe error callback. It atomically records an
output-restart request; `NativePlaybackEngine::run()` consumes that request,
invalidates the PCM generation, stops/closes the stream, opens a fresh stream,
and returns to buffering. Never reopen from Oboe's callback thread.

The service registers an `AudioDeviceCallback` while active. Its only job is
to request the same native output recovery when the current output disappears
or a route changes. The engine must tolerate duplicate requests and failures
to reopen. Route changes are not errors by themselves: the player reopens,
re-buffers, and reports the outcome.

**Check:** use wired headset, Bluetooth, and USB DAC attach/remove where
available; verify a route change cannot play pre-change queued audio.

### Phase 5. Apply audio focus at the service boundary

Use `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MUSIC)` and a permanent focus
request. Acquire focus immediately before enabling output; abandon it when
output is stopped. Pause/mute output on transient loss and request a clean
re-buffer/resume on gain. On permanent loss, stop output and require an
explicit user Connect to resume; do not fight another player.

The server connection may remain alive while output is temporarily paused only
if the native player can remain protocol-correct without draining audible
audio. Otherwise prefer the known-good path: invalidate, stop output, then
reconnect and resynchronise when focus returns. This decision is verified in
the first implementation spike rather than guessed.

**Check:** interrupt with another media app, notification ducking, and an
incoming call; no audio plays without focus and recovery does not resume after
permanent loss.

### Phase 6. Recover network and server connections

Make `SendspinNetworkProvider` reflect Android's validated network state,
using a service-owned `ConnectivityManager.NetworkCallback`. Do not reconnect
while no validated network exists. On loss, invalidate output and enter
`Recovering`; on availability, retry the saved manual URL with capped
exponential backoff and jitter.

Do not rely on the vendored library's transport auto-reconnect: outbound
connections deliberately disable it. Retrying belongs in the native engine
loop so its policy, state, clock reset, stream generation, and diagnostics are
one coherent operation. A successful reconnect starts a new time-sync/stream
generation; it never reuses the old clock or buffers.

**Check:** toggle Wi-Fi, reboot the server, and restore each. Confirm retry
count/backoff, fresh clock convergence, and a clean start before audio resumes.

### Phase 6. Expose a fixed diagnostics snapshot

Add a single native snapshot with: engine state, current generation, render
FIFO frames, output buffer/latency when available, cumulative underruns,
reconnect attempts, completed reconnects, output restarts, hard resyncs,
clock diagnostics, and last recoverable failure reason. Counters reset only
when the engine is recreated; per-connection values are clearly labelled or
reset at connection start.

Kotlin reads this at a modest cadence while the service is active and retains
the latest values for the simple status screen/logging. No high-frequency JNI
metrics and no UI charting.

**Check:** induce each supported fault and confirm exactly one or more
meaningful counters advance without affecting the audio callback's allocation
or locking behaviour.

### Phase 6. Build deterministic and soak coverage before calling it complete

Keep tests outside Android where possible. Add a fake clock, fake transport
outcomes, and fake output-restart signal around the native recovery policy.
Cover jitter/late PCM, stream clears during output restart, clock drift,
network loss, server restart, retry cancellation, and hard-resync generation
invalidation. Retain the existing FIFO and clock tests.

Add a host-run simulated soak executable that drives many hours of virtual
playback with bounded queues and repeated recoveries. It asserts bounded
queue depth, no stale generation rendered, no counter overflow, and eventual
convergence after each recoverable interruption. Run a shorter version in CI;
run the long version manually before releases.

### Phase 6 release gate

Phase 6 is complete only when a physical device can play a PCM stream with
the screen off, survive activity destruction, recover from Wi-Fi loss/server
restart and at least one physical route change, and provide diagnostics for
each recovery. A long simulated soak must pass without stale PCM, queue
growth, or unrecovered state.

## Phase 7 — server autodiscovery

Phase 7 follows only after the Phase 6 release gate. It targets the normal
installation: one Home Assistant
Sendspin server on the local network. Android's native `NsdManager` browses
the Sendspin mDNS service type `_sendspin._tcp.`, resolves it to a host and
port, constructs the normal Sendspin URL (`ws://host:port/sendspin`), and
hands it to the already-proven Phase 6 service/recovery path.

Before implementation, re-check the current Android local-network permission
rules for the project's target SDK. Newer Android releases restrict broad
service browsing without local-network permission or a system picker; choose
the platform-supported path and make the permission/request visible to the
user rather than silently failing discovery.

Keep the first version small:

- browse only while the service is active and a validated local network exists;
- when exactly one compatible server is discovered, use it automatically;
- when multiple compatible servers are discovered, show a small selector and
  use the user's choice; do not build a browser, catalogue, or management UI;
- retain the manual address as an escape hatch if discovery finds nothing or
  the user's network does not advertise the expected server;
- treat discovery as a hint, not trust: the normal Sendspin handshake remains
  the identity/compatibility check;
- stop browsing and discard stale resolutions on network change, service stop,
  or explicit manual connection.

This phase excludes a server browser UI, saved server catalogue,
cross-network discovery, and custom native mDNS code/dependencies. Its
acceptance is that a single compatible Home Assistant Sendspin server is found
and connected automatically through the same resilient path as a manually
configured server. A selector appears only when multiple servers are found.
