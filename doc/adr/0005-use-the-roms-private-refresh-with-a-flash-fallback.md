# 5. Use the ROM's private refresh with a flash fallback

Date: 2026-11-04

## Status

Accepted

## Context

E-ink panels ghost, so the owner asked for a built-in refresh when moving between preview and edit. Android has no public API for this. Disassembly shows SystemUI calling `forceClear()` on this ROM's private `epd` service through reflection. Whether a normal app is allowed to make that call has not been tested, because the device was in use when the test was due.

## Decision

We will try the same `forceClear()` call by reflection, and if it fails, fall back to flashing the screen black, then white, then back to the content. We will accept a refusal rather than try to work around one. After one failure, the app stops trying for that session.

## Consequences

If the call is allowed, the app gets the panel's own full refresh. Otherwise the flash still works on any device, though it may clear ghosting less completely. A call that succeeds but has no visible effect would not trigger the fallback, so this must be checked by eye. The app depends on an undocumented interface that a firmware update could remove, but failure only degrades to the flash. Removing this later touches one class.
