package fr.jpnco.simula.agent.observers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link StopRequest}: cooperative stop dispatch from the demo boxes (FR-108). */
class StopRequestTest {

  @Test
  void engines_are_stopped_through_their_own_stop() {
    final Engine engine = mock(Engine.class);
    StopRequest.send(engine);
    verify(engine).stop();
  }

  @Test
  void plain_actors_are_asked_to_stop_themselves() {
    final Actor actor = mock(Actor.class);
    StopRequest.send(actor);
    verify(actor).stopMe();
  }

  @Test
  void null_targets_are_ignored() {
    assertDoesNotThrow(() -> StopRequest.send(null));
  }

  @Test
  void failures_are_contained() {
    final Actor actor = mock(Actor.class);
    when(actor.getName()).thenReturn("boom");
    doThrow(new IllegalStateException("gone")).when(actor).stopMe();
    assertDoesNotThrow(() -> StopRequest.send(actor));
    verify(actor).stopMe();
  }
}
