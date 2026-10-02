package jpnco.simula.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AgentLogger}: prefixed, never-throwing diagnostics (research R5, FR-007).
 */
class AgentLoggerTest {

  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  private PrintStream original;

  @BeforeEach
  void redirectStderr() {
    original = System.err;
    System.setErr(new PrintStream(buffer, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreStderr() {
    System.setErr(original);
  }

  @Test
  void warn_is_prefixed() {
    AgentLogger.warn("something odd");
    final String out = buffer.toString(StandardCharsets.UTF_8);
    assertTrue(out.startsWith("[simula-agent] WARN something odd"), out);
  }

  @Test
  void error_is_prefixed() {
    AgentLogger.error("capture failed");
    final String out = buffer.toString(StandardCharsets.UTF_8);
    assertTrue(out.startsWith("[simula-agent] ERROR capture failed"), out);
  }

  @Test
  void null_message_never_throws() {
    AgentLogger.warn(null);
    AgentLogger.error(null);
    assertTrue(buffer.size() > 0);
  }
}
