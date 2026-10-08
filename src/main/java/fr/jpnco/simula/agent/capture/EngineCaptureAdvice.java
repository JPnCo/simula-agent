package fr.jpnco.simula.agent.capture;

import fr.jpnco.simula.agent.EngineBlackboard;
import java.util.List;
import net.bytebuddy.asm.Advice;

/**
 * The inline advice template that records the root engine at construction time (capture contract,
 * research R2).
 *
 * <p>The template is compiled here but its body is <em>inlined</em> into the framework canonical
 * {@code EngineImpl} constructor at transformation time. For that inlined code to run inside a
 * named module it may reference only {@code java.base} types, so it reads the {@link
 * EngineBlackboard} through the {@code java.base} properties blackboard and never mentions any
 * agent type. The {@link EngineBlackboard#PROPERTY_KEY} reference is a {@code String} compile-time
 * constant and is therefore inlined by the Java compiler, leaving no runtime reference to the agent
 * class behind.
 *
 * <p>Only the root engine (the one constructed with a {@code null} parent) is recorded. The advice
 * suppresses every {@link Throwable} so a capture problem can never abort engine construction.
 *
 * <p>Implements: FR-002, FR-009.
 */
public final class EngineCaptureAdvice {

  /** Private constructor to prevent instantiation of this advice template holder. */
  private EngineCaptureAdvice() {}

  /**
   * Runs after the advised constructor and appends the new instance to the blackboard when it is a
   * root engine (its parent argument is {@code null}).
   *
   * @param parent the second constructor argument, the parent engine, {@code null} for the root
   * @param self the freshly constructed engine instance
   */
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void onExit(
      @Advice.Argument(1) final Object parent, @Advice.This final Object self) {
    if (parent == null) {
      final Object board = System.getProperties().get(EngineBlackboard.PROPERTY_KEY);
      if (board instanceof List) {
        @SuppressWarnings("unchecked")
        final List<Object> captures = (List<Object>) board;
        captures.add(self);
      }
    }
  }
}
