package market.fx.screens;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;

import market.engine.api.EngineException;
import market.engine.dto.EventSummaryDto;
import market.fx.AppState;
import market.fx.components.EventDetailPane;
import market.fx.components.Tables;
import market.fx.util.Format;

/**
 * The events area: everything the system holds, filtered three ways, and the
 * details of whichever event is chosen.
 */
public final class EventsController {

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

    @FXML
    private void initialize() {
        Tables.columns(eventsTable,
                Tables.indexColumn(),
                Tables.column("Event", EventSummaryDto::name),
                Tables.column("Method", EventSummaryDto::methodType),
                Tables.column("Status", EventSummaryDto::phase),
                Tables.column("Commission",
                        event -> Format.percent(event.commissionPercent()) + " " + event.commissionMethod()),
                Tables.column("Market maker", EventSummaryDto::marketMakerName),
                Tables.column("Account", event -> Format.money(event.accountBalance())));
        eventsTable.setItems(shown);
        eventsTable.setPlaceholder(new Label("No events to show."));

        detailScroll.setContent(detail);

        eventsTable.getSelectionModel().selectedItemProperty()
                .addListener((property, was, now) -> onEventChosen(now));

        keepAtLeastOneToggle(methodFilter);
        keepAtLeastOneToggle(statusFilter);
        keepAtLeastOneToggle(commissionFilter);
    }

    public void setState(AppState state) {
        this.state = state;
        state.onRefresh(this::refresh);
    }

    /** Re-reads everything from the engine, keeping whichever event was chosen. */
    public void refresh() {
        if (state == null || !state.isFileLoaded()) {
            events.clear();
            detail.showNothing("Load a file to see the events.");
            countLabel.setText("");
            return;
        }
        Integer chosen = state.selectedEventId();
        try {
            List<EventSummaryDto> loaded = state.engine().listEvents();
            events.setAll(loaded);
        } catch (EngineException e) {
            events.clear();
        }
        applyFilters();
        reselect(chosen);
    }

    private void reselect(Integer eventId) {
        if (eventId != null) {
            for (EventSummaryDto event : shown) {
                if (event.id() == eventId.intValue()) {
                    eventsTable.getSelectionModel().select(event);
                    showDetail(eventId);
                    return;
                }
            }
        }
        if (eventsTable.getSelectionModel().getSelectedItem() == null) {
            detail.showNothing(shown.isEmpty()
                    ? "No events match the filters."
                    : "Choose an event to see it.");
        }
    }

    private void onEventChosen(EventSummaryDto event) {
        if (event == null) {
            return;
        }
        state.selectEvent(event.id());
        showDetail(event.id());
    }

    private void showDetail(int eventId) {
        try {
            detail.show(state.engine().eventState(eventId));
        } catch (EngineException e) {
            detail.showNothing(e.getMessage());
        }
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
