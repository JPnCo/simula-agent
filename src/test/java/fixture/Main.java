package fixture;

import jpnco.simula.engine.EngineImpl;

/**
 * Integration fixture application: builds a real root engine from the framework (loaded as the
 * named module {@code simula}) so the agent's construction advice fires, prints app-bounded
 * markers, then exits. Used by {@code EngineCaptureIT}.
 */
public final class Main {

  private Main() {}

  /**
   * Entry point.
   *
   * @param args ignored
   */
  public static void main(final String[] args) {
    System.out.println("APP_START");
    new EngineImpl("it-root-engine", 1);
    sleep();
    System.out.println("APP_END");
    System.exit(0);
  }

  private static void sleep() {
    try {
      Thread.sleep(800);
    } catch (final InterruptedException exc) {
      Thread.currentThread().interrupt();
    }
  }
}
