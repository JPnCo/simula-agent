package jpnco.simula.agent.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import jpnco.simula.Engine;
import org.junit.jupiter.api.Test;

/** Unit test for the {@link SimulaObserver} default behaviour (observer-api contract). */
class SimulaObserverTest {

  @Test
  void default_engine_unavailable_is_a_noop() {
    final SimulaObserver observer =
        new SimulaObserver() {
          @Override
          public void engineAvailable(final Engine engine) {}
        };
    assertDoesNotThrow(observer::engineUnavailable);
  }
}
