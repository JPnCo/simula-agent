package jpnco.simula.agent;

import java.lang.instrument.Instrumentation;
import jpnco.simula.agent.capture.EngineTransformer;

/**
 * The {@code -javaagent} entry point: creates the blackboard, installs the engine capture advice,
 * and launches the observer session on a daemon thread.
 *
 * <p>Premain never throws: any failure is logged and the host JVM continues unaffected.
 *
 * <p>This class is part of the instrumentation agent; it is not part of the simula contract.
 *
 * <p>Implements: FR-001, FR-002, FR-005, Constitution IX. Excluded from coverage (thin premain
 * bootstrap verified by the integration test).
 */
public final class SimulaAgent {

  /** Private constructor to prevent instantiation of this entry-point holder. */
  private SimulaAgent() {}

  /**
   * The premain hook invoked by the JVM before the application {@code main}.
   *
   * @param agentArgs the raw {@code -javaagent} option string, possibly {@code null}
   * @param instrumentation the instrumentation instance for class transformation
   */
  public static void premain(final String agentArgs, final Instrumentation instrumentation) {
    try {
      EngineBlackboard.create();
      final AgentOptions options = AgentOptions.parse(agentArgs);
      EngineTransformer.install(instrumentation);
      if (options.isSupervise()) {
        EngineTransformer.installSupervision(instrumentation);
      }
      ObserverRunner.threadFor(options).start();
    } catch (final Throwable throwable) {
      AgentLogger.error("agent failed to start: " + throwable);
    }
  }
}
