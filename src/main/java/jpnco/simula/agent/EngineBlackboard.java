package jpnco.simula.agent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The JVM-wide registry of captured engines, reachable across module boundaries through a {@code
 * java.base} blackboard (research R1).
 *
 * <p>The framework runs as a named module and therefore cannot reference any agent type, while the
 * inlined capture advice can only use {@code java.base}. A {@link System#getProperties()} entry (a
 * {@code Hashtable<Object,Object>}) is the one structure visible to both, so the agent stores the
 * capture list here under {@link #PROPERTY_KEY}. The advice appends raw {@link Object} instances;
 * this class exposes the create/append/snapshot primitives and keeps every list operation on a
 * {@link CopyOnWriteArrayList} for lock-free readers and atomic appends.
 *
 * <p>This class is part of the instrumentation agent; it is not part of the simula contract.
 *
 * <p>Implements: FR-002, FR-009.
 */
public final class EngineBlackboard {

  /**
   * The property key holding the capture list. Declared as a {@code String} constant so the Java
   * compiler inlines it into the capture advice, keeping the advice free of any runtime reference
   * to this (module-invisible) class.
   */
  public static final String PROPERTY_KEY = "jpnco.simula.agent.engine.registry";

  /** Private constructor to prevent instantiation of this utility class. */
  private EngineBlackboard() {}

  /**
   * Creates the capture list on first call and returns the (possibly pre-existing) list. Idempotent
   * so a repeated premain or a re-entrant call never replaces an existing registry. Participates
   * in: FR-002, FR-009.
   *
   * @return the live capture list, never {@code null}
   */
  public static List<Object> create() {
    final java.util.Properties properties = System.getProperties();
    synchronized (properties) {
      final Object existing = properties.get(PROPERTY_KEY);
      if (existing instanceof List) {
        @SuppressWarnings("unchecked")
        final List<Object> board = (List<Object>) existing;
        return board;
      }
      final List<Object> board = new CopyOnWriteArrayList<>();
      properties.put(PROPERTY_KEY, board);
      return board;
    }
  }

  /**
   * Appends a captured engine instance. A no-op when no board has been created (for example when
   * the advice fires before premain finished, or in a non-simula JVM test) — it never creates the
   * board and never throws. Participates in: FR-002, FR-007, FR-009.
   *
   * @param engine the engine instance to record
   */
  public static void append(final Object engine) {
    final Object board = System.getProperties().get(PROPERTY_KEY);
    if (board instanceof List) {
      @SuppressWarnings("unchecked")
      final List<Object> captures = (List<Object>) board;
      captures.add(engine);
    }
  }

  /**
   * Returns an immutable snapshot of the captured instances in capture order, or an empty list when
   * no board exists. Participates in: FR-002, FR-009.
   *
   * @return an immutable snapshot of the captures
   */
  public static List<Object> snapshot() {
    final Object board = System.getProperties().get(PROPERTY_KEY);
    if (board instanceof List) {
      @SuppressWarnings("unchecked")
      final List<Object> captures = (List<Object>) board;
      return List.copyOf(captures);
    }
    return List.of();
  }
}
