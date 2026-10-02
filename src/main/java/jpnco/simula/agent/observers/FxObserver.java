package jpnco.simula.agent.observers;

import javafx.application.Application;
import jpnco.simula.Engine;
import jpnco.simula.agent.api.SimulaObserver;

/**
 * Observer that launches the in-process JavaFX {@link FxApp} once the root engine is captured. The
 * JavaFX toolkit runs on its own Application Thread; {@code Application.launch} blocks this
 * (daemon) observer thread until the platform exits, which keeps the JVM alive while the window is
 * open. Requires the JavaFX modules on the runtime module path.
 *
 * <p>Configure with {@code -javaagent:…=observer=jpnco.simula.agent.observers.FxObserver}.
 *
 * <p>Implements: FR-005 (observer hook), observer-api contract.
 */
public final class FxObserver implements SimulaObserver {

  /**
   * Starts the JavaFX application for the captured engine. Participates in: FR-005.
   *
   * @param engine the captured root engine
   */
  @Override
  public void engineAvailable(final Engine engine) {
    try {
      Application.launch(FxApp.class);
    } catch (final Throwable throwable) {
      System.err.println("[fx] startup failed: " + throwable);
      throwable.printStackTrace();
    }
  }

  /** Reports that no engine appeared within the timeout. Participates in: FR-006. */
  @Override
  public void engineUnavailable() {
    System.out.println("[fx] no engine within timeout");
  }
}
