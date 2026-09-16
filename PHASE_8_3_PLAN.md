# Phase 8.3 delivery plan — text-first now-playing UI

## Outcome

Replace the plain connected shell with a useful, text-first now-playing
screen. A connected user should be able to answer two questions at a glance:

```text
What is the player doing?
What is playing?
```

The screen will use the existing metadata/group snapshot and the native
playback state. It will not add a second UI state machine, another transport,
or any new runtime dependency.

## Starting point

Phase 8.1 already delivers one immutable `NowPlayingSnapshot` through:

```text
native metadata/group roles
        ↓
SendspinSession
        ↓
PlaybackStatus
        ↓
ConnectionUiState
        ↓
NowPlayingScreen
```

The snapshot already contains title, artist, album artist, album, progress
speed, and group name/state. Phase 8.2 already routes active sessions to
`NowPlayingScreen` and keeps setup, settings, dialogs, and diagnostics
separate.

One gap must be closed before the UI can report playback accurately:
`SendspinSession` currently folds native `READY`, `BUFFERING`, and `PLAYING`
into one `SYNCHRONISED` state, which becomes `ConnectionState.READY`. The
existing native state is sufficient; the Kotlin path only needs to preserve
the distinction.

## Scope

In scope:

- concise status text: Connecting, Ready, Buffering, Playing, Paused,
  Recovering, and the existing error/disconnected states;
- title, artist, and album rows with absent-row collapse;
- album artist as the fallback only when artist is absent;
- a stable `Ready for playback` empty state when no usable metadata exists;
- one compact group/server context line using friendly names only;
- a visually secondary Disconnect action;
- existing Settings and Audio diagnostics access;
- long-text, narrow-portrait, wide-landscape, large-font, and accessibility
  handling;
- pure mapper tests and Compose previews.

Out of scope:

- progress bars, elapsed time, or a UI ticker; these belong to Phase 8.5 and
  the native interpolated progress read;
- album artwork; this belongs to Phase 8.4's bounded artwork channel;
- playback controls, seeking, visualizers, colour theming, MediaSession, or
  notifications;
- changes to metadata parsing, timestamp scheduling, JNI snapshot shape, or
  PCM/audio timing.

## Presentation contract

Extend the package-local `NowPlayingPresentation` in
`ui/NowPlayingScreen.kt` rather than letting Compose read protocol or service
objects directly. The model should contain only values needed to render the
screen, for example:

```kotlin
internal data class NowPlayingPresentation(
    val status: String,
    val message: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val emptyState: String?,
    val context: String?,
    val showConnectionProgress: Boolean,
    val showReconnect: Boolean,
    val showDisconnect: Boolean,
)
```

The mapper should normalize blank strings to absent values before applying
fallbacks. The artist rule is deliberately one-way:

```text
artist when present
otherwise album artist when present
otherwise omit the row
```

Build the context line from the current friendly group name and current
friendly server name, joining whichever are present with a compact separator.
Never use the server address, group ID, or raw protocol fields. When a manual
connection targets a different address from the saved server, do not display
the saved server's old name while the new connection is in progress. Use
`state.serverName` first, and fall back to `savedServer.name` only when
`savedServer.address == state.serverAddress`.

Use this status precedence for an active session:

1. connection lifecycle wins: Connecting, Recovering, error, and stopped;
2. a metadata progress speed of zero with a current track, or a stopped group
   with current track metadata, reports Paused;
3. native Buffering reports Buffering;
4. native Playing reports Playing;
5. synchronized native Ready reports Ready.

If there is no usable title, artist, album, or progress-backed track identity,
keep the status truthful to the native player but render exactly one stable
empty-state line, `Ready for playback`. Do not render empty labels or retain
the previous track's text.

## Implementation slices

### 8.3.a — preserve native playback state through the existing Kotlin path

Use the existing native state polling; do not add JNI calls or a new native
state holder.

- Add `BUFFERING` and `PLAYING` to `SendspinSession.SessionState` and
  `ConnectionState`. Map both to the existing active-session route, keep
  `READY` as the synchronized/idle state, and derive `PAUSED` from the
  metadata speed/group snapshot rather than persisting another independent
  state.
- Update `SendspinSession` so native `READY`, `BUFFERING`, and `PLAYING` are
  not collapsed into one callback value.
- Update `PlaybackService` mapping and notification labels while preserving
  the existing saved-server behavior: a server is saved as soon as the
  synchronized state is reached, not only after a track starts.
- Keep active-session routing in `shouldShowNowPlaying`; include the two new
  native playback states so a buffering/playing transition can never expose
  setup.
- Make `PlaybackStatus.toUiState` clear/replace `serverName` whenever an
  active `SavedServer` is supplied, even if that server has no friendly name.
  Have the mapper fall back to `savedServer.name` only when its address still
  matches the current `serverAddress`, so a previous server name cannot leak
  into a newly entered manual address.

**Gate:** pure Kotlin tests can distinguish Ready, Buffering, Playing, and
paused metadata without constructing Compose or Android components. Existing
connection, reconnect, and saved-server tests remain valid.

### 8.3.b — implement the pure presentation mapper

Replace the Phase 8.2 shell-only mapper with the text-first contract.

Cover at least these cases:

- complete metadata: title, artist, album, group, and server;
- missing artist with album-artist fallback;
- missing artist and album artist;
- missing album;
- null and blank metadata values;
- no metadata with Ready state, producing `Ready for playback`;
- Playing, Buffering, Paused, Ready, Recovering, Connecting, Error, and
  Disconnected status precedence;
- paused metadata speed of zero, including a non-zero finite duration;
- stopped group with a current track;
- context with group only, server only, both, and neither;
- reconnect and Disconnect visibility by lifecycle state;
- long strings remain values for the UI to constrain, without truncating data
  in the domain model.

Keep `NowPlayingSnapshot.progress` available for the paused decision only. Do
not calculate elapsed progress in Kotlin and do not add a timer.

**Gate:** `NowPlayingPresentationTest.kt` fully describes the mapping and no
presentation rule depends on Compose, Android, a clock, or native JNI.

### 8.3.c — render the text-first screen

Update `NowPlayingScreen.kt` to render, in order of importance:

1. concise status and connection progress indicator when connecting or
   recovering;
2. title as the primary metadata line;
3. artist as the secondary line;
4. album as the quieter tertiary line;
5. the `Ready for playback` empty state when all metadata rows are absent;
6. one compact group/server context line when present;
7. secondary Disconnect action where a session is active.

Keep the existing Settings icon in the header. Pass `onDisconnect` from
`SendspinSatelliteApp` to the screen; keep its behavior immediate and
consistent with Settings. Error/disconnected states retain the existing
Connect/reconnect action.

For layout and accessibility:

- use existing Material 3 controls so touch targets remain platform-sized;
- keep the Settings icon and action buttons labelled semantically;
- use `maxLines` and `TextOverflow.Ellipsis` for status, title, artist, album,
  and context; allow the title up to two lines and keep the supporting rows
  compact;
- use a responsive content width (`fillMaxWidth` with a sensible `widthIn`
  maximum) so narrow portrait screens do not clip text while wide screens do
  not stretch the reading column unnecessarily;
- use a scrollable content column or equivalent safe layout so large font
  scaling cannot hide the actions;
- preserve safe drawing insets and do not introduce a navigation or design
  system layer for this screen.

**Gate:** no setup field appears during an active session; the screen remains
usable on the smallest target display, in landscape, and at large font scale.

### 8.3.d — previews and focused tests

Update/add previews for:

- complete metadata while Playing;
- partial metadata using the album-artist fallback;
- no metadata with Ready for playback;
- Paused and Buffering;
- Connecting and Recovering;
- saved-server error/disconnected;
- unusually long title/artist/album/context;
- narrow portrait and wide/landscape layouts;
- a large-font preview if supported by the existing Compose tooling.

Do not add production abstractions solely to make previews easier.

### 8.3.e — integration verification

Run:

1. `./gradlew test`;
2. `./gradlew :app:assembleDebug`;
3. the existing native host tests if the state-mapping change touches native
   build inputs;
4. a physical-device session against the known Music Assistant target.

On device, verify:

- a playing finite track shows the correct title, artist, album, Playing
  status, and friendly group/server context;
- pausing changes only the status to Paused and does not blank the metadata;
- resuming returns to Playing;
- track replacement removes old rows before the new metadata is applied;
- a server that declines metadata keeps PCM playback healthy and shows the
  stable empty state;
- stop, disconnect, Wi-Fi/server recovery, and Activity recreation do not
  show stale track or server context;
- Buffering/Recovering stays on the now-playing screen;
- Disconnect is reachable but visually secondary, and Settings still reaches
  Audio diagnostics;
- narrow portrait, wide landscape, and large font scaling keep all important
  text/actions usable.

## Completion definition

Phase 8.3 is complete when the connected screen truthfully shows the native
playback status, renders useful metadata with safe fallbacks, stays readable
across the supported layouts, and clears stale text across stop/reconnect
boundaries. The result must preserve the existing audio/recovery path and add
no progress ticker, artwork path, transport controls, or runtime dependency.

The acceptance question is:

```text
When music is active, can the user tell what it is and whether this device is
ready, buffering, playing, paused, or recovering without opening diagnostics?
```
