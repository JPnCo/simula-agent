# Tasks: Supervision Watch

**Feature**: `002-supervision-watch`

## Phase 1 — Contract & API

- [x] T001 Document framework supervision listener contract (`contracts/supervision-listener.md`)
- [x] T002 [P] Add `api/SupervisionEvent` record (FR-101)
- [x] T003 [P] Add `observers/StatusLedger` queue hand-off (FR-106)

## Phase 2 — Watcher

- [x] T004 Implement `api/SupervisionWatcher`: discovery, attach/detach, snapshot diff,
      hint queue, iterate-then-drain, containment (FR-102..FR-105)

## Phase 3 — Demo integration

- [x] T005 Arm watcher in `FxObserver`, drain ledger + status styles in `MainViewController`/CSS
      (FR-106)

## Phase 4 — Verification

- [x] T007 `SupervisionWatcherTest` (discovery, diff, drain dedup, disappearance, containment,
      interrupt, null guards)
- [x] T008 `StatusLedgerTest`
- [x] T009 `SupervisionIT` + fixtures (`SupervisedMain`, `WatchObserver`)
- [x] T010 `mvn verify` green (fmt check, unit, IT, JaCoCo 0.97)
- [x] T011 Color the engine's tree cell from the same status map (FR-107, engine self-record)
- [x] T012 Status keyed by actor id; pane renders the engine itself and child engines (`addChild`
      does not register them as actors), IT covers child-engine supervision
- [x] T013 Right-click `Stop` menu on every box via `observers/StopRequest` (FR-108, actor
      `stopMe` / engine `stop`), with `StopRequestTest`
- [x] T014 Event-driven discovery: one tree walk, engine-valued callbacks trigger targeted subtree
      scans, one-second safety rescan (`SupervisionWatcher`)
- [x] T015 Stopped/unregistered actors keep their box, recolored red (disappearance reads as
      `STOPPED`; pane remembers actors, boxes keep their position)
- [x] T016 Tree synchronization is additive: a stopped engine keeps its tree item, red
- [x] T017 Icons: rocket for engines, gear for plain actors, tinted by status — on boxes and on
      engine-tree nodes
- [x] T018 Tree shows each engine's registered actors as leaf nodes (FR-109), gear icon, status
      color, selection of an actor shows its engine's pane
- [x] T019 Synthetic membership reconciliation in `SupervisionWatcher` for late-registered actors
      never reported by the framework (FR-110), with unit tests

## Dependencies & notes

- [P] tasks = different files, no dependencies
- Depends on framework contract being installed in the local repository (done).
