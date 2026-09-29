package market.fx.screens;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import market.dto.UserDetailsDto;
import market.fx.AppState;
import market.fx.util.Format;

/**
 * The frame of the main window: who is logged in, what their account holds, and
 * whether the server can be reached, above the three tabs everything else lives in.
 */
public final class MainController {

    @FXML private Label titleLabel;
    @FXML private Label userLabel;
    @FXML private Label balanceLabel;
    @FXML private Label connectionLabel;

    /** Filled in by the loader from the fx:id of each included screen, plus "Controller". */
    @FXML private EventsController eventsViewController;
    @FXML private AccountController accountViewController;
    @FXML private ChatController chatViewController;

    private Runnable onLogout;

    @FXML
    private void initialize() {
        titleLabel.setFont(Font.font(titleLabel.getFont().getFamily(), FontWeight.BOLD, 16.0));
    }

    public void setUp(Stage stage, AppState state, Runnable onLogout) {
        this.onLogout = onLogout;
        userLabel.setText("Logged in as " + state.userName());
        state.me().listen(this::showAccount);
        state.reachable().listen(reachable -> {
            connectionLabel.setText(reachable ? "" : "The server cannot be reached - retrying...");
            connectionLabel.setTextFill(Color.web("#a4302a"));
        });

        eventsViewController.setUp(state);
        accountViewController.setUp(stage, state);
        chatViewController.setUp(state);
    }

    private void showAccount(UserDetailsDto me) {
        balanceLabel.setText("Balance " + Format.money(me.balance())
                + (me.blocked() ? "  -  blocked until a deposit covers the debt" : ""));
        balanceLabel.setTextFill(me.blocked() ? Color.web("#a4302a") : Color.BLACK);
    }

    @FXML
    private void onLogout() {
        onLogout.run();
    }
}
