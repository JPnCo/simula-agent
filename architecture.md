# Architecture — simula-agent

The `simula-agent` is a `-javaagent` that captures the **root** `fr.jpnco.simula.Engine`
of a running simula application and hands it, typed as `Engine`, to a developer-supplied observer.
The host application and the framework jar are never modified: the agent instruments the framework
`EngineImpl` constructor at class-load time and communicates across the JPMS boundary through a
`java.base` blackboard.

## Design goals

- **Zero host intrusion**: no change to the application source or to the framework jar.
- **Typed, reflection-free data access**: the observer receives a real `Engine`; only a one-time
  reflective load instantiates the developer-named observer class.
- **Soft degradation** (Constitution IX): every instrumentation path is best-effort. A framework
  signature drift simply stops matching, so nothing is captured and the observer gets the
  not-available outcome — the host JVM is never harmed.
- **Cross-module visibility**: a named module cannot read the agent's unnamed module, so the two
  sides communicate only through a structure in `java.base`.

## Components

```mermaid
graph TD
  subgraph JVM["Application JVM"]
    subgraph MODULE["Named module: simula"]
      EI["EngineImpl<br/>(canonical constructor)"]
    end
    subgraph UNNAMED["Unnamed module (system classpath)"]
      SA["SimulaAgent<br/>(premain)"]
      AO["AgentOptions"]
      ET["EngineTransformer"]
      ADV["EngineCaptureAdvice<br/>(inline template)"]
      EB["EngineBlackboard"]
      ST["api.SimulaTarget"]
      SO["api.SimulaObserver"]
      OR["ObserverRunner"]
      OB["Developer observer<br/>(observer= fqcn)"]
    end
    BB["Blackboard<br/>System.getProperties()"]
  end

  SA --> AO
  SA --> ET
  SA --> OR
  ET -.->|inline advice at load| EI
  ADV -.->|advice body inlined into| EI
  EI -->|append root, java.base only| BB
  EB <-->|create / read| BB
  OR --> ST
  ST -->|instanceof Engine| BB
  OR -->|load + deliver| OB
  OB -.->|typed| SO
  SO --> ST
```

## Capture dataflow

```mermaid
sequenceDiagram
  participant JVM as JVM
  participant Agent as SimulaAgent (premain)
  participant Trans as EngineTransformer
  participant EI as EngineImpl.<init>
  participant BB as Blackboard
  participant Run as ObserverRunner (daemon)
  participant Obs as Developer observer

  JVM->>Agent: premain(agentArgs, instrumentation)
  Agent->>BB: create list under PROPERTY_KEY
  Agent->>Agent: AgentOptions.parse(agentArgs)
  Agent->>Trans: install(instrumentation)
  Agent->>Run: start() daemon thread
  Note over Trans: registered for later class load
  EI->>EI: canonical ctor runs (parent == null?)
  Note over EI: inlined advice, suppress=Throwable
  EI->>BB: append(this) when parent == null
  Run->>BB: poll via SimulaTarget.awaitRootEngine()
  BB-->>Run: latest Engine
  Run->>Obs: engineAvailable(engine)  (exactly once)
```

## Key invariants

- **Root only.** The advice fires on the canonical `EngineImpl(String, Engine, int, ExecutionMode)`
  constructor and records the instance only when its `parent` argument is `null`. Every public
  constructor delegates to this one, so a single advice site sees all constructions while child
  engines are filtered out.
- **`java.base`-only advice.** The inlined advice references only `System.getProperties()` and
  `java.util.List`. The registry key is a `String` compile-time constant
  (`EngineBlackboard.PROPERTY_KEY`) that the Java compiler inlines, leaving no runtime reference to
  any agent type inside the transformed framework class.
- **Exactly-one outcome.** `ObserverRunner` delivers `engineAvailable` or `engineUnavailable` once and
  confines any observer exception (logged, never rethrown).
- **Named target by contract, not by link.** `EngineTransformer` matches `fr.jpnco.simula.engine.EngineImpl`
  and its constructor structurally (4 arguments, `fr.jpnco.simula.Engine` parent at index 1), so the
  agent compiles with a `provided` framework dependency and links to none of its bytecode.

## Instrumentation contract

The agent depends on one structural contract of the framework (documented in
`specs/001-engine-capture/contracts/capture-contract.md`): the canonical engine constructor keeps the
shape `(String, Engine, int, ExecutionMode)` with a `null` parent marking the root. If that shape
changes, matching fails and the agent degrades to "engine unavailable" — never an error in the host.

## Optional supervision

With the `supervise=true` option, a second load-time advice (`capture/SupervisionAdvice`) targets the
framework's documented instrumentation anchor `EngineImpl.checkSupervision()` and writes
`isSupervised = true` into that field at method entry (`@Advice.FieldValue(readOnly = false)`),
before the method body runs. The body then registers the framework's `SimulaSupervisor`. The inlined
write references only the instrumented type's own `boolean` field, so it stays valid inside the named
`simula` module and is suppressed on any `Throwable` (Constitution IX).

## Supervision watch (feature 002)

`api/SupervisionWatcher` lets an observer stream actor lifecycle changes. It attaches the
framework's `SupervisionListener` (`specs/002-supervision-watch/contracts/supervision-listener.md`,
a typed compile dependency) to every `SimulaSupervisor` in the engine tree and runs a
listen/iterate-then-drain loop: callbacks only enqueue `(supervisor, actor)` hints (plus a subtree
scan request when the recorded actor is itself an `Engine`, which is how a late-started child engine
is discovered); each cycle applies the due scans, then drains the hint queue re-reading the live
`getStates()` map per hinted actor, so mid-cycle changes are never lost nor delivered twice. The
full-tree walk and per-supervisor catch-up snapshots run once at start and then once per second as
the safety fallback — the only case no callback can announce is `addChild` of a child that is never
registered as an actor. Actors registered after the supervisor was built are invisible to the
framework's own recording, so each scan also reconciles raw membership: in `getActors()` but absent
from the states means synthetic `null -> STARTED`, gone from both means synthetic `STARTED ->
STOPPED` (FR-110). Dispatch and cycles are exception-contained; supervisors that leave
the tree are detached, after reporting every last known status once as a disappearance
(`current == null`). The JavaFX demo drains a `StatusLedger` queue on the FX thread into an
`ObservableMap` that colors actor boxes `started`/`stopped`; status repaints are coalesced so a
burst of events causes at most one re-render per FX pulse. A disappearance event reads as `STOPPED`
and both stopped actors and stopped engines stay remembered (red) for the whole session — the tree
only ever grows — so stopping something recolors it instead of deleting it. Engines are themselves actors that
self-record in their own supervisor yet never appear in their own `getActors()` list, so the demo
colors the engine's *tree cell* with the same status map. The tree mirrors the pane: each engine node carries
its registered actors as leaf nodes (gear icon, same status coloring); selecting an actor node shows
the pane of its engine. Every box carries a right-click `Stop`
menu that dispatches `observers/StopRequest`: `Engine.stop()` for engine targets,
`Actor.stopMe()` (a `STOP_ME` event on the engine queue) for plain actors, exceptions contained.
