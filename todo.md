# Opus codec development backlog

> **Repository concern:** `TODO.md` already exists. This requested lowercase
> `todo.md` collides with it on case-insensitive filesystems; consolidate or
> rename these files before relying on both in macOS or Windows worktrees.

## Discovery and decisions

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

## Native implementation

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

## Diagnostics and validation

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
