package jpnco.simula.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration tests (SC-001, SC-002, SC-004): spawn real JVMs with the shaded agent jar
 * and the framework on the module path, then assert the fixture output. These run in the dedicated
 * {@code integration-tests} surefire execution, after packaging, so the shaded jar and dependency
 * paths are injected as system properties.
 */
class EngineCaptureIT {

  private static final long PROCESS_TIMEOUT_SECONDS = 60L;
  private static final String OBSERVER = "fixture.ITObserver";

  private static final String JAVA =
      Paths.get(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java")
          .toString();
  private static final String AGENT_JAR = required("agent.jar.path");
  private static final String SIMULA_JAR = required("simula.jar.path");
  private static final String FIXTURE_CP = required("fixture.classpath");

  private static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  private static String required(final String property) {
    final String value = System.getProperty(property);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "system property '" + property + "' is not set; run under the integration-tests phase");
    }
    return value;
  }

  private record RunResult(int exitCode, String output) {}

  private static RunResult run(final List<String> args) throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>();
    command.add(JAVA);
    command.addAll(args);
    final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    final String output;
    try (InputStream in = process.getInputStream()) {
      output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    final boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new IllegalStateException("fixture JVM did not terminate: " + command);
    }
    return new RunResult(process.exitValue(), output);
  }

  private static String agentArg(final long timeoutSeconds) {
    return "-javaagent:" + AGENT_JAR + "=observer=" + OBSERVER + ";timeout=" + timeoutSeconds;
  }

  @Test
  void captures_and_uses_the_real_root_engine_through_its_typed_api() throws Exception {
    final RunResult result =
        run(
            List.of(
                agentArg(15),
                "--module-path",
                SIMULA_JAR,
                "--add-modules",
                "simula",
                "-cp",
                FIXTURE_CP,
                "fixture.Main"));
    assertEquals(0, result.exitCode(), "output:\n" + result.output());
    assertTrue(result.output().contains("APP_START"), result.output());
    assertTrue(result.output().contains("APP_END"), result.output());
    assertTrue(result.output().contains("CAPTURED id="), result.output());
  }

  @Test
  void delivers_unavailable_when_no_engine_appears() throws Exception {
    final RunResult result = run(List.of(agentArg(1), "-cp", FIXTURE_CP, "fixture.Idle"));
    assertEquals(0, result.exitCode(), "output:\n" + result.output());
    assertTrue(result.output().contains("IDLE_START"), result.output());
    assertTrue(result.output().contains("UNAVAILABLE"), result.output());
  }

  @Test
  void leaves_the_host_output_and_exit_code_untouched_without_agent() throws Exception {
    final RunResult withAgent =
        run(
            List.of(
                agentArg(15),
                "--module-path",
                SIMULA_JAR,
                "--add-modules",
                "simula",
                "-cp",
                FIXTURE_CP,
                "fixture.Main"));
    final RunResult withoutAgent =
        run(
            List.of(
                "--module-path",
                SIMULA_JAR,
                "--add-modules",
                "simula",
                "-cp",
                FIXTURE_CP,
                "fixture.Main"));
    assertEquals(withoutAgent.exitCode(), withAgent.exitCode());
    assertFalse(withoutAgent.output().contains("CAPTURED"), withoutAgent.output());
    assertTrue(withoutAgent.output().contains("APP_START"), withoutAgent.output());
    assertTrue(withoutAgent.output().contains("APP_END"), withoutAgent.output());
    assertTrue(withAgent.output().contains("CAPTURED id="), withAgent.output());
  }
}
