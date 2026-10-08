# Feature Specification: Supervision Watch

**Feature**: `002-supervision-watch` — **Status**: Draft — **Created**: 2026-10-02

## Context

Feature 001 captures the root `Engine` and hands it to an observer. When the agent runs with
`supervise=true`, every engine registers a `SimulaSupervisor` actor that records the lifecycle
status (`STARTED`/`STOPPED`) of every actor of its engine in a thread-safe state map. The framework
now exposes a supervision listener on `SimulaSupervisor` (see
`contracts/supervision-listener.md`).

## User stories

- **US-001**: As a developer using the agent API, I want to receive one event per actor status
  change of any engine in the captured tree, so that I can follow the simulation lifecycle without
  touching framework internals.
- **US-002**: As an observer author, I want registration to be safe: my callback exceptions never
  reach the simulation threads and a missing supervision listener API degrades silently.
- **US-003**: As the JavaFX demo user, I want actor boxes to be color-coded by lifecycle status,
  updated live.

## Functional requirements

- **FR-101**: The agent SHALL expose `jpnco.simula.agent.api.SupervisionEvent` describing one
  status change: engine, actor, previous status (`null` on first record), current status
  (`null` on disappearance).
- **FR-102**: The agent SHALL expose `SupervisionWatcher.start(Engine, Consumer<SupervisionEvent>)`
  which attaches a framework `SupervisionListener` to every `SimulaSupervisor` discovered in the
  engine tree (root and all children, recursively, including children and supervisors that appear
  later) on a daemon thread. Discovery is event-driven: the tree is walked once, a late engine is
  picked up from the supervision callback that records it as an actor, and a one-second full-tree
  rescan is the fallback for the `addChild`-without-registration case no callback can announce.
- **FR-103**: The watcher loop SHALL follow the listen/iterate-then-drain pattern: framework
  callbacks only enqueue `(supervisor, actor)` hints; each cycle first iterates the authoritative
  `getStates()` maps (diff vs last known snapshot) and only then drains the hint queue, so a change
  that lands mid-iteration is never lost and never delivered twice for the same cycle.
- **FR-104**: Every listener dispatch SHALL be exception-contained (Constitution IX): a throwing
  consumer is logged and the watcher continues.
- **FR-105**: When a supervisor disappears from the tree (engine stopped/removed), the watcher SHALL
  detach its listener and forget its snapshot.
- **FR-106**: The JavaFX demo SHALL render each actor box with `started`/`stopped` style classes
  driven by the watcher events (via a ledger queue drained on the FX thread).
- **FR-107**: An engine is itself an actor that self-records in its own supervisor but never appears
  in its own `getActors()` list; the demo SHALL surface engine status on the engine's tree cell.
- **FR-108**: Every actor/engine box in the demo SHALL offer a right-click `Stop` menu that sends the
  framework's cooperative stop request to the represented actor (`Actor.stopMe()`) or engine
  (`Engine.stop()`); the request never throws on the FX thread.
- **FR-109**: The engine tree SHALL also show each engine's registered actors as leaf nodes under
  the engine, with the gear icon (rocket for engines), the same status coloring, and selection of an
  actor node showing the pane of the engine it belongs to.
- **FR-110**: The framework records actors present when a supervisor is built and publishes no
  lifecycle event for a plain actor registered later; the watcher SHALL reconcile raw membership to
  announce such actors synthetically (`null -> STARTED` when present in `getActors()` but absent
  from the supervisor states, `STARTED -> STOPPED` when gone from both).

## Non-functional

- Zero change to the host application; watcher thread is a daemon; no retention after engines stop.
- Coverage gate 97% line/branch still enforced on all new non-FX code.

## Out of scope

- Actor business state (only supervisor lifecycle status).
- Framework-side changes (contract already implemented and installed).
