package fr.jpnco.simula.agent.capture;

/**
 * Test fixture mirroring the framework supervision anchor: a private {@code isSupervised} field and
 * a {@code checkSupervision} method whose body copies the field. The supervision advice must flip
 * the field to {@code true} before the body runs, so {@code observedDuringCheck} becomes {@code
 * true} only when the advice is installed.
 */
public class SupervisableFixture {

  private boolean isSupervised = false;

  boolean observedDuringCheck;

  void checkSupervision() {
    observedDuringCheck = isSupervised;
  }
}
