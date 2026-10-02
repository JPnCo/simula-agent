# Feature Specification: Root Engine Capture Agent

**Feature Branch**: `001-engine-capture`

**Created**: 2026-10-01

**Status**: Draft

**Input**: User request — an instrumentation agent that captures the root simula engine as a usable `Engine` instance from an unmodified application, so observer code can use the `Engine` and `Actor` interfaces directly.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Observe a running simulation from inside it (Priority: P1)

As a developer who owns applications built on the simula framework, I want to
attach a JVM agent to any such application at launch so that my own observer code
receives the application's root engine as a normal `Engine` instance and can use
the framework's `Engine`/`Actor` interfaces on it (subscribe to topics, register
an actor, read the simulated time) — with no change to the application, the
framework, or their build artifacts.

**Why this priority**: This is the whole value of the agent: without a captured
`Engine`, nothing else (counting, plotting, future metrics) is possible. A single
story here already delivers the end-to-end capability.

**Independent Test**: Launch an existing simula demo (e.g. the traffic-light
console demo) with `-javaagent:simula-agent.jar=observer=<my-observer>` and no
other change; verify the observer receives the root engine, subscribes a real
actor to the built-in time topic, and receives time events, while the demo itself
runs and exits exactly as without the agent.

**Acceptance Scenarios**:

1. **Given** a simula application launched with the agent attached, **When** the
   application constructs its root engine, **Then** the agent captures that
   instance and makes it available as the root engine (no user action needed).
2. **Given** the observer has received the root engine, **When** it uses only the
   framework's public interfaces (`Engine`, `Actor`), **Then** every call behaves
   exactly as if the application itself had made it (subscription, registration,
   time reads).
3. **Given** the same application launched without the agent, **When** it runs to
   completion, **Then** its observable outcome is identical to a launched-with-
   agent run (the agent changes no application behavior).

---

### User Story 2 - Wait for the engine and cope with its absence (Priority: P2)

As a developer, I want observer code to work regardless of launch ordering — the
engine may appear before or after my observer starts — and I want a clear,
non-fatal outcome when the JVM never creates a simula engine at all.

**Why this priority**: The agent cannot control when the application creates its
engine, and the same agent jar may be attached to a non-simula JVM by mistake;
robust waiting and a clean "no engine" outcome are required for daily use but are
secondary to the capture itself.

**Independent Test**: Attach the agent (a) to a simula app whose engine is
created late, and (b) to a plain JVM with no simula at all; verify (a) the
observer still receives the engine within the configured wait, and (b) the plain
JVM starts, runs, and exits normally while the agent reports a single
"no engine observed" diagnostic.

**Acceptance Scenarios**:

1. **Given** the observer's wait begins before the application creates its
   engine, **When** the engine appears, **Then** the observer receives it without
   the observer having to poll.
2. **Given** a JVM that never creates a simula engine, **When** the wait timeout
   elapses, **Then** the observer is told no engine is available, the agent logs
   one diagnostic, and the host application is otherwise untouched.
3. **Given** the application creates several root engines over time (e.g. one
   simulation per batch), **When** a newer root engine is created, **Then** the
   most recently created root engine is the one exposed as current, and all
   previously captured engines remain retrievable.

---

### User Story 3 - Stay harmless across framework evolution (Priority: P3)

As an operator, I want the agent to never be able to break its host application:
if the framework it instruments has evolved away from the signatures the agent
transforms, the agent must degrade to a no-op with a diagnostic instead of
crashing or altering the application.

**Why this priority**: The framework is under active development; hard resilience
to signature drift protects every future adoption of the agent, but the capture
capability itself must exist first.

**Independent Test**: Attach the agent to a JVM running a stand-in "framework"
whose engine class lacks the expected constructor shape; verify the application
starts and runs normally, transformation errors surface only as agent log
diagnostics, and the observer receives the engine-not-available outcome.

**Acceptance Scenarios**:

1. **Given** a class named like the engine whose constructor shape the agent does
   not recognize, **When** the agent transforms (or fails to transform) it,
   **Then** the application continues unaffected and the agent logs why it could
   not capture anything.
2. **Given** a transformation failure at startup, **When** the application runs,
   **Then** no exception from the agent ever reaches application threads.

---

### Edge Cases

- What happens when the host JVM loads the framework but creates no engine (the
  library is present but unused)? No engine is captured; the observer receives the
  not-available outcome after the timeout.
- What happens when an application creates child engines (they have a parent)?
  They are not captured by this feature; only parentless (root) engines qualify.
- What happens when an application creates several root engines simultaneously
  from different threads? Each capture is recorded independently and atomically;
  the "current" engine is unambiguously the latest captured at the moment of the
  read.
- What happens when the observer class named in the agent options cannot be
  loaded or has no usable no-argument constructor? The agent logs a diagnostic,
  capture still works (the engine remains retrievable via the accessor API), and
  the host application is unaffected.
- What happens when the same JVM runs with the agent twice (duplicate option)?
  Standard JVM behavior: only one agent instance is loaded; no special handling is
  required.
- What happens during JVM shutdown while the observer waits? The wait must not
  prevent JVM exit (the observer thread is a daemon).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The agent MUST attach solely through the standard JVM agent launch
  option (`-javaagent:<agent-jar>[=<options>]`) and MUST NOT require any change to
  the application, the framework, or their artifacts.
- **FR-002**: The agent MUST detect every construction of a root simula engine (an
  engine created without a parent) in the host JVM and MUST record that instance
  as captured, keeping every captured engine retrievable.
- **FR-003**: The agent MUST expose an in-process accessor that returns a captured
  root engine as the framework's own public `Engine` type, usable directly by any
  code running in the same JVM, without reflection-based data access.
- **FR-004**: The accessor MUST support waiting for the first captured root engine
  up to a configurable timeout and MUST return an explicit "not available" result
  when the timeout elapses.
- **FR-005**: The agent MUST accept an `observer=<fully-qualified-class>` option;
  when present, it MUST instantiate that class (public no-argument constructor)
  and hand it the root engine through a documented observer contract, on a
  background daemon thread, without delaying or blocking the application.
- **FR-006**: The observer contract MUST deliver exactly one of these outcomes per
  session: the root engine became available, or no engine became available within
  the timeout.
- **FR-007**: The agent MUST degrade softly on any transformation failure (missing
  or changed framework signatures): failures MUST be reported as agent diagnostics
  and MUST NOT be thrown into, or change the behavior of, the host application.
- **FR-008**: Options MUST be provided as a semicolon-separated `key=value` list
  supporting at least `observer=<fqcn>` and `timeout=<seconds>` (default timeout:
  30 seconds); unknown keys and malformed pairs MUST be ignored with a warning.
- **FR-009**: Captured-engine bookkeeping MUST be thread-safe: captures from any
  thread MUST be visible to any reader, and the current engine MUST be the most
  recently captured root engine at read time.

### Key Entities *(include if feature involves data)*

- **Captured engine**: A root simula engine instance recorded by the agent at
  construction time, retrievable through the framework's public `Engine` type.
- **Engine registry**: The JVM-wide record of captured engines: their capture
  order, the current (most recently captured) root engine, and their availability
  state.
- **Observer**: Developer-supplied class, loaded by the agent, that receives the
  root engine (or the not-available outcome) exactly once through the observer
  contract.
- **Agent options**: The `key=value;key=value` launch string configuring observer
  class and wait timeout.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An unmodified simula demo launched with the agent and an observer
  delivers its root engine to the observer within 5 seconds of engine creation,
  and the observer can receive the framework's periodic time event through its own
  actor subscription — proven by an automated integration test.
- **SC-002**: The same demo produces byte-identical standard output and the same
  exit code with and without the agent attached.
- **SC-003**: A plain non-simula JVM with the agent attached starts, runs, and
  exits normally, producing at most one agent diagnostic line about the missing
  engine.
- **SC-004**: The agent's own code achieves at least 97% line and branch coverage
  under the project's coverage gate (Constitution Principle II), with only
  justified bootstrap exclusions.

## Assumptions

- The host application uses the simula framework on the module path, as the
  existing simula projects do; framework version drift is handled by soft
  degradation, not by version pinning.
- The observer class is compiled by the developer against the framework and the
  agent's public accessor/contract types, and is present on the host JVM's
  class or module path at launch.
- One JVM hosts at most one application under observation; multi-JVM aggregation
  (HTTP export, counters, metrics) is explicitly out of scope for this feature and
  deferred to follow-up features.
- The observer is trusted code: the agent runs it in-process with the application
  and performs no sandboxing.
- Child engines and per-engine trees are out of scope for capture in this feature
  (root engines only).
