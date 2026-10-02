package jpnco.simula.agent.api;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.concurrent.TimeUnit;
import jpnco.simula.Engine;
import jpnco.simula.agent.EngineBlackboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SimulaTarget}: typed accessor and wait (FR-003, FR-004). */
class SimulaTargetTest {

  @BeforeEach
  @AfterEach
  void resetBoard() {
    System.getProperties().remove(EngineBlackboard.PROPERTY_KEY);
  }

  @Test
  void currentRootEngine_null_on_empty_board() {
    System.getProperties().remove(EngineBlackboard.PROPERTY_KEY);
    assertNull(SimulaTarget.currentRootEngine());
    assertTrue(!SimulaTarget.isAvailable());
  }

  @Test
  void returns_engine_after_append() {
    EngineBlackboard.create();
    final Engine engine = mock(Engine.class);
    EngineBlackboard.append(engine);
    assertSame(engine, SimulaTarget.currentRootEngine());
    assertTrue(SimulaTarget.isAvailable());
  }

  @Test
  void current_is_latest_when_several_captured() {
    EngineBlackboard.create();
    final Engine first = mock(Engine.class);
    final Engine second = mock(Engine.class);
    EngineBlackboard.append(first);
    EngineBlackboard.append(second);
    assertSame(second, SimulaTarget.currentRootEngine());
  }

  @Test
  void non_engine_entries_are_skipped() {
    EngineBlackboard.create();
    EngineBlackboard.append("not-an-engine");
    final Engine engine = mock(Engine.class);
    EngineBlackboard.append(engine);
    EngineBlackboard.append(42);
    assertSame(engine, SimulaTarget.currentRootEngine());
  }

  @Test
  void await_returns_immediately_when_present() {
    EngineBlackboard.create();
    final Engine engine = mock(Engine.class);
    EngineBlackboard.append(engine);
    assertSame(engine, SimulaTarget.awaitRootEngine(1, TimeUnit.SECONDS));
  }

  @Test
  void await_returns_null_after_timeout() {
    System.getProperties().remove(EngineBlackboard.PROPERTY_KEY);
    final long start = System.nanoTime();
    assertNull(SimulaTarget.awaitRootEngine(150, TimeUnit.MILLISECONDS));
    final long elapsedMs = (System.nanoTime() - start) / 1_000_000;
    assertTrue(elapsedMs >= 140, "waited too little: " + elapsedMs);
  }

  @Test
  void await_returns_current_when_interrupted_while_waiting() {
    System.getProperties().remove(EngineBlackboard.PROPERTY_KEY);
    Thread.currentThread().interrupt();
    try {
      assertNull(SimulaTarget.awaitRootEngine(5, TimeUnit.SECONDS));
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void await_returns_as_soon_as_engine_appears_mid_wait() throws InterruptedException {
    final Engine engine = mock(Engine.class);
    final Thread poster =
        new Thread(
            () -> {
              try {
                Thread.sleep(60);
              } catch (final InterruptedException exc) {
                Thread.currentThread().interrupt();
              }
              EngineBlackboard.create();
              EngineBlackboard.append(engine);
            });
    poster.start();
    assertSame(engine, SimulaTarget.awaitRootEngine(3, TimeUnit.SECONDS));
    poster.join();
  }
}
