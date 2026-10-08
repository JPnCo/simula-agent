package fr.jpnco.simula.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * End-to-end supervision test (FR-102..FR-105): spawns a real JVM with the shaded agent in {@code
 * supervise=true} mode, runs a real engine, and asserts the watcher's typed event stream on the
 * fixture output. Runs in the dedicated {@code integration-tests} surefire execution.
 */
class SupervisionIT {

  private static final long PROCESS_TIMEOUT_SECONDS = 90L;

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

  @Test
  void streams_start_and_stop_events_for_a_supervised_engine() throws Exception {
    final RunResult result =
        run(
            List.of(
                "-javaagent:"
                    + AGENT_JAR
                    + "=observer=fixture.WatchObserver;timeout=20;supervise=true",
                "--module-path",
                SIMULA_JAR,
                "--add-modules",
                "simula",
                "-cp",
                FIXTURE_CP,
                "fixture.SupervisedMain"));
    assertEquals(0, result.exitCode(), "output:\n" + result.output());
    assertTrue(result.output().contains("APP_START"), result.output());
    assertTrue(result.output().contains("WATCH_ARMED"), result.output());
    assertTrue(result.output().contains("EVT SimulaSupervisor:watch-root-engine"), result.output());
    assertTrue(result.output().contains("EVT watch-root-engine"), result.output());
    assertTrue(result.output().contains("EVT watch-child-engine-0 null->STARTED"), result.output());
    assertTrue(result.output().contains("EVT watch-child-engine-4 null->STARTED"), result.output());
    assertTrue(result.output().contains("EVT"), result.output());
    assertTrue(result.output().contains("null->STARTED"), result.output());
    assertTrue(result.output().contains("STARTED->null"), result.output());
    assertTrue(result.output().contains("APP_END"), result.output());
  }
}
