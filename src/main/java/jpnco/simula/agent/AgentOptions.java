package jpnco.simula.agent;

import java.time.Duration;

/**
 * The immutable, parsed view of the {@code -javaagent} option string (agent-options contract).
 *
 * <p>Options are {@code key=value} pairs separated by {@code ;} (or {@code ,}), case-insensitive on
 * the key. Only {@code observer} and {@code timeout} are recognised; every other key is ignored
 * with a warning, and any malformed pair falls back to the documented default rather than aborting.
 *
 * <p>This class is part of the instrumentation agent; it is not part of the simula contract.
 *
 * <p>Implements: FR-008.
 */
public final class AgentOptions {

  /** The recognized option key naming the observer class. */
  private static final String KEY_OBSERVER = "observer";

  /** The recognized option key naming the wait timeout, in seconds. */
  private static final String KEY_TIMEOUT = "timeout";

  /** The recognized option key enabling the supervision advice. */
  private static final String KEY_SUPERVISE = "supervise";

  /** The literal value that turns a boolean option on. */
  private static final String VALUE_TRUE = "true";

  /** The default observer wait timeout. */
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  /** The minimum accepted timeout, in seconds. */
  private static final long MIN_TIMEOUT_SECONDS = 1L;

  private final String observerClassName;
  private final Duration timeout;
  private final boolean supervise;

  private AgentOptions(
      final String observerClassName, final Duration timeout, final boolean supervise) {
    this.observerClassName = observerClassName;
    this.timeout = timeout;
    this.supervise = supervise;
  }

  /**
   * Parses the raw premain argument into options, warning about (but never failing on) anything it
   * cannot use. Participates in: FR-008.
   *
   * @param agentArgs the raw {@code -javaagent} option string, possibly {@code null}
   * @return the parsed options, never {@code null}
   */
  public static AgentOptions parse(final String agentArgs) {
    String observer = null;
    Duration timeout = DEFAULT_TIMEOUT;
    boolean supervise = false;
    if (agentArgs != null && !agentArgs.isBlank()) {
      for (final String pair : agentArgs.split("[;,]")) {
        final int equals = pair.indexOf('=');
        if (equals < 0) {
          AgentLogger.warn("ignoring option without '=': " + pair.trim());
          continue;
        }
        final String key = pair.substring(0, equals).trim().toLowerCase();
        final String value = pair.substring(equals + 1).trim();
        switch (key) {
          case KEY_OBSERVER -> observer = value.isEmpty() ? null : value;
          case KEY_TIMEOUT -> timeout = parseTimeout(value);
          case KEY_SUPERVISE -> supervise = VALUE_TRUE.equalsIgnoreCase(value);
          default -> AgentLogger.warn("ignoring unknown option key: " + key);
        }
      }
    }
    return new AgentOptions(observer, timeout, supervise);
  }

  private static Duration parseTimeout(final String value) {
    try {
      final long seconds = Long.parseLong(value);
      if (seconds < MIN_TIMEOUT_SECONDS) {
        AgentLogger.warn("timeout below minimum, using default: " + value);
        return DEFAULT_TIMEOUT;
      }
      return Duration.ofSeconds(seconds);
    } catch (final NumberFormatException exc) {
      AgentLogger.warn("invalid timeout, using default: " + value);
      return DEFAULT_TIMEOUT;
    }
  }

  /**
   * The fully qualified observer class name, or {@code null} when no observer is configured.
   * Participates in: FR-008.
   *
   * @return the observer class name, possibly {@code null}
   */
  public String getObserverClassName() {
    return observerClassName;
  }

  /**
   * How long the observer thread waits for the engine to appear. Participates in: FR-008.
   *
   * @return the wait timeout, never {@code null}
   */
  public Duration getTimeout() {
    return timeout;
  }

  /**
   * Whether the supervision advice should be installed (the {@code supervise=true} option).
   * Participates in: FR-008.
   *
   * @return {@code true} when supervision was requested
   */
  public boolean isSupervise() {
    return supervise;
  }
}
