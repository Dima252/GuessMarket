package market.fx.screens;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import market.dto.ChatLineDto;
import market.fx.AppState;

/**
 * The chat (bonus): every line anybody wrote, pulled from the server with the
 * rest of the system, and a field to add one. Clients never talk to each other -
 * a message goes to the server, and the others pick it up on their next pull.
 */
public final class ChatController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @FXML private ListView<ChatLineDto> linesList;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    @FXML private Label statusLabel;

    private AppState state;

    @FXML
    private void initialize() {
        linesList.setPlaceholder(new Label("Nobody has written anything yet."));
        linesList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ChatLineDto line, boolean empty) {
                super.updateItem(line, empty);
                setText(empty || line == null ? null
                        : TIME.format(Instant.ofEpochMilli(line.timeMillis())) + "   " + line.userName()
                        + ":   " + line.text());
                setWrapText(true);
            }
        });
    }

    public void setUp(AppState state) {
        this.state = state;
        state.chat().listen(this::showLines);
    }

    private void showLines(List<ChatLineDto> lines) {
        boolean grew = lines.size() > linesList.getItems().size();
        linesList.getItems().setAll(lines);
        if (grew) {
            linesList.scrollTo(lines.size() - 1);
        }
    }

    @FXML
    private void onSend() {
        String text = messageField.getText() == null ? "" : messageField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        sendButton.setDisable(true);
        state.api().say(text, line -> {
            sendButton.setDisable(false);
            messageField.clear();
            statusLabel.setText("");
            state.poll();
        }, problem -> {
            sendButton.setDisable(false);
            statusLabel.setText(problem);
            statusLabel.setTextFill(Color.web("#a4302a"));
        });
    }
}
