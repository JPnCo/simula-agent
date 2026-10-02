package jpnco.simula.agent;

/**
 * The {@code -javaagent} diagnostics logger: prefixed lines on {@code System.err} that never throw
 * (research R5).
 *
 * <p>An agent cannot adopt the host application's logging configuration without coupling to it, and
 * writing to {@code System.out} would corrupt the application's observable output. This logger is
 * therefore the single channel for agent messages, always prefixed so it is unmistakable and always
 * on {@code System.err}.
 *
 * <p>This class is part of the instrumentation agent; it is not part of the simula contract.
 *
 * <p>Implements: FR-007, SC-002, SC-003.
 */
public final class AgentLogger {

  /** The prefix written before every agent message. */
  private static final String PREFIX = "[simula-agent] ";

  /** The severity marker for warnings. */
  private static final String WARN = "WARN ";

  /** The severity marker for errors. */
  private static final String ERROR = "ERROR ";

  /** Private constructor to prevent instantiation of this utility class. */
  private AgentLogger() {}

  /**
   * Writes a warning line. A {@code null} message is rendered as the literal text; the method never
   * throws. Participates in: FR-007, FR-008.
   *
   * @param message the message to log
   */
  public static void warn(final String message) {
    System.err.println(PREFIX + WARN + message);
  }

  /**
   * Writes an error line. A {@code null} message is rendered as the literal text; the method never
   * throws. Participates in: FR-007.
   *
   * @param message the message to log
   */
  public static void error(final String message) {
    System.err.println(PREFIX + ERROR + message);
  }
}
