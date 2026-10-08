# Implementation Plan: Supervision Watch

**Feature**: `002-supervision-watch` — see `spec.md` and `contracts/supervision-listener.md`.

## Technical context

- Java 25, ByteBuddy agent (unchanged), typed compile dependency on `jpnco:simula` (provided).
- Framework contract already implemented and installed: `SupervisionListener`,
  `SimulaSupervisor.add/removeSupervisionListener`, `ConcurrentHashMap` states.

## Design

- `api.SupervisionEvent` — immutable record of one change (engine, actor, previous, current).
- `api.SupervisionWatcher` — discovery + listen/iterate-then-drain loop (FR-102..FR-105):
  daemon thread (`start`), `cycle()` = discover supervisors across the engine tree (recursive over
  `getChildren`), attach a per-supervisor framework listener that only enqueues
  `(supervisor, actor)` hints, diff each `getStates()` snapshot vs the per-supervisor `lastSeen`
  map, detach supervisors that left the tree, then drain the hint queue re-reading the live map
  (mid-iteration changes are reported by the drain; already-synced hints deduplicate).
  Dispatch is exception-contained; cycles are exception-contained (Constitution IX).
- `observers.StatusLedger` — queue hand-off from the watcher thread to the FX thread.
- `observers.FxObserver` arms the watcher into the ledger before launching the FX app;
  `MainViewController` drains it on the FX refresh tick into an `ObservableMap` and colors actor
  boxes via `started`/`stopped` style classes.

## Testing

- `SupervisionWatcherTest`: real `SimulaSupervisor` over mocked `Engine`s; first record,
  transitions, hint drain + dedup, map disappearance, recursive discovery, detach/reattach,
  contained sink failures, contained cycle failures, interrupt exit, null guards.
- `StatusLedgerTest`: order, drain-empties, null ignored, singleton.
- `SupervisionIT`: real JVM, `supervise=true`, real engine start/stop, asserts `->STARTED` and
  `->STOPPED` event lines.

## Coverage

New production code is covered by unit tests; FX view classes stay in the existing JaCoCo
exclusions. Gate 0.97 line/branch unchanged.
