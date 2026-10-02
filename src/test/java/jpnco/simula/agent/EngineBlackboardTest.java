package jpnco.simula.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link EngineBlackboard}: the property-key registry (FR-002, FR-009). */
class EngineBlackboardTest {

  @BeforeEach
  @AfterEach
  void resetBoard() {
    System.getProperties().remove(EngineBlackboard.PROPERTY_KEY);
  }

  @Test
  void create_is_idempotent() {
    final List<Object> first = EngineBlackboard.create();
    final List<Object> second = EngineBlackboard.create();
    assertSame(first, second);
  }

  @Test
  void append_preserves_capture_order() {
    EngineBlackboard.create();
    final Object a = new Object();
    final Object b = new Object();
    EngineBlackboard.append(a);
    EngineBlackboard.append(b);
    assertEquals(List.of(a, b), EngineBlackboard.snapshot());
  }

  @Test
  void snapshot_is_an_immutable_copy() {
    EngineBlackboard.create();
    final Object a = new Object();
    EngineBlackboard.append(a);
    final List<Object> copy = EngineBlackboard.snapshot();
    EngineBlackboard.append(new Object());
    assertEquals(1, copy.size());
    assertThrows(UnsupportedOperationException.class, copy::clear);
  }

  @Test
  void append_without_board_is_a_silent_noop() {
    EngineBlackboard.append(new Object());
    assertFalse(System.getProperties().containsKey(EngineBlackboard.PROPERTY_KEY));
    assertTrue(EngineBlackboard.snapshot().isEmpty());
  }

  @Test
  void snapshot_without_board_is_empty() {
    assertTrue(EngineBlackboard.snapshot().isEmpty());
  }

  @Test
  void concurrent_appends_are_all_visible() throws InterruptedException {
    EngineBlackboard.create();
    final int threads = 4;
    final int perThread = 100;
    final CountDownLatch start = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(threads);
    for (int t = 0; t < threads; t++) {
      final Thread worker =
          new Thread(
              () -> {
                try {
                  start.await();
                  for (int i = 0; i < perThread; i++) {
                    EngineBlackboard.append(new Object());
                  }
                } catch (final InterruptedException exc) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              });
      worker.start();
    }
    start.countDown();
    done.await();
    assertEquals(threads * perThread, EngineBlackboard.snapshot().size());
  }
}
