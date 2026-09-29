package market.fx.screens;

import java.util.function.Consumer;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import market.dto.UserDetailsDto;
import market.fx.net.ServerApi;

/**
 * Logs in by name. A refusal - the name is taken, or the server is not there -
 * is shown under the field, and the user can simply try again.
 */
public final class LoginController {

    @FXML private Label title;
    @FXML private TextField nameField;
    @FXML private Button loginButton;
    @FXML private Label messageLabel;

    private ServerApi api;
    private Consumer<UserDetailsDto> onLoggedIn;

    @FXML
    private void initialize() {
        title.setFont(Font.font(title.getFont().getFamily(), FontWeight.BOLD, 22.0));
    }

    public void setUp(ServerApi api, Consumer<UserDetailsDto> onLoggedIn, String message) {
        this.api = api;
        this.onLoggedIn = onLoggedIn;
        show(message, false);
    }

    @FXML
    private void onLogin() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            show("Enter a name first.", true);
            return;
        }
        loginButton.setDisable(true);
        show("Logging in...", false);
        api.login(name, details -> {
            loginButton.setDisable(false);
            onLoggedIn.accept(details);
        }, problem -> {
            loginButton.setDisable(false);
            show(problem, true);
            nameField.requestFocus();
            nameField.selectAll();
        });
    }

    private void show(String message, boolean isProblem) {
        messageLabel.setText(message == null ? "" : message);
        messageLabel.setTextFill(isProblem ? Color.web("#a4302a") : Color.BLACK);
    }
}
