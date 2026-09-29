package market.fx.screens;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import market.dto.AccountEntryDto;
import market.dto.EventStateDto;
import market.dto.LoadReportDto;
import market.dto.ParticipantDto;
import market.dto.UserDetailsDto;
import market.dto.UserEventDto;
import market.dto.UserSummaryDto;
import market.fx.AppState;
import market.fx.components.EventDetailPane;
import market.fx.components.Tables;
import market.fx.components.TradeForm;
import market.fx.components.UserInvolvementPane;
import market.fx.tasks.UploadTask;
import market.fx.util.Format;

/**
 * The account area: the logged in user's own world.
 * <p>
 * Along the top, uploading a file of events. On the left, everybody in the system
 * - only their name, balance, and whether they carry events, which is all anyone
 * may see of somebody else - and the user's own account: its balance, loading
 * funds into it, and every movement it has seen. On the right, the events the
 * user takes part in or carries, and the chosen one in full, with what the user
 * can do in it.
 */
public final class AccountController {

    /** The key under which this screen tells the state which event it shows. */
    private static final String SCREEN = "account";
    private static final Color GOOD = Color.web("#1f6b3a");
    private static final Color BAD = Color.web("#a4302a");

    @FXML private Button loadFileButton;
    @FXML private Label filePathLabel;
    @FXML private ProgressIndicator uploadProgress;
    @FXML private Label uploadStatus;
    @FXML private Label usersHeading;
    @FXML private TableView<UserSummaryDto> usersTable;
    @FXML private Label accountHeading;
    @FXML private Label balanceLabel;
    @FXML private TextField depositField;
    @FXML private Button depositButton;
    @FXML private Label depositStatus;
    @FXML private TableView<AccountEntryDto> ledgerTable;
    @FXML private Label eventsHeading;
    @FXML private TableView<UserEventDto> myEventsTable;
    @FXML private VBox detailBox;

    private final ObservableList<UserSummaryDto> users = FXCollections.observableArrayList();
    private final ObservableList<AccountEntryDto> ledger = FXCollections.observableArrayList();
    private final ObservableList<UserEventDto> myEvents = FXCollections.observableArrayList();
    private final EventDetailPane eventDetail = new EventDetailPane();
    private final UserInvolvementPane involvement = new UserInvolvementPane();

    private Stage stage;
    private AppState state;
    private TradeForm tradeForm;
    private Integer chosenEventId;

    @FXML
    private void initialize() {
        for (Label heading : List.of(usersHeading, accountHeading, eventsHeading)) {
            heading.setFont(Font.font(heading.getFont().getFamily(), FontWeight.BOLD, 13.0));
        }

        // The details come last, so that they are the column that takes whatever
        // width is left: they are the part of a line worth reading in full.
        Tables.columns(ledgerTable,
                width(Tables.column("#", entry -> Integer.toString(entry.serial())), 36.0),
                width(Tables.column("What", AccountEntryDto::kind), 120.0),
                width(Tables.column("Amount", entry -> signed(entry.amount())), 76.0),
                width(Tables.column("Balance", entry -> Format.money(entry.balanceAfter())), 76.0),
                width(Tables.column("Details", AccountEntryDto::description), 320.0));
        ledgerTable.setItems(ledger);
        ledgerTable.setPlaceholder(new Label("Nothing yet. Load funds to start."));

        Tables.columns(myEventsTable,
                Tables.indexColumn(),
                Tables.column("Event", UserEventDto::eventName),
                Tables.column("Method", UserEventDto::methodType),
                Tables.column("Status", UserEventDto::phase),
                Tables.column("Role", event -> event.marketMaker() ? "Market maker" : "Taking part"),
                Tables.column("Shares held", AccountController::holdings),
                Tables.column("Commission", event -> event.position() == null
                        ? Format.NOTHING : Format.money(event.position().commissionPaid())),
                Tables.column("Result", event -> event.position() == null
                        ? Format.NOTHING : Format.money(event.position().profitAndLoss())));
        myEventsTable.setItems(myEvents);
        myEventsTable.setPlaceholder(new Label("None yet: upload a file, or trade on the Events tab."));
        myEventsTable.getSelectionModel().selectedItemProperty()
                .addListener((property, was, now) -> {
                    if (now != null) {
                        choose(now.eventId());
                    }
                });
    }

    public void setUp(Stage stage, AppState state) {
        this.stage = stage;
        this.state = state;
        this.tradeForm = new TradeForm(state);

        Tables.columns(usersTable,
                Tables.indexColumn(),
                Tables.column("User", user -> user.name()
                        + (user.name().equalsIgnoreCase(state.userName()) ? "  (you)" : "")),
                Tables.column("Balance", user -> Format.money(user.balance())),
                Tables.column("Market maker", user -> user.marketMaker() ? "yes" : ""));
        usersTable.setItems(users);
        usersTable.setPlaceholder(new Label("Nobody yet."));

        detailBox.getChildren().addAll(eventDetail, involvement, tradeForm);
        eventDetail.showNothing("Choose one of your events above to see it and to act in it.");

        state.users().listen(users::setAll);
        state.ledger().listen(ledger::setAll);
        state.me().listen(this::showMe);
        state.eventFeed(SCREEN).listen(this::showEvent);
    }

    // ------------------------------------------------------------- the account

    private void showMe(UserDetailsDto me) {
        balanceLabel.setText("Balance " + Format.money(me.balance())
                + (me.blocked() ? "  -  blocked until a deposit covers the debt" : ""));
        balanceLabel.setTextFill(me.blocked() ? BAD : Color.BLACK);

        Integer keep = chosenEventId;
        myEvents.setAll(me.events());
        reselect(keep);

        // What the user holds in the chosen event is part of these details.
        EventStateDto shown = state.eventFeed(SCREEN).value();
        if (shown != null) {
            involvement.show(involvementIn(shown.summary().id()), shown);
            tradeForm.show(shown, me.blocked());
        }
    }

    @FXML
    private void onDeposit() {
        String amount = depositField.getText() == null ? "" : depositField.getText().trim();
        if (amount.isEmpty()) {
            report(depositStatus, "Enter the amount to load.", false);
            return;
        }
        try {
            Double.parseDouble(amount);
        } catch (NumberFormatException e) {
            report(depositStatus, "\"" + amount + "\" is not an amount of money. An amount looks like 100 or 25.50.",
                    false);
            return;
        }
        depositButton.setDisable(true);
        state.api().deposit(amount, summary -> {
            depositButton.setDisable(false);
            depositField.clear();
            report(depositStatus, "Loaded " + Format.money(Double.parseDouble(amount))
                    + ". Your balance is now " + Format.money(summary.balance()) + ".", true);
            state.poll();
        }, problem -> {
            depositButton.setDisable(false);
            report(depositStatus, problem, false);
        });
    }

    // -------------------------------------------------------------- the events

    private void reselect(Integer eventId) {
        if (eventId != null) {
            for (UserEventDto event : myEvents) {
                if (event.eventId() == eventId.intValue()) {
                    myEventsTable.getSelectionModel().select(event);
                    return;
                }
            }
        }
        if (myEventsTable.getSelectionModel().getSelectedItem() == null) {
            choose(null);
            eventDetail.showNothing("Choose one of your events above to see it and to act in it.");
            involvement.showNothing();
            tradeForm.showNothing();
        }
    }

    private void choose(Integer eventId) {
        chosenEventId = eventId;
        state.watch(SCREEN, eventId);
    }

    private void showEvent(EventStateDto event) {
        if (event == null || chosenEventId == null || event.summary().id() != chosenEventId) {
            return;
        }
        eventDetail.show(event);
        involvement.show(involvementIn(event.summary().id()), event);
        UserDetailsDto me = state.me().value();
        tradeForm.show(event, me != null && me.blocked());
    }

    private UserEventDto involvementIn(int eventId) {
        for (UserEventDto event : myEvents) {
            if (event.eventId() == eventId) {
                return event;
            }
        }
        return null;
    }

    private static String holdings(UserEventDto event) {
        ParticipantDto position = event.position();
        if (position == null) {
            return Format.NOTHING;
        }
        List<String> pieces = new ArrayList<>();
        for (long shares : position.sharesPerOption()) {
            pieces.add(Long.toString(shares));
        }
        return String.join(" / ", pieces);
    }

    // ------------------------------------------------------------------ upload

    /**
     * Chooses a file on this computer and sends it to the server, in the
     * background so that the window stays alive while it travels.
     */
    @FXML
    private void onLoadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an events file to upload");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));
        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) {
            return;
        }
        if (!chosen.isFile() || !chosen.canRead()) {
            report(uploadStatus, "\"" + chosen.getAbsolutePath() + "\" cannot be read.", false);
            return;
        }
        if (!chosen.getName().toLowerCase(Locale.US).endsWith(".xml")) {
            report(uploadStatus, "The file must be an XML file, but \"" + chosen.getName()
                    + "\" does not end with .xml.", false);
            return;
        }
        upload(chosen);
    }

    private void upload(File chosen) {
        UploadTask task = new UploadTask(state.api(), chosen);
        filePathLabel.setText(chosen.getAbsolutePath());
        uploadStatus.setTextFill(Color.BLACK);
        uploadStatus.textProperty().bind(task.messageProperty());
        uploadProgress.setVisible(true);
        uploadProgress.setManaged(true);
        loadFileButton.setDisable(true);

        task.setOnSucceeded(event -> {
            release();
            LoadReportDto report = task.getValue();
            if (report.success()) {
                report(uploadStatus, "\"" + chosen.getName() + "\" added " + report.eventsLoaded()
                        + " events, and you are their market maker: " + String.join(", ", report.eventNames()) + ".",
                        true);
                state.poll();
            } else {
                // A faulty file adds nothing: the system is exactly as it was.
                report(uploadStatus, "\"" + chosen.getName() + "\" was refused. Nothing was added.", false);
                showProblems(chosen, report.errors());
            }
        });
        task.setOnFailed(event -> {
            release();
            String problem = task.getException() == null ? "unknown" : task.getException().getMessage();
            report(uploadStatus, "\"" + chosen.getName() + "\" could not be uploaded: " + problem, false);
        });

        Thread worker = new Thread(task, "upload-file");
        worker.setDaemon(true);
        worker.start();
    }

    private void release() {
        uploadStatus.textProperty().unbind();
        uploadProgress.setVisible(false);
        uploadProgress.setManaged(false);
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
        alert.setTitle("The file was not added");
        alert.setHeaderText(problems.size() == 1
                ? "One problem was found in \"" + chosen.getName() + "\":"
                : problems.size() + " problems were found in \"" + chosen.getName() + "\":");
        alert.getDialogPane().setContent(details);
        alert.getDialogPane().setPrefWidth(720);
        alert.setResizable(true);
        alert.show();
    }

    // -------------------------------------------------------------------- bits

    private static <T> TableColumn<T, String> width(TableColumn<T, String> column, double width) {
        column.setPrefWidth(width);
        return column;
    }

    private static String signed(double amount) {
        return (amount > 0 ? "+" : "") + Format.money(amount);
    }

    private static void report(Label label, String message, boolean succeeded) {
        label.setText(message);
        label.setTextFill(succeeded ? GOOD : BAD);
    }
}
