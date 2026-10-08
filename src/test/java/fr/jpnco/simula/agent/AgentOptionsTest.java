package fr.jpnco.simula.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AgentOptions}: parsing of the {@code -javaagent} option string (FR-008,
 * agent-options contract).
 */
class AgentOptionsTest {

  @Test
  void parse_null_returns_defaults() {
    final AgentOptions options = AgentOptions.parse(null);
    assertNull(options.getObserverClassName());
    assertEquals(Duration.ofSeconds(30), options.getTimeout());
  }

  @Test
  void parse_empty_returns_defaults() {
    final AgentOptions options = AgentOptions.parse("");
    assertNull(options.getObserverClassName());
    assertEquals(Duration.ofSeconds(30), options.getTimeout());
  }

  @Test
  void parse_reads_observer_and_timeout() {
    final AgentOptions options = AgentOptions.parse("observer=com.acme.MyObserver;timeout=5");
    assertEquals("com.acme.MyObserver", options.getObserverClassName());
    assertEquals(Duration.ofSeconds(5), options.getTimeout());
  }

  @Test
  void parse_is_case_insensitive_and_trims() {
    final AgentOptions options = AgentOptions.parse(" TIMEOUT = 7 ; Observer=com.acme.O ");
    assertEquals("com.acme.O", options.getObserverClassName());
    assertEquals(Duration.ofSeconds(7), options.getTimeout());
  }

  @Test
  void parse_ignores_pair_without_equals() {
    final AgentOptions options = AgentOptions.parse("observer=com.acme.O;garbage;timeout=9");
    assertEquals("com.acme.O", options.getObserverClassName());
    assertEquals(Duration.ofSeconds(9), options.getTimeout());
  }

  @Test
  void parse_ignores_unknown_keys() {
    final AgentOptions options = AgentOptions.parse("port=9434;observer=com.acme.O");
    assertEquals("com.acme.O", options.getObserverClassName());
    assertEquals(Duration.ofSeconds(30), options.getTimeout());
  }

  @Test
  void parse_non_numeric_timeout_falls_back_to_default() {
    final AgentOptions options = AgentOptions.parse("timeout=abc");
    assertEquals(Duration.ofSeconds(30), options.getTimeout());
  }

  @Test
  void parse_timeout_below_one_falls_back_to_default() {
    assertEquals(Duration.ofSeconds(30), AgentOptions.parse("timeout=0").getTimeout());
    assertEquals(Duration.ofSeconds(30), AgentOptions.parse("timeout=-3").getTimeout());
  }

  @Test
  void parse_empty_observer_value_is_treated_as_absent() {
    final AgentOptions options = AgentOptions.parse("observer=");
    assertNull(options.getObserverClassName());
  }

  @Test
  void parse_supervise_defaults_to_false() {
    assertFalse(AgentOptions.parse("observer=com.acme.O").isSupervise());
    assertFalse(AgentOptions.parse(null).isSupervise());
  }

  @Test
  void parse_supervise_true_is_case_insensitive() {
    assertTrue(AgentOptions.parse("supervise=true").isSupervise());
    assertTrue(AgentOptions.parse(" SUPERVISE = TRUE ").isSupervise());
  }

  @Test
  void parse_supervise_other_values_are_false() {
    assertFalse(AgentOptions.parse("supervise=false").isSupervise());
    assertFalse(AgentOptions.parse("supervise=yes").isSupervise());
    assertFalse(AgentOptions.parse("supervise=").isSupervise());
  }
}
