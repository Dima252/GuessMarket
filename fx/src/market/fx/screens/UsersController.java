package market.fx.screens;

import java.util.ArrayList;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import market.engine.api.EngineException;
import market.engine.dto.EventSummaryDto;
import market.engine.dto.ParticipantDto;
import market.engine.dto.UserDetailsDto;
import market.engine.dto.UserEventDto;
import market.engine.dto.UserSummaryDto;
import market.fx.AppState;
import market.fx.components.EventDetailPane;
import market.fx.components.TradeForm;
import market.fx.components.UserInvolvementPane;
import market.fx.components.Tables;
import market.fx.util.Format;

/**
 * The users area: everybody in the system, what the chosen one holds, and the
 * things that user can do.
 * <p>
 * This is where taking part happens, as the specification puts it: a user is
 * chosen, then an event, and the controls appear underneath the details of that
 * event - the same details the events tab shows on its own.
 */
public final class UsersController {

    @FXML private TableView<UserSummaryDto> usersTable;
    @FXML private TableView<EventRow> userEventsTable;
    @FXML private Label userHeading;
    @FXML private Label userBalance;
    @FXML private VBox userDetail;

    private final ObservableList<UserSummaryDto> users = FXCollections.observableArrayList();
    private final ObservableList<EventRow> userEvents = FXCollections.observableArrayList();
    private final EventDetailPane eventDetail = new EventDetailPane();
    private final UserInvolvementPane involvement = new UserInvolvementPane();
    private TradeForm tradeForm;

    private AppState state;

    /** One line of the events table: an event, and what this user is to it. */
    public record EventRow(EventSummaryDto event, UserEventDto involvement) {

        String role() {
            if (involvement == null) {
                return "—";
            }
            if (involvement.marketMaker()) {
                return "Market maker";
            }
            return "Taking part";
        }

        String holdings() {
            ParticipantDto position = involvement == null ? null : involvement.position();
            if (position == null) {
                return "—";
            }
            List<String> pieces = new ArrayList<>();
            for (long shares : position.sharesPerOption()) {
                pieces.add(Long.toString(shares));
            }
            return String.join(" / ", pieces);
        }

        String commission() {
            ParticipantDto position = involvement == null ? null : involvement.position();
            return position == null ? "—" : Format.money(position.commissionPaid());
        }

        String result() {
            ParticipantDto position = involvement == null ? null : involvement.position();
            return position == null ? "—" : Format.money(position.profitAndLoss());
        }
    }

    @FXML
    private void initialize() {
        Tables.columns(usersTable,
                Tables.indexColumn(),
                Tables.column("User", UserSummaryDto::name),
                Tables.column("Balance", user -> Format.money(user.balance())),
                Tables.column("Market maker", user -> user.marketMaker() ? "yes" : ""),
                Tables.column("Blocked", user -> user.blocked() ? "blocked" : ""));
        usersTable.setItems(users);
        usersTable.setPlaceholder(new Label("No users are loaded."));

        Tables.columns(userEventsTable,
                Tables.indexColumn(),
                Tables.column("Event", row -> row.event().name()),
                Tables.column("Method", row -> row.event().methodType()),
                Tables.column("Status", row -> row.event().phase()),
                Tables.column("Role", EventRow::role),
                Tables.column("Shares held", EventRow::holdings),
                Tables.column("Commission", EventRow::commission),
                Tables.column("Result", EventRow::result));
        userEventsTable.setItems(userEvents);
        userEventsTable.setPlaceholder(new Label("No events are loaded."));

        usersTable.getSelectionModel().selectedItemProperty()
                .addListener((property, was, now) -> onUserChosen(now));
        userEventsTable.getSelectionModel().selectedItemProperty()
                .addListener((property, was, now) -> onEventChosen(now));
    }

    public void setState(AppState state) {
        this.state = state;
        this.tradeForm = new TradeForm(state);
        // The event as everybody sees it, then this user's own part in it, then
        // what they can do about it.
        userDetail.getChildren().addAll(eventDetail, involvement, tradeForm);
        eventDetail.showNothing("Choose one of the events above to see it and to act in it.");
        state.onRefresh(this::refresh);
    }

    /** Re-reads everything, keeping whoever and whatever was chosen. */
    public void refresh() {
        if (state == null || !state.isFileLoaded()) {
            users.clear();
            userEvents.clear();
            userHeading.setText("Load a file to see the users.");
            userBalance.setText("");
            eventDetail.showNothing("");
            involvement.showNothing();
            tradeForm.showNothing();
            return;
        }
        String chosenUser = state.selectedUserName();
        try {
            users.setAll(state.engine().listUsers());
        } catch (EngineException e) {
            users.clear();
        }
        reselectUser(chosenUser);
    }

    private void reselectUser(String userName) {
        if (userName != null) {
            for (UserSummaryDto user : users) {
                if (user.name().equals(userName)) {
                    usersTable.getSelectionModel().select(user);
                    showUser(userName);
                    return;
                }
            }
        }
        userHeading.setText("Choose somebody to see what they are doing.");
        userBalance.setText("");
        userEvents.clear();
        eventDetail.showNothing("");
        involvement.showNothing();
        tradeForm.showNothing();
    }

    private void onUserChosen(UserSummaryDto user) {
        if (user == null) {
            return;
        }
        state.selectUser(user.name());
        showUser(user.name());
    }

    private void showUser(String userName) {
        UserDetailsDto details;
        try {
            details = state.engine().userDetails(userName);
        } catch (EngineException e) {
            userHeading.setText(e.getMessage());
            return;
        }
        userHeading.setText(details.name());
        userBalance.setText("Balance " + Format.money(details.balance())
                + (details.blocked() ? "    —    blocked, and can do nothing more" : ""));
        userBalance.setTextFill(details.blocked() ? Color.web("#a4302a") : Color.BLACK);

        fillEvents(details);
        reselectEvent(state.selectedEventId(), details.blocked());
    }

    /**
     * Every event, with what this user is to it. All of them, not only the ones
     * already joined: taking part in a new one has to start somewhere.
     */
    private void fillEvents(UserDetailsDto details) {
        List<EventRow> rows = new ArrayList<>();
        try {
            for (EventSummaryDto event : state.engine().listEvents()) {
                UserEventDto involvement = null;
                for (UserEventDto candidate : details.events()) {
                    if (candidate.eventId() == event.id()) {
                        involvement = candidate;
                    }
                }
                rows.add(new EventRow(event, involvement));
            }
        } catch (EngineException e) {
            rows.clear();
        }
        userEvents.setAll(rows);
    }

    private void reselectEvent(Integer eventId, boolean blocked) {
        if (eventId != null) {
            for (EventRow row : userEvents) {
                if (row.event().id() == eventId.intValue()) {
                    userEventsTable.getSelectionModel().select(row);
                    showEvent(row.event().id(), blocked);
                    return;
                }
            }
        }
        eventDetail.showNothing("Choose one of the events above to see it and to act in it.");
        involvement.showNothing();
        tradeForm.showNothing();
    }

    private void onEventChosen(EventRow row) {
        if (row == null) {
            return;
        }
        state.selectEvent(row.event().id());
        showEvent(row.event().id(), isChosenUserBlocked());
    }

    private void showEvent(int eventId, boolean blocked) {
        String userName = state.selectedUserName();
        try {
            var event = state.engine().eventState(eventId);
            eventDetail.show(event);
            involvement.show(userName, involvementIn(eventId), event);
            tradeForm.show(userName, event, blocked);
        } catch (EngineException e) {
            eventDetail.showNothing(e.getMessage());
            involvement.showNothing();
            tradeForm.showNothing();
        }
    }

    /** What the chosen user is to one event, as the events table already worked out. */
    private UserEventDto involvementIn(int eventId) {
        for (EventRow row : userEvents) {
            if (row.event().id() == eventId) {
                return row.involvement();
            }
        }
        return null;
    }

    private boolean isChosenUserBlocked() {
        for (UserSummaryDto user : users) {
            if (user.name().equals(state.selectedUserName())) {
                return user.blocked();
            }
        }
        return false;
    }
}
