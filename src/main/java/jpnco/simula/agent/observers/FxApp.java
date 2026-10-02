package jpnco.simula.agent.observers;

import java.io.IOException;
import java.io.UncheckedIOException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * A real in-process JavaFX application launched by {@link FxObserver} from the captured root
 * engine. It loads {@code main-view.fxml} (a {@link javafx.scene.control.SplitPane} with the engine
 * tree on the left and the selected engine's actor boxes on the right), driven by {@link
 * MainViewController}; closing the window exits the JavaFX platform.
 *
 * <p>Requires the JavaFX modules on the runtime module path ({@code --add-modules
 * javafx.controls,javafx.fxml}); JavaFX is a {@code provided} dependency so it is never bundled
 * into the agent. Excluded from the coverage gate: a graphical window cannot be meaningfully
 * unit-tested.
 *
 * <p>Implements: FR-005 (observer hook), observer-api contract.
 */
public final class FxApp extends Application {

  private static final String VIEW = "main-view.fxml";
  private static final String STYLESHEET = "main-view.css";
  private static final double WIDTH = 900;
  private static final double HEIGHT = 600;

  /**
   * Loads the FXML view, attaches the stylesheet and shows it in the primary stage.
   *
   * @param stage the primary stage provided by the JavaFX launcher
   */
  @Override
  public void start(final Stage stage) {
    try {
      final Parent root = new FXMLLoader(FxApp.class.getResource(VIEW)).load();
      root.getStylesheets()
          .add(
              java.util.Objects.requireNonNull(
                      FxApp.class.getResource(STYLESHEET), "missing stylesheet " + STYLESHEET)
                  .toExternalForm());
      stage.setScene(new Scene(root, WIDTH, HEIGHT));
    } catch (final IOException exc) {
      throw new UncheckedIOException("unable to load " + VIEW, exc);
    }
    stage.setTitle("simula-agent");
    stage.setOnCloseRequest(event -> Platform.exit());
    stage.show();
  }
}
