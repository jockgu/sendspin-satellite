# Sendspin Satellite (Android)

Alpha Android Sendspin client focused on reliability-first playback.

## Current status (alpha)

The project has completed **Phase 7** of the implementation plan: Home
Assistant server autodiscovery through Android NSD. Phase 6 network recovery,
diagnostics, soak hardening, and physical-device validation are complete. This
completes the planned phased development work for the alpha app.

What is currently working:
- Android app can connect to a Sendspin server and complete handshake/time sync.
- Client now publishes a **9.1.1-compliant** `client/state` shape (`available` + required player timing fields).
- Client is now listed as **available** by Music Assistant/aiosendspin in normal tests.
- Native playback path is active via `sendspin-cpp` + Oboe, with bounded generation-aware FIFO handling.
- Foreground-service recovery responds to validated network changes, transport
  loss, output errors, route changes, and focus recovery with bounded retry.
- A fixed native diagnostics snapshot is retained and logged at a modest rate.
- Native playback advertises 48 kHz stereo 16-bit FLAC first, with PCM fallback.
- The direct host tests and 24-hour virtual recovery soak pass; a 15-minute
  short soak is configured for CI.

Known alpha limitations:
- Playback reliability and format behavior still vary by source type.
- Android emulator audio quality/timing is not representative of real hardware.
- The pinned public `sendspin-cpp` API does not expose true RTT, clock offset,
  or clock drift accessors; those native snapshot fields remain `-1`.

## Sendspin compatibility

Sendspin Satellite uses `sendspin-cpp` v0.9.5 and requires encrypted Sendspin
connections (core protocol v1 and `player@v1`). The minimum validated Music
Assistant version is **2.10.7**. Earlier versions are unverified, and releases
that only support legacy cleartext Sendspin connections cannot connect. The app
has no cleartext fallback.

The first encrypted release changes the satellite's client identity from the
previous Android ID-based value. Music Assistant may show a new player entry;
remove the old entry and pair the new one if needed.

Compatible additive protocol extensions are tolerated where the Sendspin
specification defines them. A future core or role protocol version is supported
only after deliberate interoperability testing with Music Assistant; it is not
assumed compatible merely because it is newer.

### Compatibility validation

The earliest validated combination is **Music Assistant 2.10.7** on an
**Amazon Echo Show 5 (2nd generation)**. Pairing with the code shown by the app
succeeded and music played normally. Earlier server versions remain unverified;
the previous 2.10.2 cleartext test results do not apply to this encrypted-only
client.

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
