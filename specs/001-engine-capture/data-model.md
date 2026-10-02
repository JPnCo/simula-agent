# Data Model: Root Engine Capture Agent

## Entities

### Engine registry (blackboard)
The JVM-wide record of captured engine instances. Physically a
`CopyOnWriteArrayList<Object>` stored under the `System` property key
`jpnco.simula.agent.engine.registry` (named constant).

| Aspect | Rule |
|--------|------|
| Contents | Every engine constructed with a `null` parent, in capture order |
| Creation | Exactly once, at `premain`, before transformations are installed |
| Writers | Inlined advice inside the framework constructor (`Object` append only) |
| Readers | `EngineBlackboard` / `SimulaTarget` (snapshots, typed filtering) |
| Thread-safety | Atomic appends, lock-free snapshots (FR-009) |

Behavior:
- `currentRoot()` — last entry in capture order that is an `Engine`, or `null`.
- `captured()` — immutable snapshot list of all captured entries.
- `isAvailable()` — `currentRoot() != null`.

### AgentOptions
Immutable parse result of the `-javaagent` option string (`key=value` pairs
joined by `;`).

| Field | Type | Default |
|-------|------|---------|
| `observerClassName` | `String` or `null` | `null` (no observer thread) |
| `awaitTimeout` | `Duration` | 30 s (named constant default) |

Unknown keys and malformed pairs are skipped with a warning (FR-008).

### SimulaObserver (contract, api package)
Developer-implemented single-result callback:
- `engineAvailable(Engine engine)` — called once when the root engine is captured.
- `engineUnavailable()` — default no-op; called once when the await times out.

### SimulaTarget (accessor, api package)
Static, non-blocking queries plus the blocking wait:
- `currentRootEngine()` → `Engine` or `null`.
- `isAvailable()` → boolean.
- `awaitRootEngine(long timeout, TimeUnit unit)` → `Engine` or `null`.

## State transitions

Registry: `EMPTY → [engine1] → [engine1, engine2] …` (append-only).
The "current" engine is always the latest captured root at read time; engines
that were captured are never removed, even after their engine stops.

Observer session: `WAITING → {DELIVERED(engine) | EXPIRED(unavailable)}`;
exactly one terminal state; observer exceptions never re-open the session.

## Relationships

- One JVM hosts one agent instance, hence one registry.
- An observer (if configured) is instantiated at most once per JVM and tied to
  the registry's first available engine or to its absence.
- `SimulaTarget` is a pure read view over the registry; it mutates nothing.
