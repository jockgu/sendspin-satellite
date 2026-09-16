# Phase 8.1 delivery plan — metadata and group state

## Implementation status

Implemented on 2026-09-15. The app-owned snapshot holder, metadata/group
listeners, revision-aware typed JNI read, service propagation, and deterministic
state-clearing tests are in place with no Compose changes or new runtime
dependency.

The host CTest suite and Kotlin tests pass, and the debug APK compiles the
enabled metadata role for all configured ABIs. A real Echo Show/Music Assistant
smoke test observed the initial empty snapshot, a recovery-generation clear,
and then the current title, artist, album, progress tuple, and stopped group
state. The temporary inspection log was removed. Track-change/seek, explicit
Wi-Fi/server restart, and a server that declines metadata remain useful release
matrix checks; they do not require another application data path.

## Outcome

The foreground service receives current Sendspin track metadata and group state
from the native client, publishes one immutable Kotlin snapshot, and clears it
before an old server or stream can be shown as current. This phase deliberately
does not change the connection screen or add artwork.

The user-visible result remains the current screen until Phase 8.2/8.3. The
result of this phase is a verified data path which later UI work can consume
without touching the Sendspin transport or audio engine again.

## Scope

In scope:

- `metadata@v1`: title, artist, album artist, album, the server-reported
  position/duration/speed tuple, and its scheduled clear behaviour.
- `group/update`: friendly group name and playing/stopped state.
- One coarse native-to-Kotlin snapshot and service-state propagation.
- Deterministic stale-state and mapping coverage.

Out of scope:

- Changes to `SendspinSatelliteApp.kt`, Compose, notifications, or diagnostics
  UI.
- Artwork, `artwork_url`, release year, track number, controller controls,
  dynamic progress display, MediaSession, and any new dependency.
- Reimplementing Sendspin metadata parsing, timestamp scheduling, or progress
  interpolation outside `sendspin-cpp`.

## Existing path and ownership

```text
server/state metadata + group/update
              │
              ▼
sendspin-cpp metadata role and client loop
  - merges partial state
  - applies scheduled metadata at its server-clock deadline
              │
              ▼
NativePlaybackEngine metadata/group listeners
              │  mutex-protected immutable snapshot, revisioned
              ▼
one JNI snapshot read when revision changes
              │
              ▼
SendspinSession → PlaybackService → PlaybackStatus → ConnectionUiState
```

`sendspin-cpp` is already responsible for metadata field-overlay semantics and
server-time scheduling. The native engine must consume its callbacks only after
the library has applied a complete current state. Kotlin must not parse
`server/state`, calculate timestamps, or interpolate playback progress.

The Oboe callback is not part of this path. The only concurrent access is the
native engine loop writing the snapshot and the Kotlin poller reading it over
JNI; protect that state with a normal mutex outside real-time code.

## Snapshot contract

Add one small application-domain value in `playback/NowPlayingSnapshot.kt`.
It is the value carried by `PlaybackStatus` and `ConnectionUiState`; it is not a
Compose presentation model.

```kotlin
data class NowPlayingSnapshot(
    val revision: Long = 0,
    val generation: Long = 0,
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val progress: Progress? = null,
    val group: Group? = null,
) {
    data class Progress(
        val reportedPositionMs: Long,
        val durationMs: Long,
        val playbackSpeedMilli: Int,
    )

    data class Group(
        val name: String? = null,
        val playbackState: PlaybackState? = null,
    )

    enum class PlaybackState { PLAYING, STOPPED }
}
```

`reportedPositionMs` is the position from the applied server metadata update,
not an app-side ticker. Phase 8.5 will add a separately designed, native-owned
interpolated progress read. A duration of zero remains a valid unknown or
unlimited duration, not an inferred "live" flag.

`revision` increments for every snapshot mutation. `generation` is the active
native recovery generation at the time the snapshot was cleared or updated.
They make a poller efficient and make stale-state assertions explicit; neither
is a user-facing value.

Do not add artwork URL, year, track number, group ID, server ID, or raw
timestamps now. None is needed by the next text screen.

## Implementation slices

### 8.1.a — add a testable native snapshot holder

Create `now_playing_state.h/.cpp` and a host `now_playing_state_test.cpp`.
This is a concrete mutex-protected value holder, not an interface or a second
state machine. Its narrow responsibilities are:

- replace the metadata portion from a fully applied metadata callback;
- replace the group portion from the client's full current group state;
- clear only metadata when the metadata role is cleared;
- clear all fields on an engine/session-generation reset;
- return no value when `snapshot_after(known_revision)` has nothing new;
- reject an update tagged with an older generation.

Keep the holder independent of `sendspin-cpp` and Oboe so it can use the
project's existing host CTest/assert style. Register it as a small static CMake
target, link it into `sendspin_native` on Android, and link the same target
into its host test. The native engine maps the existing Sendspin structs into
this holder at its boundary.

Required host checks:

- a complete metadata replacement retains exactly title, artist, album artist,
  album, and the reported progress tuple;
- a metadata clear removes media fields but does not invent a group update;
- group updates change only the group portion;
- an all-state clear removes both portions and advances revision/generation;
- a repeated read at the same revision returns no snapshot;
- a late old-generation update cannot restore cleared metadata.

**Gate:** `now_playing_state_test` passes in the normal non-Android CMake test
configuration. The module has no audio, JNI, Android, or third-party transport
dependency.

### 8.1.b — enable and subscribe to the existing metadata role

Make the smallest build change in `app/src/main/cpp/CMakeLists.txt`:

- change `SENDSPIN_ENABLE_METADATA` from `OFF` to `ON`;
- leave artwork, controller, colour, and visualizer disabled.

Update `NativePlaybackEngine` to inherit `MetadataRoleListener` as well as its
current Sendspin listener roles. Register the metadata role before
`start_server()`, keep a `MetadataRole&`, and set its listener in the
constructor. This makes `sendspin-cpp` advertise `metadata@v1` through the
existing client hello; do not build another hello message or alter the player
role.

Implement exactly these callbacks:

- `on_metadata(const ServerMetadataStateObject&)`: copy only the fields in the
  snapshot contract. The library has already merged partial wire updates and
  waited for the metadata timestamp.
- `on_metadata_clear()`: clear the metadata portion immediately.
- `on_group_update(...)`: read `client_.get_group_state()` and copy the full
  current group state, not the callback's partial delta. A later group update
  may omit unchanged name or state fields.

At each native recovery transition that invalidates PCM, clear the whole
snapshot before disconnecting or attempting the new connection. Also clear it
synchronously on user Disconnect and when a new connect target is accepted.
The library's metadata-clear callback remains a second, harmless clear. This
ordering closes the window in which a recovering device could still report the
previous track.

**Gate:** a metadata role declined by the server leaves an empty snapshot and
does not affect the established player-only connection. Existing PCM recovery
tests and all current host tests still pass.

### 8.1.c — expose one revision-aware JNI read

Add this sole data read to `NativePlaybackEngine`:

```kotlin
fun nowPlayingIfChanged(knownRevision: Long): NowPlayingSnapshot?
```

Its JNI counterpart takes `knownRevision` and returns `null` only when no new
snapshot exists. Otherwise it constructs one typed `NowPlayingSnapshot`,
including nullable strings and the optional nested progress/group values. Do
not split this into field getters, arrays with positional string values, native
callbacks into Kotlin, or a polling loop that copies strings at every tick.

`SendspinSession` polls this method alongside its existing 250 ms state poll.
It retains the last observed revision and calls a new
`Listener.onNowPlaying(snapshot)` only for a newer snapshot. Its listener is
the service; it is not the Activity. The first read for a new session must
deliver an empty revision-zero snapshot, so replacing a session cannot retain
the previous service state while it waits for a metadata message.

Use the direct typed snapshot rather than JSON: it adds no parser, has a
compiler-visible field contract, and crosses JNI only after actual state
changes. Do not add any high-frequency metrics to this boundary.

**Gate:** a JNI build can construct and return empty, populated, and cleared
snapshots. A steady connected session performs no string/object transfer after
the initial snapshot until metadata or group state changes.

### 8.1.d — publish through the existing service state

Thread the value along the existing ownership chain only:

```text
NativePlaybackEngine
  → SendspinSession.Listener.onNowPlaying
  → PlaybackService
  → PlaybackStatus.nowPlaying
  → ConnectionUiState.nowPlaying
```

When `PlaybackService` replaces a session, starts a new connect, stops,
finishes discovery, or is destroyed, publish the default empty
`NowPlayingSnapshot` as part of the normal fresh `PlaybackStatus`. Retain the
service's existing `sessionGeneration` callback guard; it prevents a retired
session's scheduled poll from publishing over the new one.

Do not touch `ConnectionViewModel` beyond allowing its existing
`PlaybackStatus.toUiState` copy to carry the new field. Do not render the new
field yet.

Add focused Kotlin tests to the existing `PlaybackStatusTest` family:

- a populated service snapshot arrives unchanged in `ConnectionUiState`;
- a fresh/stop status replaces it with the empty snapshot;
- configured address and player name remain preserved as they are today.

**Gate:** the Activity-observed state is complete, immutable, and empty after a
new service status or Stop. No current text, connection, discovery, or
diagnostics behaviour changes.

### 8.1.e — integration and release check

Build and run, in this order:

1. the new host snapshot test and existing host CTest suite;
2. `./gradlew test` for Kotlin state mapping;
3. `./gradlew :app:assembleDebug` to compile JNI and the enabled metadata role;
4. an Android device connected to a real Music Assistant Sendspin server.

For the device check, validate these scenarios through a debugger or a
temporary local development inspection (not permanent normal-screen UI):

- join while a finite track is already playing: populated text/progress and
  group state arrive;
- change to another track and seek: the applied snapshot changes as expected;
- pause/resume: playback speed changes between zero and normal speed;
- stop: metadata clears;
- server restart and Wi-Fi interruption: state clears before recovery and does
  not repopulate from the old session;
- a server that does not activate metadata: playback stays healthy with an
  empty snapshot.

Remove any temporary development inspection before merging. Phase 8.2/8.3 is
the first user-facing presentation of this state.

## Completion definition

Phase 8.1 is complete when metadata and group state traverse the existing
service path, every stale-state clear has a deterministic test, the enabled
role does not change PCM behaviour, and a real server validates the received
state across reconnect. It is not complete merely because the C++ role is
enabled: the Kotlin snapshot and generation-clearing behaviour are the release
contract.
