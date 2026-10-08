package fr.jpnco.simula.agent.observers;

import fr.jpnco.simula.Engine;
import fr.jpnco.simula.agent.api.SimulaObserver;

/**
 * A minimal ready-to-use {@link SimulaObserver} that prints the captured root engine's children and
 * actors to {@code System.out}. Configure it with {@code
 * -javaagent:…=observer=fr.jpnco.simula.agent.observers.SimpleObserver}.
 *
 * <p>Implements: FR-005.
 */
public class SimpleObserver implements SimulaObserver {

  /**
   * Prints the child engines and actors of the captured root engine. Participates in: FR-005.
   *
   * @param engine the captured root engine
   */
  @Override
  public void engineAvailable(final Engine engine) {
    System.out.println(engine.getChildren());
    System.out.println(engine.getActors());
  }
}
