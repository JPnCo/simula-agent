package fr.jpnco.simula.agent.observers;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.actors.SimulaSupervisor;
import fr.jpnco.simula.agent.api.SimulaTarget;
import fr.jpnco.simula.agent.api.SupervisionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Controller for {@code main-view.fxml}: a {@link SplitPane} whose left {@link TreeView} shows the
 * engine tree (root plus children, refreshed live from {@link SimulaTarget}) and whose right {@link
 * FlowPane} renders one box per actor of the engine selected in the tree, labelled with the actor's
 * name and color-coded by the supervision status fed live through {@link StatusLedger} (FR-106).
 *
 * <p>Part of the JavaFX demo observer; excluded from the coverage gate (requires the FX toolkit and
 * a display). Implements: FR-005, FR-106..FR-109, observer-api contract.
 */
public final class MainViewController {

  private static final long REFRESH_MILLIS = 500L;
  private static final String ACTOR_BOX_STYLE = "actor-box";
  private static final String ACTOR_NAME_STYLE = "actor-name";
  private static final String ACTOR_ICON_STYLE = "actor-icon";
  private static final String TREE_ICON_STYLE = "tree-cell-icon";
  private static final double ICON_SPACING = 6.0;
  private static final String ENGINE_ICON = "\uD83D\uDE80\uFE0E";
  private static final String ACTOR_ICON = "\u2699\uFE0E";
  private static final String STOP_LABEL = "Stop";

  @FXML private TreeView<Actor> engineTree;
  @FXML private FlowPane actorPane;

  private final Map<String, TreeItem<Actor>> items = new LinkedHashMap<>();

  private final Map<String, List<Actor>> rememberedActors = new LinkedHashMap<>();

  private boolean statusRefreshPending;

  /** Live lifecycle status per engine/actor key, mutated only on the FX thread. */
  private final ObservableMap<String, SimulaSupervisor.Status> statuses =
      FXCollections.observableHashMap();

  /**
   * Wires the tree, its selection and the periodic refresh once the FXML is injected. Participates
   * in: FR-005, FR-106, FR-107, FR-109.
   */
  @FXML
  void initialize() {
    engineTree.setCellFactory(view -> new ActorCell(this::actorStatus));
    engineTree
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observed, oldValue, newValue) ->
                renderActors(paneEngineFor(newValue == null ? null : newValue.getValue())));
    statuses.addListener(
        (MapChangeListener<String, SimulaSupervisor.Status>) change -> scheduleStatusRefresh());
    final Timeline refresh =
        new Timeline(new KeyFrame(Duration.millis(REFRESH_MILLIS), event -> refreshEngines()));
    refresh.setCycleCount(Timeline.INDEFINITE);
    refresh.play();
    refreshEngines();
  }

  /**
   * Coalesces status-change re-renders: all map mutations landing in the same FX pulse repaint the
   * tree and the pane exactly once, so a burst of supervision events cannot turn into one full
   * re-render per event. Participates in: FR-106.
   */
  private void scheduleStatusRefresh() {
    if (statusRefreshPending) {
      return;
    }
    statusRefreshPending = true;
    Platform.runLater(
        () -> {
          statusRefreshPending = false;
          engineTree.refresh();
          renderActors(paneEngineFor(selectedActor()));
        });
  }

  /**
   * Moves every event recorded by the watcher thread into the observable status map, on the FX
   * thread; a disappearance ({@code current == null}) reads as {@code STOPPED} so the box keeps its
   * red color instead of losing it. Participates in: FR-106.
   */
  private void applyLedger() {
    for (final SupervisionEvent event : StatusLedger.shared().drain()) {
      final String key = statusKey(event.actor());
      if (event.current() == null) {
        if (!SimulaSupervisor.Status.STOPPED.equals(statuses.get(key))) {
          statuses.put(key, SimulaSupervisor.Status.STOPPED);
        }
      } else if (!event.current().equals(statuses.get(key))) {
        statuses.put(key, event.current());
      }
    }
  }

  /**
   * The status key is the actor's framework-unique id: an actor (including a child engine
   * registered as an actor of its parent) is supervised by whichever supervisor recorded it, so the
   * recording engine must never take part in the key. Participates in: FR-106.
   */
  private static String statusKey(final Actor actor) {
    return String.valueOf(actor.getId());
  }

  /**
   * Identity of an actor across restarts, within one pane: engine or plain-actor kind plus name, so
   * a restarted instance takes over the slot of the instance it replaced. Participates in: FR-106.
   */
  private static String displayKey(final Actor actor) {
    return (actor instanceof Engine ? "engine:" : "actor:") + actor.getName();
  }

  /**
   * The supervision status of any tree node, engine or plain actor. Participates in: FR-107,
   * FR-109.
   */
  private SimulaSupervisor.Status actorStatus(final Actor actor) {
    return actor == null ? null : statuses.get(statusKey(actor));
  }

  /**
   * The engine whose boxes the pane shows for a selected tree node: an engine node shows its own
   * pane, a plain-actor node shows the pane of the engine it belongs to. Participates in: FR-109.
   */
  private static Engine paneEngineFor(final Actor selected) {
    if (selected instanceof Engine engine) {
      return engine;
    }
    return selected == null ? null : selected.getEngine();
  }

  /**
   * Periodic FX-tick refresh: drains the supervision ledger, then re-synchronizes the engine tree
   * and re-renders the selected engine's boxes. Participates in: FR-106, FR-107.
   */
  private void refreshEngines() {
    applyLedger();
    final Actor selected = selectedActor();
    final Engine root = SimulaTarget.currentRootEngine();
    if (root == null) {
      return;
    }
    final TreeItem<Actor> rootItem = itemFor(root);
    if (engineTree.getRoot() != rootItem) {
      engineTree.setRoot(rootItem);
    }
    syncChildren(rootItem, root);
    final TreeItem<Actor> target = selected == null ? rootItem : items.get(displayKey(selected));
    engineTree.getSelectionModel().select(target == null ? rootItem : target);
    renderActors(paneEngineFor(engineTree.getSelectionModel().getSelectedItem().getValue()));
  }

  /**
   * The actor or engine currently selected in the tree, or {@code null} while nothing is selected.
   * Participates in: FR-106, FR-109.
   */
  private Actor selectedActor() {
    final TreeItem<Actor> item = engineTree.getSelectionModel().getSelectedItem();
    return item == null ? null : item.getValue();
  }

  /**
   * Returns the persistent {@link TreeItem} of an engine or plain actor, keyed by display key (type
   * plus name) and created (expanded) on first use. Items are reused across refreshes so that
   * operator collapse/expand states survive the periodic synchronization, and so that a node
   * restarted under a new instance replaces its old item in place instead of appearing twice; the
   * item value is then swapped to the live instance. Participates in: FR-106, FR-107, FR-109.
   */
  private TreeItem<Actor> itemFor(final Actor actor) {
    final TreeItem<Actor> item =
        items.computeIfAbsent(
            displayKey(actor),
            key -> {
              final TreeItem<Actor> created = new TreeItem<>(actor);
              created.setExpanded(true);
              return created;
            });
    if (item.getValue() != actor) {
      item.setValue(actor);
    }
    return item;
  }

  /**
   * Adds missing child items and recurses, keeping existing items in place. A stopped engine is no
   * longer listed by its parent but its item stays in the tree, red, exactly like a stopped actor's
   * box. Participates in: FR-106, FR-107.
   */
  private void syncChildren(final TreeItem<Actor> parentItem, final Engine parent) {
    for (final Engine child : parent.getChildren()) {
      final TreeItem<Actor> childItem = itemFor(child);
      if (!parentItem.getChildren().contains(childItem)) {
        parentItem.getChildren().add(childItem);
      }
      syncChildren(childItem, child);
    }
    for (final Actor actor : parent.getActors()) {
      final TreeItem<Actor> actorItem = itemFor(actor);
      if (!parentItem.getChildren().contains(actorItem)) {
        parentItem.getChildren().add(actorItem);
      }
    }
  }

  /**
   * Renders the selected engine's box, then one box per registered actor and per child engine
   * (deduplicated by id, since {@code addChild} does not register children as actors), each colored
   * by its supervision status. The pane is rebuilt only when the shown set changes; status-only
   * changes toggle style classes in place so an open context menu is never destroyed. An actor
   * unregistered from the engine (typically after {@code Stop}) keeps its remembered slot and
   * position, turning red, for the whole session; an actor or engine restarted under a new instance
   * takes over its slot in place (matched by name and engine/actor kind) instead of being shown
   * twice; only never-seen actors append at the end. Participates in: FR-106, FR-107.
   */
  private void renderActors(final Engine engine) {
    final List<Actor> live = new ArrayList<>();
    final Set<Integer> liveIds = new HashSet<>();
    if (engine != null) {
      if (liveIds.add(engine.getId())) {
        live.add(engine);
      }
      for (final Actor actor : engine.getActors()) {
        if (liveIds.add(actor.getId())) {
          live.add(actor);
        }
      }
      for (final Engine child : engine.getChildren()) {
        if (liveIds.add(child.getId())) {
          live.add(child);
        }
      }
    }
    final Map<String, Actor> liveByKey = new HashMap<>();
    for (final Actor actor : live) {
      liveByKey.putIfAbsent(displayKey(actor), actor);
    }
    final List<Actor> shown = new ArrayList<>();
    final Set<Integer> shownIds = new HashSet<>();
    if (engine != null) {
      final List<Actor> previous = rememberedActors.get(engine.getName());
      if (previous != null) {
        for (final Actor actor : previous) {
          Actor current = actor;
          final Actor replacement = liveByKey.remove(displayKey(actor));
          if (replacement != null && !liveIds.contains(actor.getId())) {
            current = replacement;
          }
          if (shownIds.add(current.getId())) {
            shown.add(current);
          }
        }
      }
    }
    for (final Actor actor : live) {
      if (shownIds.add(actor.getId())) {
        shown.add(actor);
      }
    }
    if (engine != null) {
      rememberedActors.put(engine.getName(), new ArrayList<>(shown));
    }
    final ObservableList<Node> current = actorPane.getChildren();
    boolean unchanged = current.size() == shown.size();
    for (int i = 0; unchanged && i < shown.size(); i++) {
      unchanged = shown.get(i) == current.get(i).getUserData();
    }
    if (!unchanged) {
      current.clear();
      for (final Actor actor : shown) {
        current.add(actorBox(actor));
      }
    }
    for (final Node node : current) {
      applyStatusStyle((StackPane) node, (Actor) node.getUserData());
    }
  }

  /**
   * Toggles the {@code started}/{@code stopped} style of an existing box without replacing it, so
   * open context menus survive status changes. Participates in: FR-106.
   */
  private void applyStatusStyle(final StackPane box, final Actor actor) {
    final SimulaSupervisor.Status status = statuses.get(statusKey(actor));
    toggleStyle(box, "started", status == SimulaSupervisor.Status.STARTED);
    toggleStyle(box, "stopped", status == SimulaSupervisor.Status.STOPPED);
  }

  /** Adds or removes one style class on a box, only when actually absent or present. */
  private static void toggleStyle(final StackPane box, final String style, final boolean on) {
    if (on) {
      if (!box.getStyleClass().contains(style)) {
        box.getStyleClass().add(style);
      }
    } else {
      box.getStyleClass().remove(style);
    }
  }

  /**
   * Builds one status-colored box for an actor (engines get an extra dashed {@code engine-box}
   * style and a rocket icon, plain actors a gear icon), with a right-click {@code Stop} menu
   * requesting the cooperative stop of that actor or engine. Participates in: FR-106, FR-108.
   */
  private StackPane actorBox(final Actor actor) {
    final Label icon = new Label(actor instanceof Engine ? ENGINE_ICON : ACTOR_ICON);
    icon.getStyleClass().add(ACTOR_ICON_STYLE);
    final Label name = new Label(actor.getName());
    name.getStyleClass().add(ACTOR_NAME_STYLE);
    final HBox content = new HBox(ICON_SPACING, icon, name);
    content.setAlignment(Pos.CENTER_LEFT);
    final StackPane box = new StackPane(content);
    box.setUserData(actor);
    box.getStyleClass().add(ACTOR_BOX_STYLE);
    if (actor instanceof Engine) {
      box.getStyleClass().add("engine-box");
    }
    final MenuItem stop = new MenuItem(STOP_LABEL);
    stop.setOnAction(event -> StopRequest.send(actor));
    final ContextMenu menu = new ContextMenu(stop);
    box.setOnContextMenuRequested(event -> menu.show(box, event.getScreenX(), event.getScreenY()));
    return box;
  }

  /** Tree cell showing the type icon (rocket or gear), the node name and the status color. */
  private static final class ActorCell extends TreeCell<Actor> {

    private final Function<Actor, SimulaSupervisor.Status> statusLookup;
    private final Label icon = new Label();

    /**
     * Creates a cell reading node status through the given lookup.
     *
     * @param statusLookup maps an engine or actor to its last supervision status, possibly {@code
     *     null}
     */
    ActorCell(final Function<Actor, SimulaSupervisor.Status> statusLookup) {
      this.statusLookup = statusLookup;
      icon.getStyleClass().add(TREE_ICON_STYLE);
      setGraphic(icon);
    }

    /**
     * Labels the cell with the node name, prefixed by the rocket (engine) or gear (plain actor)
     * graphic icon, and applies the {@code engine-started} or {@code engine-stopped} text style
     * matching its supervision status. Participates in: FR-107, FR-109.
     */
    @Override
    protected void updateItem(final Actor item, final boolean empty) {
      super.updateItem(item, empty);
      final boolean filled = !empty && item != null;
      setText(filled ? item.getName() : null);
      icon.setText(!filled ? "" : item instanceof Engine ? ENGINE_ICON : ACTOR_ICON);
      getStyleClass().removeAll("engine-started", "engine-stopped");
      final SimulaSupervisor.Status status = filled ? statusLookup.apply(item) : null;
      if (status == SimulaSupervisor.Status.STARTED) {
        getStyleClass().add("engine-started");
      } else if (status == SimulaSupervisor.Status.STOPPED) {
        getStyleClass().add("engine-stopped");
      }
    }
  }
}
