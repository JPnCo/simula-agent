# Research: Root Engine Capture Agent

**Feature**: `001-engine-capture` | **Date**: 2026-10-01

## R1 — JPMS visibility wall between the transformed module and the agent

**Decision**: The inlined advice communicates with the agent exclusively through a
blackboard: a `CopyOnWriteArrayList<Object>` stored in `System.getProperties()`
under the well-known key `jpnco.simula.agent.engine.registry` (a named constant).

**Rationale**: The framework runs as the named module `simula`; the agent's
classes live in the unnamed module (agent jar lands on the system classpath).
A named module can never read the unnamed module, so advice code inlined into
`EngineImpl` cannot reference any agent type. `java.base` is readable by every
module, and `java.util.Properties` (a `Hashtable<Object,Object>`) legally stores
arbitrary objects — the classic bootstrap-blackboard technique. The agent side
(`EngineBlackboard`, unnamed module) reads the same list; module-path app classes
loaded by the system class loader share the same `java.base`, so both sides see
the identical property object.

**Alternatives considered**: injecting a helper class into the `simula` module
(injected classes into a named module are not supported cleanly); `Unsafe.defineClass`
in the target module (overkill for appending one object); a static field added to
the transformed class (the agent could not then read it without reflection).

## R2 — Single constructor advice with the parent argument as the root test

**Decision**: Transform exactly `jpnco.simula.engine.EngineImpl`'s canonical
constructor `(String, Engine, int, ExecutionMode)` with
`@Advice.OnMethodExit(suppress = Throwable.class)`; capture
`@Advice.Argument(1) == null` by appending `@Advice.This` to the blackboard.

**Rationale**: Every public `EngineImpl` constructor delegates to this private
canonical constructor (verified in the framework source), so one advice site
sees every construction — including child engines, which are filtered out by the
`null` parent test. Advice parameters are typed `Object`, so the advice template
compiles with zero dependency on framework types and stays valid no matter how
the parameter's framework type evolves. `suppress = Throwable.class` guarantees
the advice itself can never throw into the host constructor (Constitution IX).
Soft degradation is structural: if the constructor descriptor ever changes, the
matcher no longer matches, nothing is captured, and the observer receives the
not-available outcome — the host is untouched.

**Alternatives considered**: advising all constructors (redundant delegation);
transforming `Engine` implementations generically by interface (heavier matcher,
no parent visibility in the interface); post-construction discovery via JFR/JMX
(excluded by the constitution).

## R3 — Shaded, relocated Byte Buddy

**Decision**: `maven-shade-plugin` bundles and relocates `net.bytebuddy` to
`jpnco.simula.agent.shaded.bytebuddy` inside the agent jar.

**Rationale**: A host application may itself use Byte Buddy (or another agent may
be loaded); relocation removes classpath clashes and version coupling. The agent
jar remains a single deployable artifact.

## R4 — Observer class loading is bootstrap-time, not data reflection

**Decision**: The observer is loaded with `Class.forName(name, false, loader)`
followed by `asSubclass(SimulaObserver.class).getDeclaredConstructor().newInstance()`.

**Rationale**: The constitution's "no reflection" intent concerns *data access*;
instantiating a developer-named class cannot be expressed in pure Java without a
one-time reflective lookup. Data then flows as typed `Engine` references only.
Failures (unknown class, missing no-arg constructor, wrong type, exception in
the observer) are logged and confined (FR-007, edge case "observer cannot be
loaded").

## R5 — Logging strategy

**Decision**: A minimal `AgentLogger` writing `[simula-agent] <level> <message>`
lines to `System.err`, always prefixed, never throwing, with a level threshold
(WARN/ERROR default, INFO optional via `log=info` reserved for later).

**Rationale**: Agents cannot use the host's logging configuration without coupling
to it; stderr is the only channel guaranteed independent of the application's
stdout contract (SC-002 compares application stdout byte-for-byte).

## R6 — Integration test harness under JPMS

**Decision**: Integration tests spawn real JVMs as:
`java -javaagent:target/simula-agent.jar=observer=…,timeout=… --module-path <simula.jar> --add-modules simula -cp <test-classes> fixture.Main`.

**Rationale**: Putting the framework jar on the module path exercises the true
deployment shape (named module `simula`, exported packages, system-class-loader
visibility) while the fixture app on the classpath keeps fixture builds trivial.
Because `-javaagent` jars are appended to the system classpath, agent and
fixture classes share the system loader. SC-002 is proven by running the fixture
app with and without the agent and comparing stdout and exit code.

**Note**: `simula`'s module name is `simula` (from its `module-info.java`);
`--add-modules simula` forces the framework to resolve even though the fixture is
unnamed code.

## R7 — Thread-safety of the registry

**Decision**: `CopyOnWriteArrayList<Object>` for captures; reads take immutable
snapshots; the "current" engine is the last `instanceof Engine` element in
capture order. The observer waiting thread is a daemon polling with a 50 ms sleep
until the deadline.

**Rationale**: Captures are rare (once per engine) and reads are rare (observer
startup + accessor calls); copy-on-write gives lock-free readers and atomic
appends (FR-009). A daemon waiter cannot keep the JVM alive (shutdown edge case).

## R8 — Out of scope now, designed for later

Per-event counters, HTTP export, and business-object extraction (the earlier
design conversation) build on this same base: the blackboard becomes a multi-entry
map (registry + counters), and new advices are added behind the same
soft-degradation transformer. No structural change is required later, which is why
the registry stores `Object` entries under a versioned key constant.
