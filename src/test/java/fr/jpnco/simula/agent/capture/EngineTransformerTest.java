package fr.jpnco.simula.agent.capture;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.jpnco.simula.agent.EngineBlackboard;
import fr.jpnco.simula.engine.EngineImpl;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the construction-time capture advice (FR-002, FR-009, Constitution IX). The
 * fixture is renamed and loaded child-first so its advised constructor runs in isolation while
 * {@code java.base} and the agent classes still resolve from the app loader.
 */
class EngineTransformerTest {

  private static final AtomicInteger COUNTER = new AtomicInteger();
  private final ByteBuddy byteBuddy = new ByteBuddy();

  @BeforeEach
  @AfterEach
  void resetBoard() {
    System.getProperties().remove(EngineBlackboard.PROPERTY_KEY);
  }

  private Class<?> advisedClass() {
    final String name = "fr.jpnco.simula.agent.capture.RenamedEngine$" + COUNTER.incrementAndGet();
    final DynamicType.Unloaded<?> unloaded =
        byteBuddy
            .redefine(EngineLikeFixture.class)
            .name(name)
            .visit(Advice.to(EngineCaptureAdvice.class).on(isConstructor()))
            .make();
    final Map<String, byte[]> types = Map.of(name, unloaded.getBytes());
    try {
      return Class.forName(
          name, false, new ByteArrayClassLoader.ChildFirst(getClass().getClassLoader(), types));
    } catch (final ClassNotFoundException exc) {
      throw new IllegalStateException(exc);
    }
  }

  private Object construct(final Class<?> type, final Object parent) throws Exception {
    final Constructor<?> constructor =
        type.getDeclaredConstructor(String.class, Object.class, int.class, Object.class);
    return constructor.newInstance("root", parent, 1, null);
  }

  @Test
  void null_parent_appends_the_instance_to_the_board() throws Exception {
    EngineBlackboard.create();
    final Class<?> type = advisedClass();
    final Object engine = construct(type, null);
    final List<Object> board = EngineBlackboard.snapshot();
    assertEquals(1, board.size());
    assertTrue(board.contains(engine));
  }

  @Test
  void non_null_parent_is_not_captured() throws Exception {
    EngineBlackboard.create();
    final Class<?> type = advisedClass();
    final Object child = construct(type, new Object());
    assertTrue(EngineBlackboard.snapshot().isEmpty());
    assertFalse(EngineBlackboard.snapshot().contains(child));
  }

  @Test
  void capture_is_silent_when_board_is_absent() throws Exception {
    final Class<?> type = advisedClass();
    construct(type, null);
    assertFalse(System.getProperties().containsKey(EngineBlackboard.PROPERTY_KEY));
  }

  @Test
  void type_matcher_targets_engine_impl_by_name() {
    assertEquals("fr.jpnco.simula.engine.EngineImpl", EngineTransformer.targetTypeName());
    assertTrue(EngineImpl.class.getName().equals(EngineTransformer.targetTypeName()));
  }

  @Test
  void constructor_matcher_selects_the_canonical_parent_constructor_only() {
    final Constructor<?>[] constructors = EngineImpl.class.getDeclaredConstructors();
    final List<Constructor<?>> matched =
        java.util.Arrays.stream(constructors)
            .filter(
                ctor ->
                    EngineTransformer.constructorMatcher()
                        .matches(new MethodDescription.ForLoadedConstructor(ctor)))
            .toList();
    assertEquals(1, matched.size());
    final Class<?>[] params = matched.get(0).getParameterTypes();
    assertEquals(4, params.length);
    assertEquals(String.class, params[0]);
    assertEquals(fr.jpnco.simula.Engine.class, params[1]);
  }

  @Test
  void install_registers_the_transformer_without_error() {
    final java.lang.instrument.Instrumentation instrumentation =
        org.mockito.Mockito.mock(java.lang.instrument.Instrumentation.class);
    org.junit.jupiter.api.Assertions.assertDoesNotThrow(
        () -> EngineTransformer.install(instrumentation));
  }

  @Test
  void supervision_advice_sets_the_field_before_the_body_runs() throws Exception {
    final String name = "fr.jpnco.simula.agent.capture.RenamedSup$" + COUNTER.incrementAndGet();
    final DynamicType.Unloaded<?> unloaded =
        byteBuddy
            .redefine(SupervisableFixture.class)
            .name(name)
            .visit(Advice.to(SupervisionAdvice.class).on(named("checkSupervision")))
            .make();
    final Class<?> type =
        Class.forName(
            name,
            false,
            new ByteArrayClassLoader.ChildFirst(
                getClass().getClassLoader(), Map.of(name, unloaded.getBytes())));

    final Object instance = type.getDeclaredConstructor().newInstance();
    final Method check = type.getDeclaredMethod("checkSupervision");
    check.setAccessible(true);
    check.invoke(instance);

    final Field observed = type.getDeclaredField("observedDuringCheck");
    observed.setAccessible(true);
    assertTrue((boolean) observed.get(instance));
  }

  @Test
  void install_supervision_registers_the_transformer_without_error() {
    final java.lang.instrument.Instrumentation instrumentation =
        org.mockito.Mockito.mock(java.lang.instrument.Instrumentation.class);
    org.junit.jupiter.api.Assertions.assertDoesNotThrow(
        () -> EngineTransformer.installSupervision(instrumentation));
  }
}
