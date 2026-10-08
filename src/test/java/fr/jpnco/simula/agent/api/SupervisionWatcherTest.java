package fr.jpnco.simula.agent.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.Event;
import fr.jpnco.simula.actors.SimulaSupervisor;
import fr.jpnco.simula.engine.EventImpl;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SupervisionWatcher}: listen/iterate-then-drain supervision (FR-102..105).
 */
class SupervisionWatcherTest {

  private final List<SupervisionEvent> events = new CopyOnWriteArrayList<>();
  private final List<Actor> rootActors = new ArrayList<>();
  private Engine root;
  private SimulaSupervisor supervisor;
  private SupervisionWatcher watcher;
  private Probe probe;

  @BeforeEach
  void wireEngineWithSupervisor() {
    root = mock(Engine.class);
    when(root.getChildren()).thenReturn(List.of());
    when(root.getActors()).thenReturn(rootActors);
    supervisor = new SimulaSupervisor(root);
    rootActors.add(supervisor);
    probe = new Probe(7, "probe", root);
    watcher = new SupervisionWatcher(root, events::add, 0L);
  }

  private void started(final Actor actor) {
    supervisor.process(EventImpl.createEvent(Engine.STARTED_ACTOR_EVENT, actor));
  }

  private void stopped(final Actor actor) {
    supervisor.process(EventImpl.createEvent(Engine.STOPPED_ACTOR_EVENT, actor));
  }

  @SuppressWarnings("unchecked")
  private static void forget(final SimulaSupervisor supervisor, final Actor actor)
      throws Exception {
    final Field states = SimulaSupervisor.class.getDeclaredField("states");
    states.setAccessible(true);
    ((Map<Actor, SimulaSupervisor.Status>) states.get(supervisor)).remove(actor);
  }

  @Test
  void first_record_is_reported_with_null_previous() {
    started(probe);
    watcher.cycle();
    final SupervisionEvent first =
        new SupervisionEvent(root, probe, null, SimulaSupervisor.Status.STARTED);
    assertEquals(List.of(first), events, "events=" + events);
    assertTrue(first.toString().contains("probe"), "toString=" + first);
    assertNotEquals(
        first, new SupervisionEvent(root, probe, null, SimulaSupervisor.Status.STOPPED));
  }

  @Test
  void transition_is_reported_and_unchanged_cycle_is_silent() {
    started(probe);
    watcher.cycle();
    stopped(probe);
    watcher.cycle();
    watcher.cycle();
    assertEquals(2, events.size(), "events=" + events);
    assertEquals(
        new SupervisionEvent(
            root, probe, SimulaSupervisor.Status.STARTED, SimulaSupervisor.Status.STOPPED),
        events.get(1),
        "events=" + events);
  }

  @Test
  void drain_reports_hinted_change_and_diff_deduplicates_it() {
    started(probe);
    watcher.cycle();
    stopped(probe);
    watcher.drainHints();
    assertEquals(2, events.size());
    assertEquals(SimulaSupervisor.Status.STOPPED, events.get(1).current());
    watcher.cycle();
    assertEquals(2, events.size());
  }

  @Test
  void disappearance_from_map_is_reported_with_null_current() throws Exception {
    started(probe);
    watcher.cycle();
    forget(supervisor, probe);
    watcher.cycle();
    assertEquals(2, events.size());
    assertNull(events.get(1).current());
    assertEquals(SimulaSupervisor.Status.STARTED, events.get(1).previous());
  }

  @Test
  void disappearance_via_hint_is_reported_once() throws Exception {
    started(probe);
    watcher.cycle();
    stopped(probe);
    forget(supervisor, probe);
    watcher.drainHints();
    assertEquals(2, events.size(), "events=" + events);
    assertEquals(SimulaSupervisor.Status.STARTED, events.get(1).previous());
    assertNull(events.get(1).current());
  }

  @Test
  void child_engine_supervisors_are_watched_recursively() {
    final Engine child = mock(Engine.class);
    final SimulaSupervisor childSupervisor = new SimulaSupervisor(child);
    final Probe childProbe = new Probe(8, "child-probe", child);
    when(child.getActors()).thenReturn(List.of(childSupervisor));
    when(child.getChildren()).thenReturn(List.of());
    when(root.getChildren()).thenReturn(List.of(child));
    childSupervisor.process(EventImpl.createEvent(Engine.STARTED_ACTOR_EVENT, childProbe));
    watcher.cycle();
    assertEquals(1, events.size());
    assertEquals(child, events.get(0).engine());
    assertEquals(childProbe, events.get(0).actor());
  }

  @Test
  void gone_supervisor_is_detached_and_returning_one_reattaches() {
    started(probe);
    watcher.cycle();
    assertEquals(1, watcher.supervisorCount());
    rootActors.clear();
    watcher.cycle();
    assertEquals(0, watcher.supervisorCount());
    rootActors.add(supervisor);
    final Probe late = new Probe(9, "late", root);
    started(late);
    watcher.cycle();
    assertEquals(1, watcher.supervisorCount());
    assertEquals(4, events.size(), "events=" + events);
    assertNull(events.get(1).current());
    assertEquals(SimulaSupervisor.Status.STARTED, events.get(1).previous());
    assertTrue(events.get(2).previous() == null && events.get(3).previous() == null);
  }

  @Test
  void throwing_sink_is_contained_and_state_still_advances() {
    final AtomicInteger attempts = new AtomicInteger();
    final SupervisionWatcher bad =
        new SupervisionWatcher(
            root,
            event -> {
              attempts.incrementAndGet();
              throw new IllegalStateException("boom");
            });
    started(probe);
    assertDoesNotThrow(bad::cycle);
    assertEquals(1, attempts.get());
    stopped(probe);
    assertDoesNotThrow(bad::cycle);
    assertEquals(2, attempts.get());
  }

  @Test
  void failing_engine_is_logged_and_the_loop_survives() throws Exception {
    final Engine broken = mock(Engine.class);
    when(broken.getActors()).thenThrow(new IllegalStateException("engine gone"));
    final SupervisionWatcher loop = new SupervisionWatcher(broken, events::add);
    final Thread thread = new Thread(loop);
    thread.start();
    Thread.sleep(200);
    assertTrue(thread.isAlive());
    thread.interrupt();
    thread.join(2000);
    assertFalse(thread.isAlive());
  }

  @Test
  void run_returns_promptly_on_interrupt() throws Exception {
    final Thread thread = SupervisionWatcher.start(root, events::add);
    thread.interrupt();
    thread.join(2000);
    assertFalse(thread.isAlive());
  }

  @Test
  void run_streams_events_and_rearms_while_parked() throws Exception {
    final Thread thread = SupervisionWatcher.start(root, events::add);
    Thread.sleep(150);
    started(probe);
    awaitEvents(1);
    stopped(probe);
    awaitEvents(2);
    thread.interrupt();
    thread.join(2000);
    assertFalse(thread.isAlive());
    assertEquals(SimulaSupervisor.Status.STARTED, events.get(0).current());
    assertEquals(SimulaSupervisor.Status.STOPPED, events.get(1).current());
  }

  private void awaitEvents(final int expected) throws InterruptedException {
    final long deadline = System.currentTimeMillis() + 5000;
    while (events.size() < expected && System.currentTimeMillis() < deadline) {
      Thread.sleep(20);
    }
    assertTrue(events.size() >= expected, "events=" + events);
  }

  @Test
  void late_child_engine_is_reported_retroactively() {
    watcher.cycle();
    final Engine child = mock(Engine.class);
    final SimulaSupervisor childSupervisor = new SimulaSupervisor(child);
    final Probe early = new Probe(14, "early", child);
    childSupervisor.process(EventImpl.createEvent(Engine.STARTED_ACTOR_EVENT, early));
    when(child.getActors()).thenReturn(List.of(childSupervisor));
    when(child.getChildren()).thenReturn(List.of());
    when(root.getChildren()).thenReturn(List.of(child));
    watcher.cycle();
    assertEquals(2, watcher.supervisorCount());
    assertEquals(1, events.size(), "events=" + events);
    assertNull(events.get(0).previous());
    assertEquals(early, events.get(0).actor());
    final Probe late = new Probe(15, "late", child);
    childSupervisor.process(EventImpl.createEvent(Engine.STARTED_ACTOR_EVENT, late));
    watcher.cycle();
    assertEquals(2, events.size(), "events=" + events);
    assertEquals(late, events.get(1).actor());
  }

  @Test
  void replacedSupervisorIsDetachedAndTheNewOneAttached() {
    watcher.cycle();
    assertEquals(1, watcher.supervisorCount());
    final SimulaSupervisor replacement = new SimulaSupervisor(root);
    rootActors.set(0, replacement);
    watcher.cycle();
    assertEquals(1, watcher.supervisorCount());
    final SupervisionEvent catchUp =
        new SupervisionEvent(root, supervisor, null, SimulaSupervisor.Status.STARTED);
    assertEquals(List.of(catchUp), events, "events=" + events);
  }

  @Test
  void engineValuedCallbackTriggersTargetedScanWithoutFullRescan() {
    final SupervisionWatcher slow = new SupervisionWatcher(root, events::add, 3_600_000L);
    slow.cycle();
    assertEquals(1, slow.supervisorCount());
    final Engine child = mock(Engine.class);
    final SimulaSupervisor childSupervisor = new SimulaSupervisor(child);
    final Probe early = new Probe(16, "early", child);
    childSupervisor.process(EventImpl.createEvent(Engine.STARTED_ACTOR_EVENT, early));
    when(child.getActors()).thenReturn(List.of(childSupervisor));
    when(child.getChildren()).thenReturn(List.of());
    when(child.getEngine()).thenReturn(root);
    when(root.getChildren()).thenReturn(List.of(child));
    started(child);
    slow.cycle();
    assertEquals(2, slow.supervisorCount());
    assertTrue(events.stream().anyMatch(event -> event.actor().equals(early)), "events=" + events);
    assertTrue(events.stream().anyMatch(event -> event.actor().equals(child)), "events=" + events);
  }

  @Test
  void lateRegisteredActorGetsSyntheticStart() {
    watcher.cycle();
    final Probe late = new Probe(21, "late", root);
    rootActors.add(late);
    watcher.cycle();
    assertEquals(1, events.size(), "events=" + events);
    assertEquals(late, events.get(0).actor());
    assertNull(events.get(0).previous());
    assertEquals(SimulaSupervisor.Status.STARTED, events.get(0).current());
  }

  @Test
  void unregisteredActorGetsSyntheticStop() {
    watcher.cycle();
    final Probe late = new Probe(22, "late", root);
    rootActors.add(late);
    watcher.cycle();
    rootActors.remove(late);
    watcher.cycle();
    assertEquals(2, events.size(), "events=" + events);
    assertEquals(late, events.get(1).actor());
    assertEquals(SimulaSupervisor.Status.STARTED, events.get(1).previous());
    assertEquals(SimulaSupervisor.Status.STOPPED, events.get(1).current());
  }

  @Test
  void retiredSupervisorRemovesSyntheticMembers() {
    watcher.cycle();
    final Probe late = new Probe(23, "late", root);
    rootActors.add(late);
    watcher.cycle();
    rootActors.clear();
    watcher.cycle();
    assertEquals(2, events.size(), "events=" + events);
    assertEquals(late, events.get(1).actor());
    assertEquals(SimulaSupervisor.Status.STARTED, events.get(1).previous());
    assertNull(events.get(1).current());
  }

  @Test
  void supervisorRecordedActorIsNeverSynthesized() {
    started(probe);
    rootActors.add(probe);
    watcher.cycle();
    assertEquals(1, events.size(), "events=" + events);
    assertEquals(probe, events.get(0).actor());
    assertEquals(SimulaSupervisor.Status.STARTED, events.get(0).current());
    watcher.cycle();
    assertEquals(1, events.size(), "events=" + events);
  }

  @Test
  void unregisteredButRecordedActorGetsNoSyntheticStop() {
    started(probe);
    rootActors.add(probe);
    watcher.cycle();
    events.clear();
    rootActors.remove(probe);
    watcher.cycle();
    assertTrue(events.isEmpty(), "events=" + events);
  }

  @Test
  void retiringSupervisorReportsOnlySyntheticMembers() {
    started(probe);
    rootActors.add(probe);
    watcher.cycle();
    final Probe late = new Probe(24, "late", root);
    rootActors.add(late);
    watcher.cycle();
    events.clear();
    rootActors.clear();
    watcher.cycle();
    assertEquals(2, events.size(), "events=" + events);
    assertTrue(
        events.stream().anyMatch(event -> event.actor().equals(probe) && event.current() == null),
        "events=" + events);
    assertTrue(
        events.stream().anyMatch(event -> event.actor().equals(late) && event.current() == null),
        "events=" + events);
  }

  @Test
  void duplicate_hints_in_one_batch_are_merged() {
    watcher.cycle();
    final Probe other = new Probe(13, "other", root);
    started(probe);
    stopped(probe);
    started(other);
    watcher.cycle();
    assertEquals(2, events.size(), "events=" + events);
    assertTrue(events.stream().allMatch(event -> event.current() != null));
  }

  @Test
  void orphan_hint_after_detach_is_ignored() {
    started(probe);
    watcher.cycle();
    final Probe late = new Probe(11, "late", root);
    started(late);
    rootActors.clear();
    assertDoesNotThrow(watcher::cycle);
    assertEquals(4, events.size(), "events=" + events);
    assertTrue(
        events.stream().anyMatch(event -> event.actor().equals(late) && event.current() == null));
  }

  @Test
  void plain_actors_never_discover_a_supervisor_but_get_synthesized() {
    final Probe plain = new Probe(12, "plain", root);
    rootActors.add(plain);
    watcher.cycle();
    assertEquals(1, watcher.supervisorCount());
    assertEquals(1, events.size(), "events=" + events);
    assertEquals(plain, events.get(0).actor());
    assertNull(events.get(0).previous());
    assertEquals(SimulaSupervisor.Status.STARTED, events.get(0).current());
  }

  @Test
  void start_rejects_null_arguments() {
    assertNull(SupervisionWatcher.start(null, events::add));
    assertNull(SupervisionWatcher.start(root, null));
  }

  /** A minimal, inert actor usable as a supervision event source. */
  private static final class Probe implements Actor {

    private final Integer id;
    private final String name;
    private final Engine engine;

    Probe(final Integer id, final String name, final Engine engine) {
      this.id = id;
      this.name = name;
      this.engine = engine;
    }

    @Override
    public Actor getDelegate() {
      return this;
    }

    @Override
    public Integer getId() {
      return id;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public Engine getEngine() {
      return engine;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public void process(final Event event) {}
  }
}
