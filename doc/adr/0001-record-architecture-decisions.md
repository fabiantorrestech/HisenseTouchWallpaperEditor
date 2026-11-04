# 1. Record architecture decisions

Date: 2026-11-04

## Status

Accepted

## Context

This project is new and is worked on across separate agent sessions, so the reasons behind costly choices would otherwise be lost between sessions. The handoff file is deliberately uncommitted and short-lived, so it cannot hold them.

## Decision

We will record decisions that are costly to reverse as Architecture Decision Records in `doc/adr/`, in the format described by Michael Nygard in "Documenting Architecture Decisions". Records are append-only and are changed only by superseding them.

## Consequences

Future sessions can see why the app is shaped the way it is without reconstructing it. Each costly decision now takes a short extra writing step, and the index in `doc/adr/README.md` has to be regenerated whenever a record is added.
