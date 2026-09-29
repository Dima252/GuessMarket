package market.fx;

import java.io.IOException;
import java.net.URL;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import market.dto.UserDetailsDto;
import market.fx.net.ServerApi;
import market.fx.screens.LoginController;
import market.fx.screens.MainController;

/**
 * The JavaFX client: one window, which shows the login screen until somebody
 * logs in, and the main screen from then on. Logging out, or the server
 * forgetting the session, brings the login screen back.
 */
public final class GuessMarketApp extends Application {

    private static final String LOGIN_VIEW = "/market/fx/screens/login-view.fxml";
    private static final String MAIN_VIEW = "/market/fx/screens/main-view.fxml";
    private static final double INITIAL_WIDTH = 1180;
    private static final double INITIAL_HEIGHT = 740;
    /** Small enough to prove the layout survives being squeezed, large enough to stay usable. */
    private static final double MINIMUM_WIDTH = 640;
    private static final double MINIMUM_HEIGHT = 420;

    private final ServerApi api = new ServerApi();
    private Stage stage;
    private Scene scene;
    private AppState state;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        api.onSessionLost(() -> {
            if (state != null) {
                showLogin("The server no longer knows this session. Log in again.");
            }
        });

        scene = new Scene(new javafx.scene.layout.Pane(), INITIAL_WIDTH, INITIAL_HEIGHT);
        stage.setScene(scene);
        stage.setMinWidth(MINIMUM_WIDTH);
        stage.setMinHeight(MINIMUM_HEIGHT);
        showLogin("");
        stage.show();
    }

    private void showLogin(String message) {
        endSession();
        FXMLLoader loader = loader(LOGIN_VIEW);
        scene.setRoot(load(loader));
        LoginController controller = loader.getController();
        controller.setUp(api, this::showMain, message);
        stage.setTitle("Guess Market");
    }

    private void showMain(UserDetailsDto user) {
        state = new AppState(api, user.name());
        FXMLLoader loader = loader(MAIN_VIEW);
        scene.setRoot(load(loader));
        MainController controller = loader.getController();
        controller.setUp(stage, state, () -> {
            // Polling stops first, so that nothing asks the server anything once the session is gone.
            endSession();
            api.logout(() -> showLogin("You have logged out."));
        });
        stage.setTitle("Guess Market - " + user.name());
        state.start();
    }

    private void endSession() {
        if (state != null) {
            state.stop();
            state = null;
        }
    }

    /** Closing the window logs out, which frees the name for the next time. */
    @Override
    public void stop() {
        boolean loggedIn = state != null;
        endSession();
        if (loggedIn) {
            api.logoutAndWait();
        }
        api.shutdown();
    }

    private static FXMLLoader loader(String view) {
        URL location = GuessMarketApp.class.getResource(view);
        if (location == null) {
            throw new IllegalStateException("The screen " + view + " is missing from the application.");
        }
        return new FXMLLoader(location);
    }

    private static Parent load(FXMLLoader loader) {
        try {
            return loader.load();
        } catch (IOException e) {
            throw new IllegalStateException("A screen could not be built: " + e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
