# Sendspin Satellite (Android)

Alpha Android Sendspin client focused on reliability-first playback.

## Current status (alpha)

The project is currently validating **Phase 3** of the implementation plan: a native PCM playback vertical slice.

What is currently working:
- Android app can connect to a Sendspin server and complete handshake/time sync.
- Client now publishes a **9.1.1-compliant** `client/state` shape (`available` + required player timing fields).
- Client is now listed as **available** by Music Assistant/aiosendspin in normal tests.
- Native playback path is active via `sendspin-cpp` + Oboe, with bounded generation-aware FIFO handling.

Known alpha limitations:
- Playback reliability and format behavior still vary by source type while Phase 3 testing continues.
- Android emulator audio quality/timing is not representative of real hardware.
- Full resilience features (focus/route/device-loss/network recovery/soak hardening) are Phase 4 scope.

## Protocol compatibility requirement

This alpha branch now assumes server behavior compatible with:
- **aiosendspin 9.1.1** (as shipped with Music Assistant 2.10.2 dependency set)

Reason:
- The client-side patch migrated from legacy top-level `state` to `payload.available` and includes required player timing fields expected by modern server validation.

If you test against older Sendspin/aiosendspin servers, availability and playback behavior may not match this branch.

## Compatibility matrix (alpha)

| Music Assistant | aiosendspin | Tested on | Connect | Available | Playback |
| --- | --- | --- | --- | --- | --- |
| 2.10.2 | 9.1.1 | Android Emulator | Yes | Yes | Partial (source-dependent during Phase 3) |

Notes:
- This matrix reflects known-tested combinations only.
- If your MA instance differs, treat behavior as unverified until tested.
- Future entries should include whether connection, availability, and playback were each validated.

## Build

```bash
./gradlew :app:assembleDebug
```

Install and test on a physical Android device for meaningful playback validation.

## Development notes

- Product and engineering direction: `AGENTS.md`, `NORTH_STAR.md`.
- Implementation phases and acceptance targets: `TODO.md`.
