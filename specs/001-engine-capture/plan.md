# Implementation Plan: Root Engine Capture Agent

**Branch**: `001-engine-capture` | **Date**: 2026-10-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-engine-capture/spec.md`.

## Summary

Deliver a JVM instrumentation agent (`-javaagent:simula-agent.jar`) that captures
the root engine of any unmodified simula application at construction time and
exposes it — as the framework's own public `jpnco.simula.Engine` type — to an
observer class named in the agent options. The agent transforms exactly one
framework constructor (`jpnco.simula.engine.EngineImpl`'s canonical constructor),
detects root engines by a `null` parent argument, and records captures in a
JVM-wide registry reachable from both the transformed module and the agent through
a `java.base` blackboard (a list stored under a well-known `System` property key).
Observer code then uses `Engine` and `Actor` directly, with no reflection.

## Technical Context

**Language/Version**: Java 25 (`maven.compiler.source`/`target` 25)

**Primary Dependencies**: Byte Buddy 1.17.x (transform + Advice; shaded and
relocated inside the agent jar), `jpnco:simula:0.0.1-SNAPSHOT` (`provided` —
interfaces only); JUnit Jupiter 5.14 + Mockito 5.22 (test scope)

**Storage**: In-memory only (JVM-wide registry); no files, no network

**Testing**: JUnit 5 + Mockito via Surefire (`mvn test`); integration tests spawn
real JVMs with `-javaagent` attached; JaCoCo ≥97% line and branch gate on
`verify` (Constitution II); `fmt-maven-plugin` (google-java-format) on `verify`
(Constitution V)

**Target Platform**: HotSpot JVM, module-path applications (`--module-path`)

**Project Type**: Java agent (thin jar with shaded Byte Buddy) + public API for
observers

**Performance Goals**: capture happens once per engine construction; hot paths
untouched (no per-event transformation in this feature)

**Constraints**: non-invasion (Constitution); no JMX/JFR; premain only; advice
code must reference only `java.base` types (the transformed framework module can
never read the agent's unnamed module — see research R1)

## Key Decisions

- **Single transformation point** (FR-002, Constitution IX): advise only the
  canonical constructor `EngineImpl(String, Engine parent, int, ExecutionMode)`
  — every public constructor delegates to it — and capture only when
  `parent == null`. The constructor descriptor is declared a stability contract in
  `contracts/capture-contract.md`; if the descriptor ever changes, the matcher
  simply stops matching and the agent degrades to a no-op with a startup
  diagnostic (soft degradation by construction).
- **`java.base` blackboard** (R1): a JPMS named module (`simula`) can never read
  the unnamed module where the agent lives, so the inlined advice cannot call an
  agent class. Advice instead appends the constructed instance (`Object`) to a
  `CopyOnWriteArrayList<Object>` stored in `System.getProperties()` under a
  well-known key created by `premain`. Advice references only `java.util`/`java.lang`
  types.
- **Type-safe accessor** (FR-003): `jpnco.simula.agent.api.SimulaTarget` (agent
  jar, unnamed module, which CAN read the exported `jpnco.simula` packages) casts
  blackboard entries with `instanceof Engine` — typed access, no reflective data
  reading. `awaitRootEngine(timeout)` polls with a short sleep and returns `null`
  on timeout (FR-004).
- **Observer hook** (FR-005/FR-006): agent option `observer=<fqcn>`; a daemon
  thread loads the class by name (one-time bootstrap class loading), instantiates
  it via its public no-arg constructor, and delivers exactly one outcome through
  `api.SimulaObserver` (`engineAvailable(Engine)` or `engineUnavailable()`).
  Failures are logged, never propagated (Constitution IX).
- **Relocated Byte Buddy** (R3): the agent jar shades and relocates
  `net.bytebuddy` to `jpnco.simula.agent.shaded.bytebuddy` so it cannot clash
  with a host application that uses Byte Buddy itself.
- **Framework as `provided`** (Constitution): compile-time only; at runtime the
  agent sees the host's own framework classes.
- **Test strategy** (R6): integration tests spawn `java -javaagent:… --module-path
  <simula.jar> --add-modules simula -cp <test-classes> <FixtureMain>` — the
  framework on the module path (true JPMS scenario), the fixture app on the
  classpath. A plain JVM fixture proves SC-003.

## Project Structure

### Documentation (this feature)

```text
specs/001-engine-capture/
├── plan.md              # This file
├── research.md
├── data-model.md
├── contracts/
│   ├── agent-options.md      # -javaagent options string
│   ├── observer-api.md       # SimulaTarget + SimulaObserver contract
│   └── capture-contract.md   # framework signature stability contract
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/jpnco/simula/agent/
├── SimulaAgent.java          # premain: options, blackboard, transform, observer thread
├── AgentOptions.java         # key=value;key=value parser (observer, timeout)
├── AgentLogger.java          # prefixed stderr diagnostics (never throws)
├── EngineBlackboard.java     # property-key registry: create/append/snapshot
├── ObserverRunner.java       # daemon thread: wait, load observer, deliver once
├── capture/
│   ├── EngineCaptureAdvice.java   # inline advice template (java.base types only)
│   └── EngineTransformer.java     # AgentBuilder wiring (soft-failure listener)
└── api/
    ├── SimulaTarget.java     # currentRootEngine(), awaitRootEngine(timeout), isAvailable()
    └── SimulaObserver.java   # engineAvailable(Engine) / engineUnavailable()

src/test/java/jpnco/simula/agent/
├── AgentOptionsTest.java
├── AgentLoggerTest.java
├── EngineBlackboardTest.java
├── ObserverRunnerTest.java
├── api/SimulaTargetTest.java
├── capture/EngineCaptureAdviceTest.java
└── integration/
    ├── EngineCaptureIT.java       # spawns agent-attached JVMs; SC-001..SC-003
    └── fixture/
        ├── ObserverFixture.java   # writes capture evidence to a file
        ├── SimulaAppFixture.java  # tiny simula app (root + child engine + ticks)
        └── PlainAppFixture.java   # non-simula main for SC-003
```

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **G1 (Principle I — Test-First, NON-NEGOTIABLE)**: **PASS** — tasks schedule
  failing unit tests for every class before its implementation, and the
  integration gate tests before the observer hook is trusted.
- **G2 (Principle II — Coverage ≥97%)**: **PASS** — core classes are unit-tested;
  `SimulaAgent` premain glue and `EngineCaptureAdvice` (inlined at transform time,
  never executed as-is) are declared exclusions with justification in `pom.xml`.
- **G3/G4 (English code & comments)**: **PASS**.
- **G5 (Formatting)**: **PASS** — `fmt-maven-plugin` bound to `verify`.
- **G6 (Javadoc + FR/SC citations)**: **PASS** — planned on every class/method.
- **G7 (Named literals)**: **PASS** — property key, log prefix, defaults, and
  fixture paths are named constants.
- **G8 (Architecture document)**: **PASS** — root `architecture.md` is created by
  the Polish phase and kept current.
- **G9 (Instrumentation Contract Stability)**: **PASS** — single matcher,
  `suppress = Throwable.class` on advice, Byte Buddy listener logs transformation
  failures without touching the host; integration tests prove the contract and the
  no-engine and non-simula degradations.

## Complexity Tracking

> No violations — gate passes with no deviations.
