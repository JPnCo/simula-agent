package jpnco.simula.agent.api;

import java.util.concurrent.TimeUnit;
import jpnco.simula.Engine;
import jpnco.simula.agent.EngineBlackboard;

/**
 * The developer-facing, typed accessor for the captured root engine (observer-api contract,
 * research R1).
 *
 * <p>Because the framework engine is captured into the {@link EngineBlackboard} as an opaque {@link
 * Object}, this class is the supported way to read it back as a real {@link Engine}: every entry is
 * tested with {@code instanceof} and the most recently captured match is returned, so no reflective
 * access to framework internals is ever required.
 *
 * <p>This class is part of the instrumentation agent; it is not part of the simula contract.
 *
 * <p>Implements: FR-003, FR-004.
 */
public final class SimulaTarget {

  /** The interval between availability polls while waiting, in milliseconds. */
  private static final long POLL_INTERVAL_MS = 50L;

  /** Private constructor to prevent instantiation of this utility class. */
  private SimulaTarget() {}

  /**
   * Returns the most recently captured root engine, or {@code null} when none is available yet.
   * Participates in: FR-003.
   *
   * @return the current root engine, possibly {@code null}
   */
  public static Engine currentRootEngine() {
    Engine latest = null;
    for (final Object candidate : EngineBlackboard.snapshot()) {
      if (candidate instanceof Engine) {
        latest = (Engine) candidate;
      }
    }
    return latest;
  }

  /**
   * Whether a root engine has been captured already. Participates in: FR-003.
   *
   * @return {@code true} when {@link #currentRootEngine()} would return a non-null engine
   */
  public static boolean isAvailable() {
    return currentRootEngine() != null;
  }

  /**
   * Blocks (polling) until a root engine is captured or the timeout elapses. Participates in:
   * FR-004.
   *
   * @param timeout how long to wait
   * @param unit the unit of {@code timeout}
   * @return the captured root engine, or {@code null} if none appeared in time
   */
  public static Engine awaitRootEngine(final long timeout, final TimeUnit unit) {
    final long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
    while (true) {
      final Engine engine = currentRootEngine();
      if (engine != null) {
        return engine;
      }
      if (System.nanoTime() >= deadlineNanos) {
        return null;
      }
      try {
        TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS);
      } catch (final InterruptedException exc) {
        Thread.currentThread().interrupt();
        return currentRootEngine();
      }
    }
  }
}
