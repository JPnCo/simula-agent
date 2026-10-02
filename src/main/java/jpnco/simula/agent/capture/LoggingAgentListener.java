package jpnco.simula.agent.capture;

import jpnco.simula.agent.AgentLogger;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.utility.JavaModule;

/**
 * An {@link AgentBuilder.Listener} that reports transformation problems through {@link AgentLogger}
 * and never propagates them, keeping instrumentation strictly non-fatal to the host.
 *
 * <p>This class is part of the instrumentation agent; it is not part of the simula contract.
 *
 * <p>Implements: FR-007, FR-009, Constitution IX.
 */
final class LoggingAgentListener extends AgentBuilder.Listener.Adapter {

  @Override
  public void onError(
      final String typeName,
      final ClassLoader classLoader,
      final JavaModule module,
      final boolean loaded,
      final Throwable throwable) {
    AgentLogger.error("could not transform " + typeName + ": " + throwable);
  }
}
