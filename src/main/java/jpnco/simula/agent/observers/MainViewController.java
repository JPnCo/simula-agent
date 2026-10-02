package jpnco.simula.agent.observers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.agent.api.SimulaTarget;

/**
 * Controller for {@code main-view.fxml}: a {@link SplitPane} whose left {@link TreeView} shows the
 * engine tree (root plus children, refreshed live from {@link SimulaTarget}) and whose right {@link
 * FlowPane} renders one box per actor of the engine selected in the tree, labelled with the actor's
 * name.
 *
 * <p>Part of the JavaFX demo observer; excluded from the coverage gate (requires the FX toolkit and
 * a display). Implements: FR-005, observer-api contract.
 */
public final class MainViewController {

  private static final long REFRESH_MILLIS = 500L;
  private static final String ACTOR_BOX_STYLE = "actor-box";
  private static final String ACTOR_NAME_STYLE = "actor-name";

  @FXML private TreeView<Engine> engineTree;
  @FXML private FlowPane actorPane;

  private final Map<Engine, TreeItem<Engine>> items = new IdentityHashMap<>();

  /** Wires the tree, its selection and the periodic refresh once the FXML is injected. */
  @FXML
  void initialize() {
    engineTree.setCellFactory(view -> new EngineCell());
    engineTree
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observed, oldValue, newValue) ->
                renderActors(newValue == null ? null : newValue.getValue()));
    final Timeline refresh =
        new Timeline(new KeyFrame(Duration.millis(REFRESH_MILLIS), event -> refreshEngines()));
    refresh.setCycleCount(Timeline.INDEFINITE);
    refresh.play();
    refreshEngines();
  }

  private void refreshEngines() {
    final Engine selected = selectedEngine();
    final Engine root = SimulaTarget.currentRootEngine();
    if (root == null) {
      return;
    }
    final TreeItem<Engine> rootItem = itemFor(root);
    if (engineTree.getRoot() != rootItem) {
      engineTree.setRoot(rootItem);
    }
    syncChildren(rootItem, root);
    final TreeItem<Engine> target = selected == null ? rootItem : items.get(selected);
    engineTree.getSelectionModel().select(target == null ? rootItem : target);
    renderActors(engineTree.getSelectionModel().getSelectedItem().getValue());
  }

  private Engine selectedEngine() {
    final TreeItem<Engine> item = engineTree.getSelectionModel().getSelectedItem();
    return item == null ? null : item.getValue();
  }

  /**
   * Returns the persistent {@link TreeItem} of an engine, creating it (expanded) on first use.
   * Items are reused across refreshes so that operator collapse/expand states survive the periodic
   * synchronization.
   */
  private TreeItem<Engine> itemFor(final Engine engine) {
    return items.computeIfAbsent(
        engine,
        key -> {
          final TreeItem<Engine> item = new TreeItem<>(key);
          item.setExpanded(true);
          return item;
        });
  }

  /**
   * Adds missing child items, removes stale ones, and recurses, keeping existing items in place.
   */
  private void syncChildren(final TreeItem<Engine> parentItem, final Engine parent) {
    final List<Engine> current = new ArrayList<>(parent.getChildren());
    final Set<Engine> currentSet = Collections.newSetFromMap(new IdentityHashMap<>());
    currentSet.addAll(current);
    parentItem
        .getChildren()
        .removeIf(
            child -> {
              if (currentSet.contains(child.getValue())) {
                return false;
              }
              prune(child);
              return true;
            });
    for (final Engine child : current) {
      final TreeItem<Engine> childItem = itemFor(child);
      if (!parentItem.getChildren().contains(childItem)) {
        parentItem.getChildren().add(childItem);
      }
      syncChildren(childItem, child);
    }
  }

  /** Drops the items of a subtree being detached from the tree from the item cache. */
  private void prune(final TreeItem<Engine> item) {
    for (final TreeItem<Engine> child : item.getChildren()) {
      prune(child);
    }
    items.remove(item.getValue());
  }

  private void renderActors(final Engine engine) {
    actorPane.getChildren().clear();
    if (engine == null) {
      return;
    }
    final List<Actor> actors = engine.getActors();
    for (final Actor actor : actors) {
      actorPane.getChildren().add(actorBox(actor));
    }
  }

  private static StackPane actorBox(final Actor actor) {
    final Label name = new Label(actor.getName());
    name.getStyleClass().add(ACTOR_NAME_STYLE);
    final StackPane box = new StackPane(name);
    box.getStyleClass().add(ACTOR_BOX_STYLE);
    return box;
  }

  /** Tree cell that labels each engine by its name. */
  private static final class EngineCell extends TreeCell<Engine> {
    @Override
    protected void updateItem(final Engine item, final boolean empty) {
      super.updateItem(item, empty);
      setText(empty || item == null ? null : item.getName());
    }
  }
}
