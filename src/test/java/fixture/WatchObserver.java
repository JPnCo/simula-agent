package fixture;

import fr.jpnco.simula.Engine;
import fr.jpnco.simula.agent.api.SimulaObserver;
import fr.jpnco.simula.agent.api.SupervisionEvent;
import fr.jpnco.simula.agent.api.SupervisionWatcher;

/**
 * Integration observer fixture: arms a {@link SupervisionWatcher} on the captured root engine and
 * prints one bounded {@code EVT} line per status change, for {@code SupervisionIT} to assert on.
 */
public final class WatchObserver implements SimulaObserver {

  @Override
  public void engineAvailable(final Engine engine) {
    System.out.println("WATCH_ARMED id=" + engine.getId());
    SupervisionWatcher.start(engine, WatchObserver::print);
  }

  private static void print(final SupervisionEvent event) {
    System.out.println(
        "EVT " + event.actor().getName() + " " + event.previous() + "->" + event.current());
  }
}
