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
