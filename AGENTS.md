# AGENTS.md

## Project summary

This project is an Android Sendspin client.

The goal is to build the **most reliable, no-nonsense Sendspin client for Android**.

The application should be simple, lightweight, intuitive, and suitable both for normal phone/tablet listening and for repurposing an Android device as a dedicated network speaker.

The primary reference use case is:

> An older phone or tablet is installed near a speaker, amplifier, or DAC, configured once, and then trusted to behave like a dependable Sendspin endpoint for long periods.

The UI is intentionally secondary to playback reliability.

---

## Product priorities

When making product or engineering trade-offs, prefer the following order:

1. Audio correctness
2. Playback reliability
3. Synchronization accuracy
4. Automatic recovery
5. Device compatibility
6. Resource efficiency
7. Ease of use
8. Maintainability
9. Visual polish
10. Feature count

If a proposed change makes the application more complicated without making it a better Sendspin player, reconsider it.

A useful decision test is:

> Does this make the Android device a better Sendspin player?

---

## Product philosophy

The application should be deliberately simple.

Avoid unnecessary:

- UI complexity
- animation
- navigation layers
- customization
- dashboards
- features unrelated to Sendspin playback
- architectural abstraction for its own sake

The desired user experience is approximately:

```text
Install
  ↓
Find or configure server
  ↓
Connect
  ↓
Play
  ↓
Keep working
```

For dedicated installations, the ideal long-term experience is:

```text
Configure once
  ↓
Leave device running
  ↓
Forget about it
```

The app should recover automatically from transient failures wherever practical.

---

## Target users and devices

The application should support two main usage patterns.

### Dedicated network speaker

This is the primary design reference.

Examples include:

- old Android phone connected to powered speakers;
- tablet connected to an amplifier;
- Android device connected to a USB DAC;
- permanently installed Android device acting as a Sendspin endpoint.

These devices may run continuously for days, weeks, or longer.

The application should therefore behave well when:

- the screen is off;
- the UI Activity is destroyed or recreated;
- Wi-Fi temporarily disappears;
- the server restarts;
- the audio route changes;
- the app has been running for a long period;
- Android lifecycle events occur.

### Normal mobile listening

The application should also work naturally when someone installs it on their everyday phone or tablet and listens through headphones, speakers, Bluetooth, or another normal Android audio route.

Dedicated-device behaviour should not make normal mobile use awkward.

---

## Android compatibility

Support relatively old Android hardware where technically practical.

Do not raise the minimum Android version merely for developer convenience.

The minimum SDK should be selected based on real technical requirements, particularly around:

- foreground playback;
- networking;
- native audio;
- lifecycle behaviour;
- security requirements.

Graceful fallbacks are preferable where practical.

---

# High-level architecture

The intended architecture is:

```text
Kotlin / Android
    │
    │ Application and control plane
    │
    ▼
JNI boundary
    │
    ▼
C++ Sendspin / real-time audio engine
    │
    ▼
Oboe
    │
    ▼
AAudio / Android audio system
```

The architectural principle is:

> Kotlin owns the application. C++ owns the timing-sensitive audio path.

---

## Kotlin responsibilities

Kotlin should generally own:

- Jetpack Compose UI;
- Android lifecycle integration;
- foreground service behaviour;
- permissions;
- settings;
- Android network-state awareness;
- audio focus;
- audio-route awareness;
- user-facing connection state;
- metadata presentation;
- diagnostics presentation;
- MediaSession integration where appropriate.

Do not move ordinary Android application logic into C++ without a concrete reason.

---

## C++ responsibilities

C++ should generally own components where deterministic timing and native audio control matter.

Expected native responsibilities include:

- Sendspin music clock;
- server/local clock translation;
- clock synchronization and drift estimation;
- timestamped audio scheduling;
- codec decoding;
- PCM buffering;
- playback timeline;
- synchronization correction;
- sample-rate conversion where required;
- native audio rendering;
- Oboe integration;
- audio callback;
- low-level playback diagnostics.

C++ is not being used because "C++ is faster."

It is being used because the Sendspin playback path behaves like a small real-time system and benefits from:

- deterministic memory ownership;
- preallocated buffers;
- predictable threading;
- direct Oboe access;
- low-level clock access;
- bounded queues;
- allocation-free audio callbacks;
- precise control of audio scheduling.

---

# JNI boundary

Keep JNI deliberately small.

JNI should expose coarse-grained engine operations and state rather than the internal audio pipeline.

Good examples:

```text
startEngine
stopEngine
connect
disconnect
setVolume
setMuted
getDiagnostics
```

Avoid APIs where Kotlin directly manages:

- PCM buffers;
- decoder calls;
- individual timing calculations;
- audio callback data;
- native ring buffers.

PCM audio should not cross JNI during normal playback.

Avoid high-frequency JNI calls where a native subsystem can own the complete operation instead.

---

# Networking

The exact networking location is not yet permanently decided.

Two architectures remain possible:

```text
Kotlin WebSocket
    ↓
JNI
    ↓
Native Sendspin audio engine
```

or:

```text
Native WebSocket
    ↓
Native Sendspin protocol
    ↓
Native audio engine
```

Do not assume networking must be native merely because the audio path is native.

The stronger reason for C++ is the path from:

```text
Sendspin clock
    ↓
audio timestamps
    ↓
decoder
    ↓
timeline
    ↓
sync correction
    ↓
Oboe
```

If networking begins in Kotlin, structure it so that moving transport into the native engine later remains possible.

No PCM should be passed through Kotlin.

---

# Audio engine rules

The audio callback is a real-time context.

It must not perform:

- network I/O;
- file I/O;
- blocking locks;
- sleeps;
- unbounded work;
- memory allocation under normal operation;
- codec decoding;
- complex logging.

Prefer:

- fixed-capacity buffers;
- preallocated storage;
- single-producer/single-consumer queues where appropriate;
- explicit ownership;
- bounded work;
- simple callback logic.

The callback should ideally consume prepared PCM and update lightweight timing information.

---

# Timing and synchronization

Clock synchronization is a first-class subsystem.

Do not treat Sendspin timestamps as ordinary packet metadata.

The playback engine must maintain an explicit mapping between:

```text
Sendspin server time
    ↓
client monotonic time
    ↓
audio output timeline
```

All playback scheduling should use the same clock abstraction.

Do not create independent clock-offset calculations in unrelated parts of the codebase.

The system should account for:

- clock offset;
- clock drift;
- output latency;
- hardware clock differences;
- network jitter.

Small synchronization errors should be corrected gradually and inaudibly.

Large errors should result in controlled resynchronization rather than indefinite attempts to recover broken state.

---

# Buffering

Do not optimize for the lowest possible latency at the expense of reliability.

This is a synchronized network music player, not a live musical instrument.

Buffering should prioritize:

- continuous playback;
- tolerance of realistic Wi-Fi jitter;
- predictable recovery;
- accurate scheduling.

Treat network buffering, decoded audio buffering, and hardware output buffering as separate concepts.

Likely logical stages include:

```text
compressed/network queue
        ↓
decoder
        ↓
timestamped PCM timeline
        ↓
render FIFO
        ↓
audio callback
```

Bound all queues.

---

# State management

Use explicit state machines for important playback and connection behaviour.

Likely states include:

```text
Stopped
Connecting
Handshaking
Synchronising
Ready
Buffering
Playing
Recovering
Error
```

Avoid distributing implicit playback state across unrelated boolean flags.

State transitions should be observable and testable.

---

# Stream generations

Old audio must never leak into a new Sendspin stream.

Use an explicit stream generation or equivalent mechanism around events such as:

- stream start;
- stream clear;
- reconnect;
- format change;
- hard resynchronization.

Queued data associated with an obsolete generation should be safely discarded.

---

# Failure handling

Failure is a normal condition in a network audio application.

Design explicitly for:

- Wi-Fi interruption;
- server restart;
- WebSocket loss;
- decoder error;
- malformed packets;
- underruns;
- audio-device restart;
- Bluetooth route changes;
- USB DAC removal;
- Android sleep/lifecycle events.

Prefer:

```text
detect invalid state
    ↓
reset affected subsystem
    ↓
re-establish known-good state
    ↓
resynchronise
    ↓
resume
```

over complicated attempts to preserve partially invalid internal state.

---

# Diagnostics

Diagnostics are part of the product architecture.

The native engine should expose inexpensive metrics that help diagnose playback problems.

Useful metrics may include:

- round-trip time;
- clock offset;
- clock drift;
- clock convergence state;
- network buffer depth;
- decoded buffer depth;
- output buffer depth;
- output latency;
- synchronization error;
- sample-rate correction;
- late packets;
- dropped packets;
- decoder failures;
- underruns/xruns;
- reconnect count;
- hard-resync count;
- playback uptime.

Keep diagnostics separate from the normal UI.

The normal UI should remain simple.

---

# Testing expectations

The native timing and playback logic should be designed for testing outside the Android UI where possible.

Abstract platform dependencies such as:

- clock source;
- transport;
- audio sink.

Prefer deterministic tests for behaviour such as:

- clock drift;
- network jitter;
- packet delays;
- dropped data;
- output-device drift;
- stream clears;
- reconnection;
- long-running playback.

Long-duration soak testing is important.

The system should not accumulate synchronization, memory, queue, or timing errors over time.

---

# Preferred technologies

Unless later architectural decisions change this explicitly:

```text
Language — Android:
Kotlin

UI:
Jetpack Compose

Application concurrency:
Kotlin Coroutines / Flow

Native language:
C++20

Native build:
Android NDK + CMake

Native audio:
Oboe

Underlying modern Android audio API:
AAudio

Application lifecycle:
Foreground playback service
```

Do not introduce a major framework or dependency without explaining why it improves the core product.

---

# Coding principles

Prefer code that is:

- explicit;
- boring;
- testable;
- deterministic;
- measurable;
- easy to debug.

Avoid speculative abstractions.

Do not build infrastructure for hypothetical future features unless the current architecture genuinely requires it.

For timing-sensitive native code, favour correctness and predictable behaviour over cleverness.

For Kotlin application code, favour standard Android patterns over unnecessary custom frameworks.

---

# Product scope

This project is not intended to become:

- a general-purpose music player;
- a music library manager;
- a streaming-service frontend;
- a media server;
- an audio-production application;
- a general DSP playground;
- a complex home-automation dashboard.

The application exists to be an excellent Sendspin player.

---

# Current release direction

Initial releases are expected to be APKs aimed at technically interested users and keen amateurs.

Early versions may have:

- limited settings;
- a minimal UI;
- engineering diagnostics;
- manual installation.

They should not have unreliable playback.

The long-term goal is a polished application distributed through Google Play and regarded as the default reliable Sendspin client for Android.

---

# When uncertain

When an implementation decision is unclear, optimize for the following question:

> If this Android device were installed beside a speaker and left unattended for a month, which design would I trust more?

Choose that design.