# Protocol compatibility memory

## Current handshake target

Phase 1 currently handshakes and identifies itself compatibly with the
`aiosendspin` server implementation used for integration testing.

This includes compatibility behaviour for an older handshake variant which
omits `psk_category`, plus the `aiosendspin` `client/hello` schema expected by
that server.

## Future compatibility work

Sendspin is evolving. Do not treat the current `aiosendspin` wire behaviour as
the only Sendspin implementation the app must support.

Before changing the protocol layer, keep these principles in mind:

- Prefer the current published Sendspin protocol for new behaviour.
- Preserve tested compatibility paths for deployed older servers where safe.
- Make a protocol-version or feature difference explicit in code; do not rely
  on undocumented assumptions.
- Test against more than one server implementation/version as they become
  available.
- Keep handshake, hello, activation, and player-state compatibility decisions
  together in the protocol layer, with diagnostics that identify the failed
  stage.

This document is engineering memory, not a replacement for the Sendspin
specification or server-specific release notes.

## Phase 6 implementation notes

- The parent native engine owns reconnect policy; the pinned `sendspin-cpp`
  transport auto-reconnect remains disabled.
- The service forwards validated default-network state to native code. Native
  recovery clears output, advances the generation, resets clock diagnostics,
  and retries until explicit Stop.
- The JNI diagnostics contract is one fixed 17-element snapshot. Oboe latency
  is reported when available; clock error, convergence, and sample count are
  populated from the public time-sync callback.
- The pinned public transport API does not expose true RTT, clock offset, or
  clock drift accessors. Keep those snapshot values at `-1` rather than using
  private submodule internals.
- The host recovery tests and 24-hour virtual soak pass with direct `g++`;
  the 15-minute soak is registered in the CMake/CTest workflow for CI.
