package market.fx;

import java.net.URL;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import market.fx.screens.MainController;

/**
 * The JavaFX application: it builds the single window of Guess Market from
 * {@code main-view.fxml} and hands the controller the state of the system.
 */
public final class GuessMarketApp extends Application {

    private static final String MAIN_VIEW = "/market/fx/screens/main-view.fxml";
    private static final double INITIAL_WIDTH = 1180;
    private static final double INITIAL_HEIGHT = 720;
    /** Small enough to prove the layout survives being squeezed, large enough to stay usable. */
    private static final double MINIMUM_WIDTH = 640;
    private static final double MINIMUM_HEIGHT = 420;

    @Override
    public void start(Stage stage) throws Exception {
        URL view = GuessMarketApp.class.getResource(MAIN_VIEW);
        if (view == null) {
            throw new IllegalStateException("The screen " + MAIN_VIEW + " is missing from the application.");
        }
        FXMLLoader loader = new FXMLLoader(view);
        BorderPane root = loader.load();

        MainController controller = loader.getController();
        controller.setStage(stage);

        stage.setTitle("Guess Market");
        stage.setScene(new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT));
        stage.setMinWidth(MINIMUM_WIDTH);
        stage.setMinHeight(MINIMUM_HEIGHT);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
