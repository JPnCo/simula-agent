package fixture;

import fr.jpnco.simula.engine.EngineImpl;

/**
 * Integration fixture application: builds and runs a real supervised root engine plus five child
 * engines so every supervisor records actor lifecycle transitions while {@code WatchObserver}
 * watches. Used by {@code SupervisionIT}.
 */
public final class SupervisedMain {

  private SupervisedMain() {}

  /**
   * Entry point.
   *
   * @param args ignored
   */
  public static void main(final String[] args) {
    System.out.println("APP_START");
    final EngineImpl root = new EngineImpl("watch-root-engine", 1);
    for (int i = 0; i < 5; i++) {
      new EngineImpl("watch-child-engine-" + i, root, 1);
    }
    root.start();
    sleep(1500);
    root.stop();
    sleep(1500);
    System.out.println("APP_END");
    System.exit(0);
  }

  private static void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    } catch (final InterruptedException exc) {
      Thread.currentThread().interrupt();
    }
  }
}
