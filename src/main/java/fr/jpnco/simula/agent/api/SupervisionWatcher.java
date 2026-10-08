package fr.jpnco.simula.agent.api;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.actors.SimulaSupervisor;
import fr.jpnco.simula.actors.SupervisionListener;
import fr.jpnco.simula.agent.AgentLogger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Watches the supervision states of an engine tree and streams {@link SupervisionEvent}s to a
 * developer consumer (FR-102, FR-103).
 *
 * <p>Discovery is event-driven: the engine tree is walked once, and a late-started engine is
 * discovered through the supervision callback itself — when a supervisor records an actor that is
 * an {@link Engine}, that engine's subtree is scanned on the next cycle and its own supervisor
 * attached with a catch-up of its already-recorded states. A full-tree walk and per-supervisor
 * catch-up snapshots repeat only on the one-second safety rescan, which covers the one case no
 * listener can announce: a child added through {@code addChild} that is never registered as an
 * actor.
 *
 * <p>Each framework callback only enqueues a {@code (supervisor, actor)} hint; after discovery the
 * cycle drains the hint queue and re-reads the live state per hinted actor, so a change landing
 * mid-scan is reported by the drain instead of being lost, and an already-reported change is
 * deduplicated by the snapshot.
 *
 * <p>Actors the framework never reports — registered after the supervisor was built, which the
 * framework records only at its own construction — are reconciled from raw membership: present in
 * {@code getActors()} but absent from the supervisor states announces a synthetic {@code null ->
 * STARTED}, and disappearing from both announces a synthetic {@code STARTED -> STOPPED} (FR-110).
 *
 * <p>Every dispatch is exception-contained: a throwing consumer is logged and the cycle continues
 * (Constitution IX). The watcher holds no framework type beyond the exported supervision API.
 *
 * <p>This class is part of the instrumentation agent; it is not part of the simula contract.
 *
 * <p>Implements: FR-102, FR-103, FR-104, FR-105, FR-110.
 */
public final class SupervisionWatcher implements Runnable {

  /** The name given to the watcher daemon thread. */
  private static final String THREAD_NAME = "simula-agent-supervision";

  /** The idle wait between cycles, in milliseconds. */
  private static final long POLL_INTERVAL_MS = 50L;

  /**
   * The fallback interval between full-tree rescans, in milliseconds: needed only because the
   * framework's {@code addChild} notifies no one for a child that is never registered as an actor.
   */
  private static final long RESYNC_INTERVAL_MS = 1000L;

  /** The engine tree to watch for supervisors. */
  private final Engine root;

  /** The developer sink receiving every emitted event. */
  private final Consumer<SupervisionEvent> sink;

  /** The rescan interval in milliseconds; {@code 0} rescans every cycle (tests). */
  private final long resyncIntervalMs;

  /** Hints offered by framework callbacks, drained after each scan. */
  private final LinkedBlockingQueue<Hint> hints = new LinkedBlockingQueue<>();

  /** Engines recorded as actors by an attached supervisor, awaiting a subtree scan. */
  private final LinkedBlockingQueue<Engine> scans = new LinkedBlockingQueue<>();

  /** The last known state snapshot per attached supervisor. */
  private final Map<SimulaSupervisor, Map<Actor, SimulaSupervisor.Status>> lastSeen =
      new IdentityHashMap<>();

  /** The framework listener attached per known supervisor, for later detachment. */
  private final Map<SimulaSupervisor, SupervisionListener> attached = new IdentityHashMap<>();

  /** The supervisor currently known for each scanned engine, possibly none. */
  private final Map<Engine, SimulaSupervisor> supervised = new IdentityHashMap<>();

  /** The last actor-membership snapshot per scanned engine, for synthetic start/stop (FR-110). */
  private final Map<Engine, Set<Actor>> lastMembers = new IdentityHashMap<>();

  /** Timestamp of the last full-tree walk; {@code 0} when none happened yet. */
  private long lastRescanNanos;

  /**
   * Creates a watcher with the production rescan interval; run it on a thread or drive {@link
   * #cycle()} from tests. Participates in: FR-102.
   *
   * @param root the root engine whose tree is watched, not {@code null}
   * @param sink the consumer of status-change events, not {@code null}
   */
  public SupervisionWatcher(final Engine root, final Consumer<SupervisionEvent> sink) {
    this(root, sink, RESYNC_INTERVAL_MS);
  }

  /**
   * Creates a watcher with an explicit rescan interval.
   *
   * @param root the root engine whose tree is watched, not {@code null}
   * @param sink the consumer of status-change events, not {@code null}
   * @param resyncIntervalMs milliseconds between full-tree rescans; {@code 0} rescans every cycle
   */
  SupervisionWatcher(
      final Engine root, final Consumer<SupervisionEvent> sink, final long resyncIntervalMs) {
    this.root = Objects.requireNonNull(root, "root");
    this.sink = Objects.requireNonNull(sink, "sink");
    this.resyncIntervalMs = resyncIntervalMs;
  }

  /**
   * Starts the watcher on a named daemon thread. Participates in: FR-102.
   *
   * @param root the root engine whose tree is watched
   * @param events the consumer of status-change events
   * @return the started daemon thread, or {@code null} when an argument is {@code null}
   */
  public static Thread start(final Engine root, final Consumer<SupervisionEvent> events) {
    if (root == null || events == null) {
      AgentLogger.warn("supervision watcher not started: null argument");
      return null;
    }
    final Thread thread = new Thread(new SupervisionWatcher(root, events), THREAD_NAME);
    thread.setDaemon(true);
    thread.start();
    return thread;
  }

  /**
   * Runs cycles until the current thread is interrupted. Never throws; a failing cycle is logged
   * and retried. Participates in: FR-102, FR-104, Constitution IX.
   */
  @Override
  public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        cycle();
      } catch (final Throwable throwable) {
        AgentLogger.error("supervision cycle failed: " + throwable);
      }
      try {
        final Hint hint = hints.poll(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        if (hint != null) {
          hints.add(hint);
        }
      } catch (final InterruptedException exc) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  /**
   * One watch cycle: the safety rescan when due (with per-supervisor catch-up and detachment of
   * vanished supervisors), then the subtree scans requested by engine-valued callbacks, then the
   * hint drain. Participates in: FR-103, FR-105. Package-private seam for unit tests.
   */
  void cycle() {
    if (lastRescanNanos == 0L
        || System.nanoTime() - lastRescanNanos >= TimeUnit.MILLISECONDS.toNanos(resyncIntervalMs)) {
      lastRescanNanos = System.nanoTime();
      final Map<Engine, SimulaSupervisor> found = new IdentityHashMap<>();
      walk(root, found);
      applyScan(found, true);
      for (final SimulaSupervisor supervisor : List.copyOf(attached.keySet())) {
        syncFromMap(supervisor);
      }
    }
    final List<Engine> requested = new ArrayList<>();
    scans.drainTo(requested);
    for (final Engine engine : requested) {
      final Map<Engine, SimulaSupervisor> found = new IdentityHashMap<>();
      walk(engine, found);
      applyScan(found, false);
    }
    drainHints();
  }

  private static void walk(final Engine engine, final Map<Engine, SimulaSupervisor> found) {
    SimulaSupervisor supervisor = null;
    for (final Actor actor : engine.getActors()) {
      if (actor instanceof SimulaSupervisor candidate) {
        supervisor = candidate;
      }
    }
    found.put(engine, supervisor);
    for (final Engine child : engine.getChildren()) {
      walk(child, found);
    }
  }

  private void applyScan(final Map<Engine, SimulaSupervisor> found, final boolean global) {
    for (final Map.Entry<Engine, SimulaSupervisor> entry : found.entrySet()) {
      SimulaSupervisor known = supervised.get(entry.getKey());
      final SimulaSupervisor current = entry.getValue();
      if (known != null && known != current) {
        retire(entry.getKey(), known);
        supervised.remove(entry.getKey());
        known = null;
      }
      if (current != null) {
        if (known == null) {
          supervised.put(entry.getKey(), current);
          attach(current);
        }
        reconcile(entry.getKey(), current);
      }
    }
    if (global) {
      for (final Engine engine : List.copyOf(supervised.keySet())) {
        if (!found.containsKey(engine)) {
          retire(engine, supervised.remove(engine));
        }
      }
    }
  }

  /**
   * Synthetic lifecycle reconciliation for actors the framework never reports (FR-110): the
   * framework records supervisors built after the actors, and publishes no event for a plain actor
   * registered later, so such an actor present in {@code getActors()} but absent from the
   * supervisor states is announced {@code null -> STARTED}, and its disappearance from both is
   * announced {@code STARTED -> STOPPED}. Supervisors themselves are engine plumbing, already
   * covered by their own states, and are excluded. Participates in: FR-110.
   */
  private void reconcile(final Engine engine, final SimulaSupervisor supervisor) {
    final Map<Actor, SimulaSupervisor.Status> states = supervisor.getStates();
    final Set<Actor> previous = lastMembers.get(engine);
    final Set<Actor> current = Collections.newSetFromMap(new IdentityHashMap<>());
    for (final Actor actor : engine.getActors()) {
      if (actor instanceof SimulaSupervisor) {
        continue;
      }
      current.add(actor);
      if (!states.containsKey(actor) && (previous == null || !previous.contains(actor))) {
        emit(supervisor, actor, null, SimulaSupervisor.Status.STARTED);
      }
    }
    if (previous != null) {
      for (final Actor actor : previous) {
        if (!current.contains(actor) && !states.containsKey(actor)) {
          emit(supervisor, actor, SimulaSupervisor.Status.STARTED, SimulaSupervisor.Status.STOPPED);
        }
      }
    }
    lastMembers.put(engine, current);
  }

  private void attach(final SimulaSupervisor supervisor) {
    if (attached.containsKey(supervisor)) {
      return;
    }
    lastSeen.put(supervisor, new HashMap<>());
    final SupervisionListener listener =
        (actor, previous, current) -> {
          hints.offer(new Hint(supervisor, actor));
          if (actor instanceof Engine lateEngine) {
            scans.offer(lateEngine);
          }
        };
    supervisor.addSupervisionListener(listener);
    attached.put(supervisor, listener);
    syncFromMap(supervisor);
  }

  private void retire(final Engine engine, final SimulaSupervisor supervisor) {
    syncFromMap(supervisor);
    final Map<Actor, SimulaSupervisor.Status> seen = lastSeen.get(supervisor);
    for (final Actor actor : lastMembers.getOrDefault(engine, Set.of())) {
      if (!seen.containsKey(actor)) {
        emit(supervisor, actor, SimulaSupervisor.Status.STARTED, null);
      }
    }
    lastMembers.remove(engine);
    new HashMap<>(seen).forEach((actor, before) -> emit(supervisor, actor, before, null));
    supervisor.removeSupervisionListener(attached.remove(supervisor));
    lastSeen.remove(supervisor);
  }

  private void syncFromMap(final SimulaSupervisor supervisor) {
    final Map<Actor, SimulaSupervisor.Status> current = new HashMap<>(supervisor.getStates());
    final Map<Actor, SimulaSupervisor.Status> previous = lastSeen.get(supervisor);
    for (final Map.Entry<Actor, SimulaSupervisor.Status> entry :
        new ArrayList<>(previous.entrySet())) {
      final SimulaSupervisor.Status before = entry.getValue();
      final SimulaSupervisor.Status now = current.get(entry.getKey());
      if (!Objects.equals(before, now)) {
        if (now == null) {
          previous.remove(entry.getKey());
        } else {
          previous.put(entry.getKey(), now);
        }
        emit(supervisor, entry.getKey(), before, now);
      }
    }
    for (final Map.Entry<Actor, SimulaSupervisor.Status> entry : current.entrySet()) {
      if (!previous.containsKey(entry.getKey())) {
        previous.put(entry.getKey(), entry.getValue());
        emit(supervisor, entry.getKey(), null, entry.getValue());
      }
    }
  }

  /** Drains queued hints, re-reading the live map per hinted actor. Package-private seam. */
  void drainHints() {
    final Deque<Hint> batch = new ArrayDeque<>();
    hints.drainTo(batch);
    final List<Hint> seen = new ArrayList<>();
    for (final Hint hint : batch) {
      if (!containsSame(seen, hint)) {
        seen.add(hint);
        apply(hint);
      }
    }
  }

  private static boolean containsSame(final List<Hint> hints, final Hint candidate) {
    for (final Hint hint : hints) {
      if (hint.supervisor() == candidate.supervisor() && hint.actor().equals(candidate.actor())) {
        return true;
      }
    }
    return false;
  }

  private void apply(final Hint hint) {
    final Map<Actor, SimulaSupervisor.Status> previous = lastSeen.get(hint.supervisor());
    if (previous == null) {
      return;
    }
    final SimulaSupervisor.Status now = hint.supervisor().getStates().get(hint.actor());
    final SimulaSupervisor.Status before = previous.get(hint.actor());
    if (!Objects.equals(before, now)) {
      if (now == null) {
        previous.remove(hint.actor());
      } else {
        previous.put(hint.actor(), now);
      }
      emit(hint.supervisor(), hint.actor(), before, now);
    }
  }

  private void emit(
      final SimulaSupervisor supervisor,
      final Actor actor,
      final SimulaSupervisor.Status previous,
      final SimulaSupervisor.Status current) {
    try {
      sink.accept(new SupervisionEvent(supervisor.getEngine(), actor, previous, current));
    } catch (final Throwable throwable) {
      AgentLogger.error("supervision listener failed: " + throwable);
    }
  }

  /** The number of supervisors currently attached; test seam for discovery and detachment. */
  int supervisorCount() {
    return attached.size();
  }

  /** A callback hint: an actor of one supervisor may have changed. */
  private record Hint(SimulaSupervisor supervisor, Actor actor) {}
}
