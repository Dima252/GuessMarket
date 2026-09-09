import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

import market.fx.AppState;
import market.fx.screens.EventsController;

/**
 * Drives the events screen without a mouse: loads it, feeds it a file, works
 * each of the three filters, and reads back what the detail pane says about an
 * LMSR event and about an order book one.
 */
public final class EventsScreenCheck {

    private static int checks;
    private static int failures;

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> { });
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        if (!done.await(30, TimeUnit.SECONDS)) {
            System.out.println("TIMED OUT");
            System.exit(1);
        }
        Platform.exit();

        if (failure.get() != null) {
            failure.get().printStackTrace();
            System.exit(1);
        }
        System.out.println();
        System.out.println(failures == 0 ? "All " + checks + " checks passed."
                : failures + " of " + checks + " checks FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run() throws Exception {
        FXMLLoader loader = new FXMLLoader(
                EventsController.class.getResource("/market/fx/screens/events-view.fxml"));
        SplitPane root = loader.load();
        EventsController controller = loader.getController();
        new Scene(root, 1100, 700);   // the toggles need a scene before they behave

        AppState state = new AppState();
        controller.setState(state);

        System.out.println("== before a file is loaded");
        controller.refresh();
        equal("the table is empty", rows(root), 0);

        System.out.println();
        System.out.println("== multiple.xml");
        if (!state.engine().loadFile("testing_files/EX2/multiple.xml").success()) {
            throw new IllegalStateException("the file should load");
        }
        state.refresh();
        equal("all four events are listed", rows(root), 4);

        press(root, METHOD, "LMSR");
        equal("two of them are LMSR", rows(root), 2);
        press(root, METHOD, "Order Book");
        equal("two of them are order books", rows(root), 2);
        press(root, METHOD, "All");
        equal("and the all button brings them back", rows(root), 4);

        press(root, STATUS, "Active");
        equal("none is active yet", rows(root), 0);
        press(root, STATUS, "Not started");
        equal("all four are waiting to be opened", rows(root), 4);

        press(root, COMMISSION, "On purchase");
        equal("two charge on purchase", rows(root), 2);
        press(root, COMMISSION, "On close");
        equal("two charge on close", rows(root), 2);

        System.out.println();
        System.out.println("== after Avrum opens his order book event");
        state.engine().openEvent("Avrum", 2);
        state.refresh();
        press(root, COMMISSION, "All");
        press(root, STATUS, "Active");
        equal("one event is now active", rows(root), 1);
        press(root, STATUS, "All");

        System.out.println();
        System.out.println("== choosing an event fills the detail pane");
        TableView<?> table = table(root);
        table.getSelectionModel().select(0);
        VBox detail = (VBox) ((ScrollPane) root.getItems().get(1)).getContent();
        equal("the pane is no longer a single message", detail.getChildren().size() > 3, true);

        List<String> texts = new ArrayList<>();
        collectText(detail, texts);
        contains(texts, "Mujtaba is Dead");
        contains(texts, "LMSR");
        contains(texts, "Not started");

        state.selectEvent(2);
        state.refresh();
        texts.clear();
        collectText((VBox) ((ScrollPane) root.getItems().get(1)).getContent(), texts);
        contains(texts, "World Cap Winner");
        contains(texts, "Order books");
        contains(texts, "Argentina");
        contains(texts, "Spain");
    }

    // ------------------------------------------------------------------ tools

    private static int rows(SplitPane root) {
        return table(root).getItems().size();
    }

    private static TableView<?> table(SplitPane root) {
        List<TableView<?>> found = new ArrayList<>();
        walk(root.getItems().get(0), node -> {
            if (node instanceof TableView<?> table) {
                found.add(table);
            }
        });
        if (found.isEmpty()) {
            throw new IllegalStateException("the events table is not on the screen");
        }
        return found.get(0);
    }

    private static final int METHOD = 0;
    private static final int STATUS = 1;
    private static final int COMMISSION = 2;

    /**
     * Clicks a filter button. Every filter row has an "All" of its own, so the
     * row has to be named as well as the label.
     */
    private static void press(SplitPane root, int filterRow, String text) {
        List<javafx.scene.layout.FlowPane> rows = new ArrayList<>();
        // Without a stage the SplitPane has no skin, so its items are reachable
        // through getItems() rather than as children.
        walk(root.getItems().get(0), node -> {
            if (node instanceof javafx.scene.layout.FlowPane row) {
                rows.add(row);
            }
        });
        for (Node node : rows.get(filterRow).getChildren()) {
            if (node instanceof ToggleButton button && text.equals(button.getText())) {
                button.fire();
                return;
            }
        }
        throw new IllegalStateException("no button called " + text + " in filter row " + filterRow);
    }

    private static void walk(Node node, java.util.function.Consumer<Node> visitor) {
        visitor.accept(node);
        if (node instanceof javafx.scene.Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> walk(child, visitor));
        }
    }

    private static void collectText(Node node, List<String> into) {
        if (node instanceof javafx.scene.control.Labeled labeled && labeled.getText() != null) {
            into.add(labeled.getText());
        }
        if (node instanceof TableView<?> table) {
            table.getColumns().forEach(column -> into.add(column.getText()));
            table.getItems().forEach(item -> into.add(String.valueOf(item)));
        }
        if (node instanceof javafx.scene.Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectText(child, into));
        }
    }

    private static void contains(List<String> texts, String wanted) {
        checks++;
        boolean found = texts.stream().anyMatch(text -> text != null && text.contains(wanted));
        if (found) {
            System.out.println("  ok    the pane mentions \"" + wanted + "\"");
        } else {
            failures++;
            System.out.println("  FAIL  the pane never mentions \"" + wanted + "\"");
        }
    }

    private static <T> void equal(String what, T actual, T expected) {
        checks++;
        if (actual == null ? expected != null : !actual.equals(expected)) {
            failures++;
            System.out.println("  FAIL  " + what + ": expected " + expected + ", got " + actual);
        } else {
            System.out.println("  ok    " + what + ": " + actual);
        }
    }
}
