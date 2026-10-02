package jpnco.simula.agent.observers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import jpnco.simula.Engine;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link SimpleObserver}: the demo observer prints engine state without failing
 * (FR-005).
 */
class SimpleObserverTest {

  @Test
  void engine_available_prints_children_and_actors() {
    final Engine engine = mock(Engine.class);
    when(engine.getChildren()).thenReturn(List.of());
    when(engine.getActors()).thenReturn(List.of());
    assertDoesNotThrow(() -> new SimpleObserver().engineAvailable(engine));
  }
}
