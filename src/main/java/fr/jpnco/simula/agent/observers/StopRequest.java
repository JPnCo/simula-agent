package fr.jpnco.simula.agent.observers;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.agent.AgentLogger;

/**
 * Sends the framework's cooperative stop request to an actor box target (FR-108).
 *
 * <p>An {@link Engine} target is stopped through {@link Engine#stop()}; any other actor is asked to
 * stop itself through {@link Actor#stopMe()}, which signals a {@code STOP_ME} event on its own
 * engine queue. Failures are logged and never propagate to the JavaFX thread.
 *
 * <p>Part of the JavaFX demo observer. Implements: FR-108.
 */
public final class StopRequest {

  /** Private constructor to prevent instantiation of this utility class. */
  private StopRequest() {}

  /**
   * Requests the cooperative stop of the given actor or engine. Participates in: FR-108.
   *
   * @param target the actor or engine to stop; {@code null} is ignored
   */
  public static void send(final Actor target) {
    if (target == null) {
      return;
    }
    try {
      if (target instanceof Engine engine) {
        engine.stop();
      } else {
        target.stopMe();
      }
    } catch (final Throwable throwable) {
      AgentLogger.error("stop request failed for " + target.getName() + ": " + throwable);
    }
  }
}
