# Contract: Supervision Listener (framework)

**Feature**: `002-supervision-watch` — **Side**: framework `simula`, package `fr.jpnco.simula.actors`
(exported), as implemented in framework 0.0.1-SNAPSHOT.

## Types

```java
public interface SupervisionListener {
  /** Called after the supervisor records a status. previous == null on first record. */
  void statusChanged(Actor actor, SimulaSupervisor.Status previous, SimulaSupervisor.Status current);
}

public final class SimulaSupervisor implements Actor {
  public void addSupervisionListener(SupervisionListener listener);    // idempotent (addIfAbsent)
  public void removeSupervisionListener(SupervisionListener listener);
  public Map<Actor, Status> getStates();                               // unmodifiable live view
}
```

## Guarantees relied upon by the agent

1. `statusChanged` fires on the engine thread, after `states.put`, with the previous value returned
   by the put (so `previous == null` iff the actor was seen for the first time).
2. The listener list is copy-on-write: attaching/removing during notification is safe.
3. The supervisor catches any `Throwable` thrown by a listener: a faulty listener cannot break the
   simulation (matches Constitution IX).
4. Statuses only ever transition through `record()`; entries are never removed from the map, so a
   disappeared actor is observed by the agent as `current == null` via map diff, not via a callback.
5. The `states` map is a `ConcurrentHashMap`: iteration is weakly consistent and never throws
   `ConcurrentModificationException`, but the agent must still expect values to change concurrently
   and must treat its own snapshot as best-effort truth per cycle.
6. A supervisor records at construction time the actors already present in the engine, and the only
   in-jar publisher of `STARTED_ACTOR` for an actor is `TimeSource` (for itself): an actor
   registered after the supervisor was built is recorded by nothing and never fires a callback.
   The agent therefore reconciles raw membership each scan (FR-110): `getActors()` minus the states
   yields synthetic `null -> STARTED`, disappearance from both yields synthetic `STARTED -> STOPPED`.

## Agent usage (listen / scan / drain)

The agent listener does NOT dispatch: it only enqueues a `(supervisor, actor)` hint (plus a subtree
scan request when the recorded actor is an `Engine`). Each watcher cycle: (1) applies due scans —
the full-tree walk and `getStates()` catch-up snapshots once per second, targeted subtree scans on
engine-valued callbacks — attaching supervisors and reconciling membership (FR-110); (2) drains the
hint queue and re-reads the live map for each hinted actor, emitting only still-unreported changes.
Hints that were already captured by a snapshot deduplicate, giving at-least-once, cycle-deduped
delivery.
