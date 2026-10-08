package fr.jpnco.simula.agent.observers;

import fr.jpnco.simula.agent.api.SupervisionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thread-safe hand-off of supervision events between the watcher thread and the JavaFX Application
 * Thread (FR-106).
 *
 * <p>The watcher only records (enqueue); the FX refresh loop drains. Because every mutation of the
 * view's observable map then happens on the FX thread, JavaFX collection listeners fire legally and
 * no cross-thread list mutation can occur.
 *
 * <p>Part of the JavaFX demo observer. Implements: FR-106.
 */
public final class StatusLedger {

  /** The process-wide ledger shared by the observer and the FXML controller. */
  private static final StatusLedger SHARED = new StatusLedger();

  /** The queue of events recorded by the watcher and not yet drained. */
  private final LinkedBlockingQueue<SupervisionEvent> pending = new LinkedBlockingQueue<>();

  /**
   * The shared ledger used by the demo observer wiring. Participates in: FR-106.
   *
   * @return the singleton ledger, never {@code null}
   */
  public static StatusLedger shared() {
    return SHARED;
  }

  /**
   * Records one event for later draining; {@code null} events are ignored. Participates in: FR-106.
   *
   * @param event the supervision event to record, possibly {@code null}
   */
  public void record(final SupervisionEvent event) {
    if (event != null) {
      pending.offer(event);
    }
  }

  /**
   * Removes and returns all pending events in arrival order. Participates in: FR-106.
   *
   * @return the drained events, oldest first
   */
  public List<SupervisionEvent> drain() {
    final List<SupervisionEvent> drained = new ArrayList<>();
    pending.drainTo(drained);
    return drained;
  }
}
