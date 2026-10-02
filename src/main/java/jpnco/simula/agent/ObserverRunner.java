package jpnco.simula.agent;

import java.util.concurrent.TimeUnit;
import jpnco.simula.Engine;
import jpnco.simula.agent.api.SimulaObserver;
import jpnco.simula.agent.api.SimulaTarget;

/**
 * Loads the configured observer, waits for the engine, and delivers exactly one outcome on a daemon
 * thread (observer-api contract).
 *
 * <p>Every step — class loading, instantiation, the wait, and the callback — is contained: any
 * {@link Throwable} is logged and never reaches the host application, and precisely one of {@link
 * SimulaObserver#engineAvailable} or {@link SimulaObserver#engineUnavailable} runs.
 *
 * <p>This class is part of the instrumentation agent; it is not part of the simula contract.
 *
 * <p>Implements: FR-005, FR-006, FR-007.
 */
public final class ObserverRunner {

  /** The name given to the observer daemon thread. */
  private static final String THREAD_NAME = "simula-agent-observer";

  private final AgentOptions options;

  /**
   * Creates a runner bound to the parsed options. Participates in: FR-005.
   *
   * @param options the parsed agent options
   */
  public ObserverRunner(final AgentOptions options) {
    this.options = options;
  }

  /**
   * Executes the whole observer session on the calling thread. Participates in: FR-005, FR-006,
   * FR-007. Never throws.
   */
  public void run() {
    final String observerClassName = options.getObserverClassName();
    if (observerClassName == null) {
      return;
    }
    try {
      final SimulaObserver observer = instantiate(observerClassName);
      if (observer == null) {
        return;
      }
      final Engine engine =
          SimulaTarget.awaitRootEngine(options.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
      if (engine != null) {
        observer.engineAvailable(engine);
      } else {
        observer.engineUnavailable();
      }
    } catch (final Throwable throwable) {
      AgentLogger.error("observer session failed: " + throwable);
    }
  }

  private SimulaObserver instantiate(final String observerClassName) {
    try {
      final ClassLoader loader = classLoader();
      final Class<?> type = Class.forName(observerClassName, false, loader);
      if (!SimulaObserver.class.isAssignableFrom(type)) {
        AgentLogger.error("observer is not a SimulaObserver: " + observerClassName);
        return null;
      }
      return (SimulaObserver) type.getDeclaredConstructor().newInstance();
    } catch (final Throwable throwable) {
      AgentLogger.error("could not instantiate observer " + observerClassName + ": " + throwable);
      return null;
    }
  }

  private static ClassLoader classLoader() {
    final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    return contextLoader != null ? contextLoader : ObserverRunner.class.getClassLoader();
  }

  /**
   * Builds (but does not start) the daemon thread that runs the observer session for the given
   * options. Participates in: FR-005.
   *
   * @param options the parsed agent options
   * @return a named daemon thread ready to start
   */
  public static Thread threadFor(final AgentOptions options) {
    final Thread thread = new Thread(() -> new ObserverRunner(options).run(), THREAD_NAME);
    thread.setDaemon(true);
    return thread;
  }
}
