package jpnco.simula.agent.api;

import jpnco.simula.Engine;

/**
 * A callback the developer supplies (via the {@code observer} agent option) to receive the captured
 * root engine (observer-api contract).
 *
 * <p>The agent instantiates the implementing class through its public no-argument constructor and
 * invokes exactly one of the two methods, once: {@link #engineAvailable(Engine)} when the root
 * engine is captured within the configured timeout, otherwise {@link #engineUnavailable()}.
 *
 * <p>Any exception thrown by an implementation is contained by the agent and only logged.
 *
 * <p>Implements: FR-005.
 */
public interface SimulaObserver {

  /**
   * Invoked once when the root engine becomes available. Participates in: FR-005.
   *
   * @param engine the captured root engine, never {@code null}
   */
  void engineAvailable(Engine engine);

  /** Invoked once when no engine appeared before the timeout elapsed. Participates in: FR-006. */
  default void engineUnavailable() {}
}
