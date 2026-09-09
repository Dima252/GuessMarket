package market.fx.screens;

import java.io.File;
import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import market.engine.dto.LoadReportDto;
import market.fx.AppState;
import market.fx.tasks.LoadFileTask;

/**
 * The frame of the window: the bar that loads a file, and the two areas the rest
 * of the application lives in.
 */
public final class MainController {

    private final AppState state = new AppState();

    @FXML private Button loadFileButton;
    @FXML private Label loadedFileLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressBar loadProgress;
    @FXML private TabPane tabs;

    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void initialize() {
        tabs.setDisable(true);
    }

    /**
     * Chooses a file and reads it in the background, so that the window stays
     * alive while it happens.
     */
    @FXML
    private void onLoadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an events file");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));

        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) {
            return;
        }
        startLoading(chosen);
    }

    private void startLoading(File chosen) {
        LoadFileTask task = new LoadFileTask(state.engine(), chosen.getAbsolutePath());

        // The task updates these from its own thread; JavaFX moves them here for us.
        statusLabel.textProperty().bind(task.messageProperty());
        loadProgress.progressProperty().bind(task.progressProperty());
        loadProgress.setVisible(true);
        loadFileButton.setDisable(true);

        task.setOnSucceeded(event -> finish(task.getValue(), chosen));
        task.setOnFailed(event -> {
            release();
            statusLabel.setText("The file could not be read.");
            showProblems(chosen, List.of(String.valueOf(task.getException())));
        });

        Thread worker = new Thread(task, "load-file");
        worker.setDaemon(true);
        worker.start();
    }

    private void finish(LoadReportDto report, File chosen) {
        release();
        if (report.success()) {
            loadedFileLabel.setText(chosen.getAbsolutePath());
            statusLabel.setText(report.eventsLoaded() + " events and " + report.usersLoaded()
                    + " users are loaded.");
            tabs.setDisable(false);
        } else {
            // A faulty file changes nothing: whatever was loaded before is still there.
            statusLabel.setText("\"" + chosen.getName() + "\" was refused. Nothing has changed.");
            showProblems(chosen, report.errors());
        }
    }

    private void release() {
        statusLabel.textProperty().unbind();
        loadProgress.progressProperty().unbind();
        loadProgress.setVisible(false);
        loadFileButton.setDisable(false);
    }

    /**
     * Shows every problem at once, in a box that scrolls: a file can easily have
     * more of them than a plain dialog would show.
     */
    private void showProblems(File chosen, List<String> problems) {
        TextArea details = new TextArea(String.join(System.lineSeparator(), problems));
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(Math.min(12, Math.max(3, problems.size() + 1)));

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle("The file was not loaded");
        alert.setHeaderText(problems.size() == 1
                ? "One problem was found in \"" + chosen.getName() + "\":"
                : problems.size() + " problems were found in \"" + chosen.getName() + "\":");
        alert.getDialogPane().setContent(details);
        alert.getDialogPane().setPrefWidth(720);
        alert.setResizable(true);
        alert.showAndWait();
    }
}
