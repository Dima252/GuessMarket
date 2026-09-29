package market.fx.screens;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import market.dto.EventStateDto;
import market.dto.EventSummaryDto;
import market.dto.UserDetailsDto;
import market.fx.AppState;
import market.fx.components.EventDetailPane;
import market.fx.components.Tables;
import market.fx.components.TradeForm;
import market.fx.util.Format;

/**
 * The events area: every event in the system, whoever uploaded it, filtered three
 * ways; and the details of whichever event is chosen, with what the logged in
 * user can do in it underneath.
 */
public final class EventsController {

    /** The key under which this screen tells the state which event it shows. */
    private static final String SCREEN = "events";
    /** The value of the toggle that filters nothing out. */
    private static final String ALL = "all";

    @FXML private ToggleGroup methodFilter;
    @FXML private ToggleGroup statusFilter;
    @FXML private ToggleGroup commissionFilter;
    @FXML private TableView<EventSummaryDto> eventsTable;
    @FXML private Label countLabel;
    @FXML private ScrollPane detailScroll;

    private final ObservableList<EventSummaryDto> events = FXCollections.observableArrayList();
    private final FilteredList<EventSummaryDto> shown = new FilteredList<>(events);
    private final EventDetailPane detail = new EventDetailPane();

    private AppState state;
    private TradeForm tradeForm;
    private Integer chosenEventId;

    @FXML
    private void initialize() {
        Tables.columns(eventsTable,
                Tables.indexColumn(),
                Tables.column("Event", EventSummaryDto::name),
                Tables.column("Method", EventSummaryDto::methodType),
                Tables.column("Options", event -> Integer.toString(event.optionNames().size())),
                Tables.column("Status", EventSummaryDto::phase),
                Tables.column("Commission",
                        event -> Format.percent(event.commissionPercent()) + " " + event.commissionMethod()),
                Tables.column("Market maker", EventSummaryDto::marketMakerName),
                Tables.column("Account", event -> Format.money(event.accountBalance())));
        eventsTable.setItems(shown);
        eventsTable.setPlaceholder(new Label("No events yet. Upload a file on the Account tab."));

        eventsTable.getSelectionModel().selectedItemProperty()
                .addListener((property, was, now) -> onEventChosen(now));

        keepAtLeastOneToggle(methodFilter);
        keepAtLeastOneToggle(statusFilter);
        keepAtLeastOneToggle(commissionFilter);
    }

    public void setUp(AppState state) {
        this.state = state;
        this.tradeForm = new TradeForm(state);
        VBox content = new VBox(detail, tradeForm);
        content.setPadding(new Insets(0.0, 0.0, 12.0, 0.0));
        detailScroll.setContent(content);
        detail.showNothing("Choose an event to see it.");

        state.events().listen(this::showEvents);
        state.eventFeed(SCREEN).listen(this::showDetail);
        // Being blocked, or no longer, changes what the form offers.
        state.me().listen(me -> {
            EventStateDto shownEvent = state.eventFeed(SCREEN).value();
            if (shownEvent != null) {
                tradeForm.show(shownEvent, me.blocked());
            }
        });
    }

    /** A fresh list of events, keeping whichever one was chosen. */
    private void showEvents(List<EventSummaryDto> fresh) {
        Integer keep = chosenEventId;
        events.setAll(fresh);
        applyFilters();
        reselect(keep);
    }

    private void reselect(Integer eventId) {
        if (eventId != null) {
            for (EventSummaryDto event : shown) {
                if (event.id() == eventId.intValue()) {
                    eventsTable.getSelectionModel().select(event);
                    return;
                }
            }
        }
        if (eventsTable.getSelectionModel().getSelectedItem() == null) {
            choose(null);
            detail.showNothing(shown.isEmpty()
                    ? (events.isEmpty() ? "No events yet." : "No events match the filters.")
                    : "Choose an event to see it.");
            tradeForm.showNothing();
        }
    }

    private void onEventChosen(EventSummaryDto event) {
        if (event != null) {
            choose(event.id());
        }
    }

    private void choose(Integer eventId) {
        chosenEventId = eventId;
        state.watch(SCREEN, eventId);
    }

    private void showDetail(EventStateDto event) {
        if (event == null || chosenEventId == null || event.summary().id() != chosenEventId) {
            return;
        }
        detail.show(event);
        UserDetailsDto me = state.me().value();
        tradeForm.show(event, me != null && me.blocked());
    }

    // ----------------------------------------------------------------- filters

    /**
     * A toggle group where clicking the chosen button again would leave nothing
     * selected, which would mean "no filter at all" and read as a bug. Keeping
     * the button pressed is friendlier than silently showing everything.
     */
    private void keepAtLeastOneToggle(ToggleGroup group) {
        group.selectedToggleProperty().addListener((property, was, now) -> {
            if (now == null) {
                group.selectToggle(was);
                return;
            }
            applyFilters();
            if (state != null) {
                reselect(chosenEventId);
            }
        });
    }

    private void applyFilters() {
        String method = chosen(methodFilter);
        String status = chosen(statusFilter);
        String commission = chosen(commissionFilter);

        shown.setPredicate(event -> matches(method, event.methodType())
                && matches(status, event.phase())
                && matches(commission, event.commissionMethod()));

        countLabel.setText(shown.size() == events.size()
                ? shown.size() + " events"
                : shown.size() + " of " + events.size() + " events");
    }

    private boolean matches(String filter, String value) {
        return ALL.equals(filter) || filter.equalsIgnoreCase(value);
    }

    private String chosen(ToggleGroup group) {
        Toggle selected = group.getSelectedToggle();
        return selected == null || selected.getUserData() == null
                ? ALL
                : String.valueOf(selected.getUserData());
    }
}
