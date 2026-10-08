package fr.jpnco.simula.agent.observers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.actors.SimulaSupervisor;
import fr.jpnco.simula.agent.api.SupervisionEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link StatusLedger}: the watcher-to-FX hand-off queue (FR-106). */
class StatusLedgerTest {

  private static SupervisionEvent event() {
    return new SupervisionEvent(
        mock(Engine.class), mock(Actor.class), null, SimulaSupervisor.Status.STARTED);
  }

  @Test
  void shared_is_a_singleton() {
    assertSame(StatusLedger.shared(), StatusLedger.shared());
  }

  @Test
  void record_then_drain_preserves_order_and_empties() {
    final StatusLedger ledger = new StatusLedger();
    final SupervisionEvent first = event();
    final SupervisionEvent second = event();
    ledger.record(first);
    ledger.record(second);
    assertEquals(List.of(first, second), ledger.drain());
    assertTrue(ledger.drain().isEmpty());
  }

  @Test
  void null_events_are_ignored() {
    final StatusLedger ledger = new StatusLedger();
    ledger.record(null);
    assertTrue(ledger.drain().isEmpty());
  }
}
