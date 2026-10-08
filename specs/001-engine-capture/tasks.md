---

description: "Task list for the Root Engine Capture Agent feature"
---

# Tasks: Root Engine Capture Agent

**Input**: Design documents from `/specs/001-engine-capture/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests ARE included. The constitution mandates Test-First (Principle I)
and ≥97% line and branch coverage (Principle II). Tests MUST be written and
confirmed to FAIL before implementation.

**Organization**: Tasks are grouped by user story to enable independent
implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Single project: `src/main/java/`, `src/test/java/` at repository root
- Code under `src/main/java/jpnco/simula/agent/`

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Build skeleton so all code compiles, formats, and gates run.

- [x] T001 Create `pom.xml`: Java 25, `jpnco:simula-agent:0.0.1-SNAPSHOT` jar,
      deps (byte-buddy + byte-buddy-agent runtime, `jpnco:simula` provided,
      junit-jupiter + mockito test), `maven-shade-plugin` relocating
      `net.bytebuddy` to `jpnco.simula.agent.shaded.bytebuddy` and injecting
      manifest `Premain-Class: jpnco.simula.agent.SimulaAgent`,
      `fmt-maven-plugin` bound to `verify`, JaCoCo ≥97% line/branch gate with
      declared exclusions (`SimulaAgent.class`, `capture/EngineCaptureAdvice.class`),
      `maven-dependency-plugin` properties exposing the `simula` jar path for
      integration tests; create `.gitignore` (target/, .opencode state)

**Checkpoint**: `mvn verify` passes on the empty skeleton; gates active.

---

## Phase 2: User Story 1 - Observe a running simulation from inside it (Priority: P1) MVP

**Goal**: Construction-time capture of root engines into the registry, exposed
through the typed accessor (FR-001..FR-004, FR-009; SC-001, SC-002).

### Tests for User Story 1 (write FIRST, confirm FAIL before implementation)

- [x] T002 [P] [US1] Write failing unit tests for `AgentOptions` (parsing
      `observer=`/`timeout=`, case-insensitivity, trimming, defaults 30 s/none,
      malformed pairs and unknown keys skipped with one warning, bad timeout →
      default+warning) in
      `src/test/java/jpnco/simula/agent/AgentOptionsTest.java`
- [x] T003 [P] [US1] Write failing unit tests for `EngineBlackboard` (create is
      idempotent; append order preserved; `snapshot()` immutable copy;
      `currentRoot()` = last entry that is an `Engine`, skipping non-`Engine`
      entries; empty board → null; concurrent appends all visible) in
      `src/test/java/jpnco/simula/agent/EngineBlackboardTest.java`
- [x] T004 [P] [US1] Write failing unit tests for `AgentLogger` (prefix
      `[simula-agent]`, level filtering, never throws on null/odd arguments) in
      `src/test/java/jpnco/simula/agent/AgentLoggerTest.java`
- [x] T005 [P] [US1] Write failing unit tests for `api.SimulaTarget`
      (`currentRootEngine()` null on empty board; returns mock `Engine` after
      append; `isAvailable()`; `awaitRootEngine` returns immediately when
      present, returns `null` after timeout, returns as soon as an engine is
      appended mid-wait) in
      `src/test/java/jpnco/simula/agent/api/SimulaTargetTest.java`
- [x] T006 [P] [US1] Write failing tests for `capture.EngineTransformer` using a
      synthetic `EngineLike` fixture with the canonical constructor shape
      (transform it at test time; constructing with `null` parent appends `this`
      to the blackboard; non-null parent appends nothing; advice never throws
      even when the board is absent) in
      `src/test/java/jpnco/simula/agent/capture/EngineTransformerTest.java`

### Implementation for User Story 1

- [x] T007 [US1] Implement `jpnco/simula/agent/AgentOptions.java` (FR-008)
- [x] T008 [US1] Implement `jpnco/simula/agent/EngineBlackboard.java` with the
      named property-key constant and `java.base`-only API (FR-002, FR-009)
- [x] T009 [US1] Implement `jpnco/simula/agent/AgentLogger.java` (R5)
- [x] T010 [US1] Implement `jpnco/simula/agent/api/SimulaTarget.java` (typed
      `Engine` accessor + wait) (FR-003, FR-004)
- [x] T011 [US1] Implement `jpnco/simula/agent/capture/EngineCaptureAdvice.java`
      (inline advice, `Object`-typed parameters, `suppress = Throwable.class`,
      root test on argument 1) and `capture/EngineTransformer.java`
      (AgentBuilder wiring on the `EngineImpl` descriptor + soft-failure
      listener) (FR-002, FR-007; capture-contract.md)
- [x] T012 [US1] Implement `jpnco/simula/agent/SimulaAgent.java` `premain`:
      parse options, create blackboard, install transformer, start observer
      thread only when `observer` configured (FR-001)

**Checkpoint**: Unit suite green; a manual `java -javaagent:… --module-path
<simula.jar> …` run of a scratch app exposes the engine via `SimulaTarget` (MVP).

---

## Phase 3: User Story 2 - Wait for the engine and cope with its absence (Priority: P2)

**Goal**: Observer hook with exactly-one-outcome semantics (FR-005, FR-006;
waiting and absence edge cases).

### Tests for User Story 2 (write FIRST, confirm FAIL before implementation)

- [x] T013 [P] [US2] Write failing unit tests for `ObserverRunner` (engine
      appears → `engineAvailable` exactly once with that engine; timeout →
      `engineUnavailable` exactly once; unknown observer class / missing
      no-arg ctor / wrong type / observer throws → single `[simula-agent]` log,
      no exception escapes, session still terminal; no observer configured →
      runner does nothing) in
      `src/test/java/jpnco/simula/agent/ObserverRunnerTest.java`

### Implementation for User Story 2

- [x] T014 [US2] Implement `jpnco/simula/agent/api/SimulaObserver.java`
      (contract per observer-api.md)
- [x] T015 [US2] Implement `jpnco/simula/agent/ObserverRunner.java` (daemon
      thread, wait via `SimulaTarget.awaitRootEngine`, one-time reflective class
      load per R4, terminal-state session) (FR-005, FR-006)

**Checkpoint**: User Stories 1 AND 2 both work independently.

---

## Phase 4: User Story 3 - Stay harmless across framework evolution (Priority: P3)

**Goal**: Provable soft degradation in real JVMs (FR-007; SC-003; the no-engine
and drift outcomes).

### Implementation for User Story 3

- [x] T016 [P] [US3] Create integration fixtures in
      `src/test/java/jpnco/simula/agent/integration/fixture/`:
      `SimulaAppFixture` (tiny deterministic app: root `EngineImpl` + one child
      engine + ~3 simulated seconds, prints a fixed OUTCOME block to stdout),
      `ObserverFixture` (implements `SimulaObserver`; writes capture evidence
      lines `ENGINE <name>` and `TIME t=<n>` to a file given by a system
      property), `PlainAppFixture` (non-simula `main` printing one line)
- [x] T017 [US3] Implement `src/test/java/jpnco/simula/agent/integration/EngineCaptureIT.java`
      spawning real JVMs: (a) agent+observer on SimulaAppFixture → evidence file
      contains `ENGINE` then `TIME` lines (SC-001); (b) agent on
      SimulaAppFixture without observer and fixture WITHOUT agent → stdout and
      exit code byte-identical (SC-002); (c) agent+observer on
      PlainAppFixture → exit 0, no engine file, exactly one
      `[simula-agent]` diagnostic line (SC-003); (d) child engine constructed by
      the fixture does NOT appear in the evidence (root-only rule)

**Checkpoint**: All three success criteria proven end-to-end.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [x] T018 Create root `architecture.md` (Mermaid: context/dataflow of
      app JVM, agent classes, blackboard; no historical info) (Constitution VIII)
- [x] T019 Confirm JaCoCo ≥97% line and branch for the core packages; keep the
      two documented exclusions; add tests to close any gap (Constitution II)
- [x] T020 Add FR/SC requirement citations to the Javadoc of every production
      class and method (Constitution VI)
- [x] T021 Apply `mvn fmt:format` and run the full `mvn verify` green
      (Constitution V)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies - can start immediately
- **US1 (P2 phase)**: depends on Phase 1
- **US2 (P3 phase)**: depends on Phase 2 (uses `SimulaTarget`)
- **US3 (P4 phase)**: depends on Phases 2-3 (fixtures run the real agent)
- **Polish**: after all stories

### User Story Dependencies

- **US1**: standalone MVP — capture + accessor, provable by a manual run
- **US2**: builds on US1's registry/accessor; independently testable with fakes
- **US3**: end-to-end proof layer over US1+US2 behavior

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Registry before accessor; advice/transformer before premain wiring
- Story complete before moving to next priority

### Parallel Opportunities

- T002-T006 [P] (different test files)
- T007-T011 can proceed in parallel once their paired test exists
- T013 [P] and fixture creation T016 [P]

---

## Parallel Example: User Story 1

```bash
# Launch the US1 test tasks together (write FIRST, confirm FAIL):
Task: "Write failing unit tests for AgentOptions"
Task: "Write failing unit tests for EngineBlackboard"
Task: "Write failing unit tests for AgentLogger"
Task: "Write failing unit tests for api.SimulaTarget"
Task: "Write failing tests for capture.EngineTransformer"
```

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Integration tests must resolve the `simula` jar via the dependency-plugin
  property (no hardcoded .m2 paths)
- `EngineCaptureAdvice` and `SimulaAgent` are the only JaCoCo exclusions, each
  with an inline justification in `pom.xml`
- Constitution IX: every transformation path fails soft — never log-and-rethrow
  into host threads

## Implementation notes (deviations at build time)

- Integration fixtures live in `src/test/java/fixture/` (`Main`, `Idle`,
  `ITObserver`) rather than a nested `integration/fixture/` package; they print
  bounded markers to stdout (`APP_START/APP_END`, `CAPTURED id=…`, `UNAVAILABLE`)
  which `EngineCaptureIT` asserts on, instead of writing an evidence file.
- `EngineCaptureIT` proves SC-001 (typed capture in a real `simula` module JVM),
  SC-002 (identical host output/exit with and without the agent) and
  SC-003/SC-004 (non-simula JVM inert, not-available outcome). The root-only rule
  (no child engine) is covered by `EngineTransformerTest.non_null_parent_is_not_captured`.
- JaCoCo excludes three glue classes, each with an inline justification:
  `SimulaAgent` (premain), `EngineCaptureAdvice` (inlined template) and
  `LoggingAgentListener` (callbacks fire only on real class loads). Achieved
  coverage: 116/117 lines (99.1%), 41/41 branches.

---

## Phase 6: Convergence

- [ ] T022 Declare the canonical constructor arity used by
      `EngineTransformer.constructorMatcher()` (`takesArguments(4)` in
      `src/main/java/jpnco/simula/agent/capture/EngineTransformer.java`) as a
      named constant so no raw numeric literal other than -1/0/1 remains in
      production code per Constitution VII (contradicts) [CRITICAL]
- [ ] T023 Extend the integration proof for time-event delivery: register a real
      actor on the captured engine's time topic in `fixture/ITObserver` (and run
      the engine in `fixture/Main`) and assert the received time-event marker in
      `EngineCaptureIT` per SC-001, US1/AC2 (partial) [HIGH]
- [ ] T024 Assert byte-identical stdout and equal exit code between a
      no-agent run and an agent-without-observer run of `fixture/Main` in
      `EngineCaptureIT`, replacing the current exit-code-only comparison per
      SC-002, US1/AC3, capture-contract.md Verification (partial) [HIGH]
- [ ] T025 Log exactly one `[simula-agent]` diagnostic when the observer session
      ends in the not-available outcome in
      `src/main/java/jpnco/simula/agent/ObserverRunner.java` per US2/AC2
      (partial) [HIGH]
- [ ] T026 Assert in `EngineCaptureIT` that a plain JVM run with the agent
      attached produces at most one `[simula-agent]` diagnostic line (including
      an agent-without-observer variant) per SC-003 (partial) [MEDIUM]
- [ ] T027 Start the observer daemon thread in `SimulaAgent.premain` only when an
      observer class is configured, instead of starting an idle thread per
      T012, FR-005 (partial) [LOW]
