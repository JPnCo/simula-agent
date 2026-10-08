package fr.jpnco.simula.agent.capture;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Installs the construction-time capture advice on the framework engine type (capture contract,
 * research R2/R3).
 *
 * <p>Matching is by the documented structural contract of the canonical {@code EngineImpl}
 * constructor — four arguments with a {@code fr.jpnco.simula.Engine} parent in position one —
 * rather than by any compiled reference, so the agent never links against the framework at build
 * time. A transformation failure is logged and swallowed so the host JVM is never harmed.
 *
 * <p>This class is part of the instrumentation agent; it is not part of the simula contract.
 *
 * <p>Implements: FR-002, FR-009, Constitution IX.
 */
public final class EngineTransformer {

  /** The fully qualified name of the framework engine implementation. */
  private static final String TARGET_TYPE = "fr.jpnco.simula.engine.EngineImpl";

  /** The framework engine interface, matched by name to avoid a compile-time link. */
  private static final String ENGINE_INTERFACE = "fr.jpnco.simula.Engine";

  /** The framework's stable instrumentation anchor where supervision is decided. */
  private static final String SUPERVISION_METHOD = "checkSupervision";

  private EngineTransformer() {}

  /**
   * The fully qualified name of the type under transformation. Participates in: FR-002.
   *
   * @return the target type name
   */
  public static String targetTypeName() {
    return TARGET_TYPE;
  }

  /**
   * The matcher selecting exactly the canonical constructor that carries the parent engine.
   * Participates in: FR-002.
   *
   * @return a matcher over constructor declarations
   */
  public static ElementMatcher<? super MethodDescription> constructorMatcher() {
    return ElementMatchers.isConstructor()
        .and(ElementMatchers.takesArguments(4))
        .and(ElementMatchers.takesArgument(0, ElementMatchers.named("java.lang.String")))
        .and(ElementMatchers.takesArgument(1, ElementMatchers.named(ENGINE_INTERFACE)));
  }

  /**
   * Registers the capture advice with the running JVM. Any failure is reported and contained.
   * Participates in: FR-002, FR-009.
   *
   * @param instrumentation the instrumentation instance handed to premain
   */
  public static void install(final Instrumentation instrumentation) {
    new AgentBuilder.Default()
        .with(new LoggingAgentListener())
        .type(ElementMatchers.named(TARGET_TYPE))
        .transform(
            (builder, typeDescription, classLoader, module, protectionDomain) ->
                builder.visit(Advice.to(EngineCaptureAdvice.class).on(constructorMatcher())))
        .installOn(instrumentation);
  }

  /**
   * Registers the supervision advice so every engine raises the framework's {@code isSupervised}
   * flag before its {@code checkSupervision} body runs. Any failure is reported and contained.
   * Participates in: agent-options contract ({@code supervise}), Constitution IX.
   *
   * @param instrumentation the instrumentation instance handed to premain
   */
  public static void installSupervision(final Instrumentation instrumentation) {
    new AgentBuilder.Default()
        .with(new LoggingAgentListener())
        .type(ElementMatchers.named(TARGET_TYPE))
        .transform(
            (builder, typeDescription, classLoader, module, protectionDomain) ->
                builder.visit(
                    Advice.to(SupervisionAdvice.class)
                        .on(ElementMatchers.named(SUPERVISION_METHOD))))
        .installOn(instrumentation);
  }
}
