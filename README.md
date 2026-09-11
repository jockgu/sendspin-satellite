# Sendspin Satellite (Android)

Alpha Android Sendspin client focused on reliability-first playback.

## Current status (alpha)

The project is implementing **Phase 6** of the implementation plan: network
recovery, fixed diagnostics, and soak hardening. The host recovery tests and
virtual soak are passing; the physical-device release gate remains open.

What is currently working:
- Android app can connect to a Sendspin server and complete handshake/time sync.
- Client now publishes a **9.1.1-compliant** `client/state` shape (`available` + required player timing fields).
- Client is now listed as **available** by Music Assistant/aiosendspin in normal tests.
- Native playback path is active via `sendspin-cpp` + Oboe, with bounded generation-aware FIFO handling.
- Foreground-service recovery responds to validated network changes, transport
  loss, output errors, route changes, and focus recovery with bounded retry.
- A fixed native diagnostics snapshot is retained and logged at a modest rate.
- The direct host tests and 24-hour virtual recovery soak pass; a 15-minute
  short soak is configured for CI.

Known alpha limitations:
- Playback reliability and format behavior still vary by source type while
  physical Phase 6 testing continues.
- Android emulator audio quality/timing is not representative of real hardware.
- The physical screen-off, Wi-Fi loss, server restart, and route-change checks
  are not yet complete.
- The pinned public `sendspin-cpp` API does not expose true RTT, clock offset,
  or clock drift accessors; those native snapshot fields remain `-1`.

## Sendspin compatibility

Sendspin Satellite supports the encrypted Sendspin protocol generation (core
protocol v1 and `player@v1`). Earlier pre-encryption Sendspin implementations
are not supported.

Compatible additive protocol extensions are tolerated where the Sendspin
specification defines them. A future core or role protocol version is supported
only after deliberate interoperability testing with Music Assistant; it is not
assumed compatible merely because it is newer.

### Alpha test target

This alpha branch now assumes server behavior compatible with:
- **aiosendspin 9.1.1** (as shipped with Music Assistant 2.10.2 dependency set)

Reason:
- The client-side patch migrated from legacy top-level `state` to `payload.available` and includes required player timing fields expected by modern server validation.

If you test against older Sendspin/aiosendspin servers, availability and playback behavior may not match this branch.

## Compatibility matrix (alpha)

| Music Assistant | aiosendspin | Tested on | Connect | Available | Playback |
| --- | --- | --- | --- | --- | --- |
| 2.10.2 | 9.1.1 | Android Emulator | Yes | Yes | Partial (source-dependent; physical recovery pending) |

Notes:
- This matrix reflects known-tested combinations only.
- If your MA instance differs, treat behavior as unverified until tested.
- Future entries should include whether connection, availability, and playback were each validated.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Install and test on a physical Android device for meaningful playback validation.

## Development notes

- Product and engineering direction: `AGENTS.md`, `NORTH_STAR.md`.
- Implementation phases and acceptance targets: `TODO.md`.
- Native host-test workflow: `.github/workflows/native-tests.yml`.
