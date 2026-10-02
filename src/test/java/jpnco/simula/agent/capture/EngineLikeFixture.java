package jpnco.simula.agent.capture;

/**
 * Test fixture constructor shape mirroring the framework canonical constructor: {@code (String,
 * Object, int, Object)}. Kept free of any framework types so its transformed copy can be loaded in
 * an isolated child-first class loader (advice body references only {@code java.base} types).
 */
public class EngineLikeFixture {

  /** No-op constructor; the transformation under test advises it. */
  public EngineLikeFixture(
      final String title, final Object parent, final int timeFactor, final Object mode) {
    // Deliberately empty: only construction-time capture is observed.
  }
}
