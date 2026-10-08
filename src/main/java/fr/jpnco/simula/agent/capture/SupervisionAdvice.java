package fr.jpnco.simula.agent.capture;

import net.bytebuddy.asm.Advice;

/**
 * The inline advice template that forces supervision on for an engine by setting the framework's
 * {@code isSupervised} field to {@code true} at the entry of the documented instrumentation anchor
 * {@code EngineImpl.checkSupervision()} (capture-contract.md: the framework keeps this method and
 * the {@code isSupervised} field stable precisely so an agent can drive them).
 *
 * <p>Like {@link EngineCaptureAdvice}, the body is inlined into the framework class at load time,
 * so it references only the instrumented type's own {@code boolean} field — no agent or framework
 * type appears in the generated code, keeping it valid inside the named {@code simula} module. The
 * write is confined by {@code suppress = Throwable.class}: if the field is ever absent or renamed,
 * the advice silently does nothing and the host is untouched (Constitution IX).
 *
 * <p>Implements: agent-options contract ({@code supervise} option), Constitution IX.
 */
public final class SupervisionAdvice {

  /** Private constructor to prevent instantiation of this advice template holder. */
  private SupervisionAdvice() {}

  /**
   * Runs before the advised method body and writes {@code true} back into the {@code isSupervised}
   * field.
   *
   * @param isSupervised the framework field, bound writable
   */
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void forceSupervised(
      @Advice.FieldValue(value = "isSupervised", readOnly = false) boolean isSupervised) {
    isSupervised = true;
  }
}
