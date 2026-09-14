# Sendspin Android Client — North Star

## Product vision

Build the **most reliable, no-nonsense Sendspin client for Android**.

The application should do one thing exceptionally well:

> Turn an Android device into a dependable Sendspin audio player.

It should be simple enough that somebody can install it, connect it to their Sendspin environment, and then largely forget that the application exists.

Reliability, predictability, and audio quality matter more than visual novelty or feature count.

---

## What this app is

This is a dedicated Sendspin player for Android.

It should work equally well when Android is being used as:

- a permanently installed network audio endpoint;
- an old tablet repurposed as a network speaker;
- an old phone connected to an amplifier, DAC, powered speaker, or stereo;
- a small dedicated Android-based audio appliance;
- a normal phone or tablet used for personal listening.

The primary mental model, however, is not:

> "a music app running on my phone."

It is:

> **"an Android device acting as a reliable network speaker."**

That distinction should influence almost every product and engineering decision.

The app should be a **thin Android endpoint**: after a user configures and
enables it, its UI is optional. The foreground playback service keeps the
Sendspin client ready to receive and recover playback while the Activity is
closed or removed from Recents.

---

# The north star

The ideal experience is deliberately uneventful.

A user installs the application, opens it, connects it to a Sendspin server, and audio plays.

From that point onwards the client should:

- remain synchronized;
- remain connected where possible;
- recover cleanly when connections are interrupted;
- continue working for long periods without intervention;
- consume sensible amounts of CPU, memory, network bandwidth, and battery;
- behave predictably when Android lifecycle or audio-device events occur.

The ordinary Android boundary is explicit: the app must not claim to restart
audio unattended after a device reboot or a user Force Stop. A user activates
the endpoint once after restart; from then on it should again be forgettable.

The user should not need to understand audio buffering, synchronization, codecs, sample rates, network jitter, or Android audio APIs.

Those are engineering concerns, not user concerns.

---

# Product principles

## Reliability before features

A small application that plays correctly for days is more valuable than a large application with many features and occasional playback problems.

New functionality should not compromise the stability of the playback engine.

When deciding between:

- more features; and
- greater reliability;

reliability wins.

---

## Simple by default

The primary interface should remain extremely small.

The application should not become a general-purpose music player, media browser, or control centre unless there is a compelling Sendspin-specific reason to do so.

A typical user should need very little interaction beyond:

```text
Open app
   ↓
Find or enter Sendspin server
   ↓
Connect
   ↓
Play
```

Once configured, even this may be more interaction than necessary.

A dedicated device should ideally be capable of launching the application and reconnecting automatically.

---

## No unnecessary visual complexity

The UI should be functional, calm, and easy to understand.

It does not need:

- elaborate animations;
- complex navigation;
- decorative dashboards;
- extensive customization;
- visual effects that provide no functional benefit.

A small amount of well-designed information is preferable to a large amount of information.

The interface should communicate the state of the player clearly:

```text
Disconnected
Connecting
Synchronising
Buffering
Playing
Reconnecting
Error
```

The user should always be able to understand what the player is currently doing.

---

## Support ordinary Android hardware

The client should not require a flagship phone or the latest Android release.

Where technically practical, it should support relatively old Android devices because those devices are particularly attractive as dedicated network-audio endpoints.

An unused phone or tablet should be a viable Sendspin player.

Supporting older hardware does not mean compromising the correctness of the audio engine. It means avoiding unnecessary platform requirements and designing graceful fallbacks where possible.

The minimum Android version should therefore be chosen based on genuine technical requirements rather than convenience.

---

## Treat dedicated devices as a first-class use case

The application should assume that some installations will operate continuously.

Examples include:

```text
Android tablet
      │
      USB
      ▼
     DAC
      │
      ▼
 Amplifier / speakers
```

or:

```text
Old Android phone
      │
   headphone
      │
      ▼
Active speakers
```

or:

```text
Android device
      │
   Bluetooth
      │
      ▼
Speaker system
```

These devices may spend weeks or months serving primarily as Sendspin endpoints.

The application should therefore behave well when:

- the screen is off;
- the application UI has not been opened recently;
- Android recreates the Activity;
- the network briefly disappears;
- Wi-Fi reconnects;
- the Sendspin server restarts;
- the output device changes;
- the application runs for very long periods.

---

## Still work naturally as a phone app

Although dedicated devices are the primary design reference, the client should not feel strange when used normally on a phone.

Someone should be able to install it, connect headphones, and listen through Sendspin without configuring the device as a permanent appliance.

The same core player should support both use cases.

This means dedicated-device functionality should generally be additive:

- automatic reconnection;
- persistent playback;
- start-on-launch behaviour;
- background operation;

rather than making normal phone use awkward.

---

# Audio is the product

The most important part of the application is not the UI.

It is the audio path.

Success should primarily be measured by:

- how reliably playback continues;
- how accurately players remain synchronized;
- how rarely audio glitches occur;
- how well the client handles poor but realistic network conditions;
- how cleanly it recovers from interruptions;
- how efficiently it streams and decodes audio.

Engineering effort should reflect that priority.

A disproportionately large amount of development time spent improving playback reliability is entirely justified.

---

# No-nonsense engineering

The implementation should favour understandable and deterministic systems over clever ones.

Prefer:

- explicit state machines;
- bounded queues;
- measurable behaviour;
- clear ownership;
- boring interfaces;
- good diagnostics;
- recoverable components;
- extensive automated tests.

Avoid unnecessary abstraction, framework complexity, or architectural fashion.

The application should remain maintainable by a relatively small number of contributors.

---

# Protocol compatibility boundary

The client supports the encrypted Sendspin protocol generation (core protocol
v1 and `player@v1`). Earlier pre-encryption Sendspin implementations are not
supported.

The client should tolerate compatible additive extensions defined by the
protocol. Future core or role protocol versions require deliberate
interoperability testing with Music Assistant before support is claimed.

This keeps the connection, identity, and pairing model small and dependable,
instead of carrying legacy protocol paths into the playback product.

---

# Diagnostics without clutter

Most users should never need to see detailed diagnostics.

Developers and advanced users, however, should have access to enough information to understand playback problems.

A secondary diagnostics screen may expose information such as:

```text
Server
Connected

Stream
Opus / 48 kHz / Stereo

Clock sync
Healthy

Buffer
186 ms

Synchronization error
0.3 ms

Underruns
0

Reconnects
1

Uptime
3d 14h
```

This information should remain separate from the primary UI.

The normal interface should stay simple.

---

# Graceful failure

Network audio systems inevitably experience failures.

The client should treat failure as a normal operating condition rather than an exceptional programming event.

When something goes wrong, it should favour:

```text
detect
  ↓
stop invalid state
  ↓
recover
  ↓
resynchronise
  ↓
resume
```

rather than attempting indefinitely to preserve partially broken state.

The player should recover automatically where doing so is safe and sensible.

User intervention should be required only when the application genuinely cannot determine how to recover.

---

# Efficient by design

Efficiency matters because many installations may use older or lower-powered Android hardware.

The client should avoid unnecessary:

- CPU usage;
- memory allocation;
- network traffic;
- wakeups;
- background processing;
- battery consumption.

Efficiency should primarily come from good architecture rather than premature micro-optimization.

The native playback engine should be optimized where deterministic performance matters, while ordinary Android functionality should remain straightforward Kotlin.

---

# Release philosophy

## Initial releases

The first releases are expected to be distributed primarily as APKs to technically interested Sendspin users.

These users are likely to tolerate:

- manual APK installation;
- limited settings;
- minimal documentation;
- diagnostic interfaces intended for testing.

They should not, however, be expected to tolerate unreliable playback.

The early releases should already embody the core product promise:

> **simple interface, excellent Sendspin playback.**

The purpose of early releases is to validate and harden the playback engine across real Android hardware.

---

## Long-term goal

The long-term goal is a polished application available through the Google Play Store.

It should be the Android Sendspin client that people recommend when someone asks:

> "What should I install if I just want an Android device to behave like a reliable Sendspin player?"

The desired reputation is:

- dependable;
- lightweight;
- uncomplicated;
- efficient;
- well-engineered;
- boring in the best possible way.

Users should trust it enough to leave it running unattended.

---

# What we are not building

Maintaining a clear boundary around the product is important.

This project is not intended to become:

- a general music-library application;
- a streaming-service client;
- a media server;
- an audio-production workstation;
- a DSP playground;
- a highly customizable dashboard;
- a social music application;
- a replacement for the Sendspin server.

Features should be evaluated against the core question:

> **Does this make the Android device a better Sendspin player?**

If the answer is no, the feature probably does not belong in the core application.

---

# Decision hierarchy

When product or technical decisions conflict, use the following order of priorities:

1. **Audio correctness**
2. **Playback reliability**
3. **Synchronization accuracy**
4. **Automatic recovery**
5. **Device compatibility**
6. **Resource efficiency**
7. **Ease of use**
8. **Maintainability**
9. **Visual polish**
10. **Feature count**

This hierarchy is intentionally unusual for a consumer Android application.

It reflects the purpose of the product.

---

# Definition of success

The project has succeeded when an inexpensive or old Android device can be placed next to a speaker, configured once, and then trusted as part of a Sendspin audio system.

The ideal user experience is:

```text
Install
   ↓
Connect
   ↓
Place device next to speaker
   ↓
Forget about it
```

Weeks later, it should still be doing its job.

That is the north star.
