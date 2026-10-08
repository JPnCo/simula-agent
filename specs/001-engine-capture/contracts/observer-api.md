# Contract: Observer API

**Feature**: `001-engine-capture`

The agent jar exposes two public types for developer-written observer code.
Observer code compiled against these types uses the framework's own interfaces
(`fr.jpnco.simula.Engine`, `fr.jpnco.simula.Actor`) directly — no reflection.

## `fr.jpnco.simula.agent.api.SimulaTarget`

```java
public final class SimulaTarget {
  /** Non-blocking: the latest captured root engine, or null. */
  public static Engine currentRootEngine();

  /** Non-blocking: true when a root engine has been captured. */
  public static boolean isAvailable();

  /** Blocking: waits up to the timeout for the first root engine, or returns null. */
  public static Engine awaitRootEngine(long timeout, TimeUnit unit);
}
```

## `fr.jpnco.simula.agent.api.SimulaObserver`

```java
public interface SimulaObserver {
  /** Called once when the root engine becomes available. */
  void engineAvailable(Engine engine);

  /** Called once when no root engine appeared within the timeout. Default no-op. */
  default void engineUnavailable() {}
}
```

## Lifecycle

1. `premain` starts a daemon thread when `observer=<fqcn>` is set.
2. The thread loads the class, instantiates it via its public no-argument
   constructor, then delivers exactly one outcome:
   - `engineAvailable(engine)` when `SimulaTarget.awaitRootEngine(timeout)` yields
     an engine; otherwise
   - `engineUnavailable()`.
3. Any exception (loading, construction, or inside the observer) is logged with
   the `[simula-agent]` prefix and never reaches the application (FR-007).

## Visibility notes

- The observer class must be loadable by the application (system) class loader.
- Because the framework module `simula` exports `fr.jpnco.simula`, observer code on
  the classpath may import and use `Engine`/`Actor` freely.
- The engine handed to the observer is the live application engine: subscribing to
  topics and registering actors are real actions on the running simulation.
