package fixture;

/**
 * Integration fixture that never builds an engine, to prove the observer receives the not-available
 * outcome and that a non-simula JVM is unaffected by the agent. Used by {@code EngineCaptureIT}.
 */
public final class Idle {

  private Idle() {}

  /**
   * Entry point.
   *
   * @param args ignored
   */
  public static void main(final String[] args) {
    System.out.println("IDLE_START");
    try {
      Thread.sleep(2000);
    } catch (final InterruptedException exc) {
      Thread.currentThread().interrupt();
    }
    System.out.println("IDLE_END");
    System.exit(0);
  }
}
