# Opus codec implementation strategy

## Goal and scope

Add reliable Sendspin Opus playback after the established PCM path, without
changing the Kotlin control plane or moving PCM across JNI. The initial format
scope should be negotiated 48 kHz, stereo Opus decoded to interleaved signed
16-bit PCM for the existing Oboe output. Do not add surround, custom
resampling, DSP, or a codec-selection UI in the first release.

The completed PCM path is the baseline: the native engine advertises only
48 kHz, stereo, 16-bit PCM; the Sendspin listener writes to its bounded,
generation-aware FIFO; and the Oboe callback only drains that FIFO. Opus must
preserve these boundaries.

## Implementation order

1. **Establish the dependency and protocol contract.** Inspect the pinned
   `sendspin-cpp` submodule revision to verify its Opus negotiation, packet
   delivery, decoding, format reporting, error, and stream-clear behaviour.
   Confirm the exact Sendspin Opus format fields with an interoperable server.
   Identify whether the library already uses a maintained `libopus`, or whether
   it needs an upstream change. Record the library version, license, Android
   ABI support, update process, and security ownership before adding a native
   dependency.
2. **Run a narrow native spike.** Add Opus capability alongside PCM only in a
   test build. Verify that a negotiated Opus stream arrives as decoded,
   48 kHz/stereo/signed-16 PCM at the listener boundary. If it instead delivers
   compressed packets, implement a bounded compressed-packet queue and decode
   worker outside the Oboe callback; it must feed the existing PCM FIFO and
   never expose PCM to Kotlin.
3. **Make negotiation and format handling explicit.** Advertise only the Opus
   profiles actually supported by the decoder/output path. Keep PCM available
   during staged interoperability testing, and ensure the selected codec and
   output format are observable. Reject unsupported channel counts, sample
   layouts, malformed format metadata, and unexpected decoder output before it
   reaches the render FIFO.
4. **Preserve stream and recovery correctness.** Treat stream start, clear,
   reconnect, decoder reinitialization, format change, and hard resync as
   generation boundaries. Discard obsolete compressed and decoded data. Decoder
   failures or irrecoverable queue corruption must enter the existing bounded
   recovery path rather than replaying or indefinitely retaining audio.
5. **Add operational diagnostics.** Extend the fixed native snapshot with the
   selected codec/profile, compressed and decoded queue depth, packets/bytes
   decoded, partial writes or drops, decode failures, late packets, packet-loss
   concealment events when available, decoder resets, and decoder CPU timing
   sampled outside the real-time callback. Keep the normal UI unchanged.
6. **Test from deterministic units through physical devices.** Cover valid and
   invalid format negotiation, malformed/truncated packets, queue saturation,
   packet loss/jitter/reordering, decoder reset, stream clear, reconnect, route
   recovery, and stale-generation rejection in host tests. Then verify
   interoperability and long-running playback on representative old Android
   hardware, wired output, Bluetooth, and a USB DAC.
7. **Release gradually.** Ship only after the PCM fallback and an Opus session
   both pass the existing recovery and soak gates. Start with a compatibility
   matrix for known server versions and physical devices; keep unsupported
   source formats unavailable rather than silently converting them poorly.

## Required invariants

- The Oboe callback remains allocation-free, non-blocking, and decoder-free.
- Codec work happens on a bounded non-real-time path with preallocated or
  bounded storage; network input cannot grow memory without limit.
- The output receives only the format it is configured to render. Any decode,
  channel mapping, or sample conversion occurs before the render FIFO.
- PCM and compressed audio never cross JNI during normal playback.
- A partial FIFO write has defined backpressure behaviour; it must not silently
  drop a packet tail or desynchronize the decoder timeline.
- Codec failure follows the existing observable recovery state machine, and old
  generations can never become audible after a reset.

## Issues and performance concerns

| Concern | Why it matters | Required mitigation or decision |
| --- | --- | --- |
| Upstream capability is unverified | The checked-in engine depends on a pinned `sendspin-cpp` submodule, but the current Android code only advertises PCM. Assuming that Opus is decoded or negotiated upstream could produce no audio or incompatible streams. | Make the dependency/protocol spike a hard gate before changing the app pipeline. |
| Decoder placement | Decoding in `OboePcmOutput::onAudioReady` would cause callback overruns, glitches, and poor reliability on old devices. | Decode before the PCM FIFO on a bounded non-real-time path only. |
| CPU, thermal, and battery pressure | Opus uses materially more CPU than PCM; this app's native engine currently loops at a 5 ms cadence. Extra allocations, busy work, or decode bursts can hurt older dedicated devices. | Measure decode time and underruns on target hardware, keep work bounded, do not increase callback or loop frequency, and retain adequate buffering. |
| FIFO backpressure | The current FIFO holds two seconds at 48 kHz and its listener can report a partial write. Incorrect handling of a full FIFO can lose decoded frames or leave an Opus decoder timeline inconsistent. | Define packet/PCM ownership and retry/drop policy, cap all upstream queues, and test saturation and recovery. |
| Format mismatch | The renderer is fixed to 48 kHz, stereo, signed-16 PCM. Unsupported channels, layouts, or output sample representations can cause distortion, resampling overhead, or failed playback. | Restrict the first negotiated profile to the native renderer's exact format; defer broader formats until a measured need exists. |
| Packet loss and jitter | Opus can conceal loss, but only if packet ordering/timing and the decoder API are handled correctly. Misuse causes artifacts, runaway latency, or broken synchronization. | Verify Sendspin packet semantics and the upstream decoder's loss-concealment contract; bound late-packet handling and hard-resync large errors. |
| Native dependency size and maintenance | Bundling `libopus` for Android ABIs increases APK size, build time, CVE exposure, and release maintenance. | Prefer verified upstream integration; otherwise pin, scan, update, and build only required ABIs. |
| Decoder errors and untrusted input | Malformed compressed data must not crash or wedge a long-running endpoint. | Validate lengths and format metadata, count failures, reset or recover deterministically, and add malformed-input tests. |
| `todo.md` filename collision | This repository already has `TODO.md`. A second file named `todo.md` works on this Linux checkout but collides on common case-insensitive macOS and Windows filesystems. | Keep the requested file for this task, but consolidate or rename the two documents before cross-platform development workflows rely on both. |

## Exit criteria

Opus is ready only when a real compatible server negotiates and plays the
supported profile, PCM fallback remains functional, the native host tests and
short/long soak tests pass, and physical-device testing shows no callback
underruns, unbounded queue growth, stale audio, thermal regression, or failed
recovery through network and route changes.
