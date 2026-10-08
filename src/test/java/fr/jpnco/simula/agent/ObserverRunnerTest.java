package fr.jpnco.simula.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import fr.jpnco.simula.Engine;
import fr.jpnco.simula.agent.api.SimulaObserver;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ObserverRunner}: exactly-one-outcome delivery and full failure containment
 * (FR-005, FR-006, FR-007).
 */
class ObserverRunnerTest {

  /** Delivers the engine to a static reference for assertions. */
  public static final class CapturingObserver implements SimulaObserver {
    static final AtomicReference<Engine> ENGINE = new AtomicReference<>();
    static final AtomicInteger CALLS = new AtomicInteger();

    @Override
    public void engineAvailable(final Engine engine) {
      CALLS.incrementAndGet();
      ENGINE.set(engine);
    }
  }

  /** Records the not-available outcome. */
  public static final class UnavailableObserver implements SimulaObserver {
    static final AtomicInteger CALLS = new AtomicInteger();

    @Override
    public void engineAvailable(final Engine engine) {}

    @Override
    public void engineUnavailable() {
      CALLS.incrementAndGet();
    }
  }

  /** Throws inside the delivery callback. */
  public static final class ThrowingObserver implements SimulaObserver {
    static final AtomicInteger CALLS = new AtomicInteger();

    @Override
    public void engineAvailable(final Engine engine) {
      CALLS.incrementAndGet();
      throw new IllegalStateException("observer blew up");
    }
  }

  /** Lacks a public no-argument constructor. */
  public static final class NoDefaultCtorObserver implements SimulaObserver {
    private NoDefaultCtorObserver() {}

    @Override
    public void engineAvailable(final Engine engine) {}
  }

  /** Not an observer at all. */
  public static final class NotAnObserver {}

  private PrintStream originalErr;

  @BeforeEach
  void setUp() {
    System.getProperties().remove(EngineBlackboard.PROPERTY_KEY);
    CapturingObserver.ENGINE.set(null);
    CapturingObserver.CALLS.set(0);
    UnavailableObserver.CALLS.set(0);
    ThrowingObserver.CALLS.set(0);
    originalErr = System.err;
    System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void tearDown() {
    System.setErr(originalErr);
    System.getProperties().remove(EngineBlackboard.PROPERTY_KEY);
  }

  private static AgentOptions optionsFor(final Class<?> observer, final long timeoutSeconds) {
    return AgentOptions.parse("observer=" + observer.getName() + ";timeout=" + timeoutSeconds);
  }

  @Test
  void delivers_the_engine_exactly_once_when_available() {
    EngineBlackboard.create();
    final Engine engine = mock(Engine.class);
    EngineBlackboard.append(engine);

    new ObserverRunner(optionsFor(CapturingObserver.class, 1)).run();

    assertSame(engine, CapturingObserver.ENGINE.get());
    assertEquals(1, CapturingObserver.CALLS.get());
  }

  @Test
  void delivers_unavailable_once_when_no_engine_appears() {
    new ObserverRunner(optionsFor(UnavailableObserver.class, 1)).run();
    assertEquals(1, UnavailableObserver.CALLS.get());
  }

  @Test
  void observer_exception_is_contained_and_session_stays_terminal() {
    EngineBlackboard.create();
    EngineBlackboard.append(mock(Engine.class));

    new ObserverRunner(optionsFor(ThrowingObserver.class, 1)).run();

    assertEquals(1, ThrowingObserver.CALLS.get());
  }

  @Test
  void unknown_observer_class_is_logged_without_failure() {
    final AgentOptions options = AgentOptions.parse("observer=com.acme.DoesNotExist;timeout=1");
    new ObserverRunner(options).run();
    assertEquals(0, CapturingObserver.CALLS.get());
  }

  @Test
  void observer_without_default_constructor_is_logged_without_failure() {
    EngineBlackboard.create();
    EngineBlackboard.append(mock(Engine.class));
    new ObserverRunner(optionsFor(NoDefaultCtorObserver.class, 1)).run();
  }

  @Test
  void non_observer_class_is_logged_without_failure() {
    EngineBlackboard.create();
    EngineBlackboard.append(mock(Engine.class));
    new ObserverRunner(optionsFor(NotAnObserver.class, 1)).run();
  }

  @Test
  void no_observer_configured_does_nothing() {
    new ObserverRunner(AgentOptions.parse(null)).run();
    assertEquals(0, CapturingObserver.CALLS.get());
  }

  @Test
  void falls_back_to_own_loader_when_context_loader_is_null() {
    EngineBlackboard.create();
    final Engine engine = mock(Engine.class);
    EngineBlackboard.append(engine);
    final ClassLoader saved = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(null);
    try {
      new ObserverRunner(optionsFor(CapturingObserver.class, 1)).run();
    } finally {
      Thread.currentThread().setContextClassLoader(saved);
    }
    assertSame(engine, CapturingObserver.ENGINE.get());
  }

  @Test
  void thread_for_is_named_daemon() {
    EngineBlackboard.create();
    EngineBlackboard.append(mock(Engine.class));
    final Thread thread = ObserverRunner.threadFor(optionsFor(CapturingObserver.class, 1));
    assertTrue(thread.isDaemon());
    assertEquals("simula-agent-observer", thread.getName());
  }
}
