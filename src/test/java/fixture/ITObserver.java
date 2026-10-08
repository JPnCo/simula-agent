package fixture;

import fr.jpnco.simula.Engine;
import fr.jpnco.simula.agent.api.SimulaObserver;

/**
 * Integration observer fixture: uses the captured engine through its typed {@link Engine} API (no
 * reflection) to prove the developer can drive the engine directly. Prints one bounded marker per
 * outcome for {@code EngineCaptureIT} to assert on.
 */
public final class ITObserver implements SimulaObserver {

  @Override
  public void engineAvailable(final Engine engine) {
    System.out.println("CAPTURED id=" + engine.getId() + " tf=" + engine.getTimeFactor());
  }

  @Override
  public void engineUnavailable() {
    System.out.println("UNAVAILABLE");
  }
}
