import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import market.engine.dto.UserSummaryDto;
import market.fx.AppState;
import market.fx.screens.UsersController;

/**
 * Drives the users screen the way a person would: chooses somebody, chooses an
 * event, opens it, trades in it, is refused where it should be refused, and
 * closes it. This is the half of the application that acts, so it is the half
 * worth driving.
 */
public final class UsersScreenCheck {

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

        if (!done.await(60, TimeUnit.SECONDS)) {
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

    private static SplitPane root;
    private static AppState state;

    private static void run() throws Exception {
        FXMLLoader loader = new FXMLLoader(
                UsersController.class.getResource("/market/fx/screens/users-view.fxml"));
        root = loader.load();
        UsersController controller = loader.getController();
        new Scene(root, 1200, 800);

        state = new AppState();
        controller.setState(state);
        if (!state.engine().loadFile("testing_files/EX2/multiple.xml").success()) {
            throw new IllegalStateException("multiple.xml should load");
        }
        state.refresh();

        System.out.println("== everybody in the file");
        equal("three users are listed", usersTable().getItems().size(), 3);

        System.out.println();
        System.out.println("== Tikva, who carries three of the four events");
        chooseUser("Tikva");
        equal("all four events are offered to her", eventsTable().getItems().size(), 4);
        equal("and three of them are hers", rolesSaying("Market maker"), 3);
        near("she starts with", balanceOf("Tikva"), 10000.00);

        System.out.println();
        System.out.println("== she opens her LMSR event");
        chooseEvent("Mujtaba is Dead");
        press("Open the event");
        equal("the event is now active", statusOf("Mujtaba is Dead"), "Active");
        near("and the subsidy came out of her account", balanceOf("Tikva"), 10000.00 - 69.31);
        says("the form says what happened", "The event is open");

        System.out.println();
        System.out.println("== Menash, who is nobody's market maker, buys into it");
        chooseUser("Menash");
        chooseEvent("Mujtaba is Dead");
        type("how many", "10");
        press("Buy");
        says("the purchase is reported", "Bought 10");
        near("and he paid what LMSR charged", balanceOf("Menash"), 100.00 - 5.38);

        System.out.println();
        System.out.println("== what he is not offered, and what he is refused");
        chooseEvent("Will it rain tomorrow ?");
        says("somebody else's unopened event offers him no button, and says why",
                "not been opened yet by Tikva");
        equal("and it stayed shut", statusOf("Will it rain tomorrow ?"), "Not started");

        // Nothing on the screen can stop this one, so the engine has to, and its
        // words have to reach the person who tried.
        chooseEvent("Mujtaba is Dead");
        type("how many", "100000");
        press("Buy");
        says("a purchase beyond his balance is refused in his own terms", "has only");

        System.out.println();
        System.out.println("== Avrum opens his order book event and offers shares");
        chooseUser("Avrum");
        chooseEvent("World Cap Winner");
        press("Open the event");
        equal("it is open", statusOf("World Cap Winner"), "Active");
        near("the hundred he paid bought the first pairs", balanceOf("Avrum"), 900.00);

        choose("Sell");
        type("how many", "40");
        type("per share", "0.60");
        press("Place the order");
        says("nothing matched it yet", "waiting in the book");

        System.out.println();
        System.out.println("== Menash takes half of what Avrum offered");
        chooseUser("Menash");
        chooseEvent("World Cap Winner");
        type("how many", "20");
        type("per share", "0.60");
        press("Place the order");
        says("the order executed", "execution");
        // This event charges on close, so nothing is added on top of the twelve.
        near("twelve dollars left his account", balanceOf("Menash"), 100.00 - 5.38 - 12.00);

        System.out.println();
        System.out.println("== a price the exchange will not take");
        type("how many", "5");
        type("per share", "1.40");
        press("Place the order");
        says("it is refused with the range", "between 0.01 and 0.99");

        System.out.println();
        System.out.println("== Avrum closes his event");
        chooseUser("Avrum");
        chooseEvent("World Cap Winner");
        press("Close on this option");
        equal("the event is closed", statusOf("World Cap Winner"), "Closed");
        says("and it says so", "closed on");
    }

    // ------------------------------------------------------- driving the screen

    private static void chooseUser(String name) {
        TableView<?> table = usersTable();
        for (int i = 0; i < table.getItems().size(); i++) {
            if (((UserSummaryDto) table.getItems().get(i)).name().equals(name)) {
                table.getSelectionModel().select(i);
                return;
            }
        }
        throw new IllegalStateException("no user called " + name);
    }

    private static void chooseEvent(String name) {
        TableView<?> table = eventsTable();
        for (int i = 0; i < table.getItems().size(); i++) {
            if (rowText(table, i).contains(name)) {
                table.getSelectionModel().select(i);
                return;
            }
        }
        throw new IllegalStateException("no event called " + name);
    }

    private static void press(String buttonText) {
        for (Node node : detailNodes()) {
            if (node instanceof Button button && buttonText.equals(button.getText())) {
                button.fire();
                return;
            }
        }
        throw new IllegalStateException("no button called " + buttonText);
    }

    private static void type(String promptText, String text) {
        for (Node node : detailNodes()) {
            if (node instanceof TextField field && promptText.equals(field.getPromptText())) {
                field.setText(text);
                return;
            }
        }
        throw new IllegalStateException("no field prompting " + promptText);
    }

    /** Picks a value in whichever choice box offers it. */
    private static void choose(String value) {
        for (Node node : detailNodes()) {
            if (node instanceof ChoiceBox<?> box) {
                for (Object item : box.getItems()) {
                    if (String.valueOf(item).equalsIgnoreCase(value)) {
                        select(box, item);
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("nothing offers " + value);
    }

    @SuppressWarnings("unchecked")
    private static void select(ChoiceBox<?> box, Object item) {
        ((ChoiceBox<Object>) box).getSelectionModel().select(item);
    }

    // ------------------------------------------------------- reading it back

    private static TableView<?> usersTable() {
        return tables(root.getItems().get(0)).get(0);
    }

    private static TableView<?> eventsTable() {
        // Without a stage neither the SplitPane nor the ScrollPane has a skin, so
        // what they hold is reached through getItems() and getContent() rather
        // than as children.
        return tables(((ScrollPane) root.getItems().get(1)).getContent()).get(0);
    }

    private static List<TableView<?>> tables(Node from) {
        List<TableView<?>> found = new ArrayList<>();
        walk(from, node -> {
            if (node instanceof TableView<?> table) {
                found.add(table);
            }
        });
        return found;
    }

    private static List<Node> detailNodes() {
        List<Node> found = new ArrayList<>();
        walk(((ScrollPane) root.getItems().get(1)).getContent(), found::add);
        return found;
    }

    private static void walk(Node node, java.util.function.Consumer<Node> visitor) {
        visitor.accept(node);
        if (node instanceof javafx.scene.Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> walk(child, visitor));
        }
    }

    private static String rowText(TableView<?> table, int row) {
        StringBuilder text = new StringBuilder();
        for (var column : table.getColumns()) {
            text.append(String.valueOf(column.getCellData(row))).append(' ');
        }
        return text.toString();
    }

    private static int rolesSaying(String role) {
        int count = 0;
        for (int i = 0; i < eventsTable().getItems().size(); i++) {
            if (rowText(eventsTable(), i).contains(role)) {
                count++;
            }
        }
        return count;
    }

    private static String statusOf(String eventName) {
        for (int i = 0; i < eventsTable().getItems().size(); i++) {
            String text = rowText(eventsTable(), i);
            if (text.contains(eventName)) {
                for (String status : new String[] {"Not started", "Active", "Closed"}) {
                    if (text.contains(status)) {
                        return status;
                    }
                }
            }
        }
        return "not listed";
    }

    private static double balanceOf(String userName) {
        for (Object item : usersTable().getItems()) {
            UserSummaryDto user = (UserSummaryDto) item;
            if (user.name().equals(userName)) {
                return user.balance();
            }
        }
        throw new IllegalStateException("no user called " + userName);
    }

    /** The form reports what happened, and that report is part of the screen. */
    private static void says(String what, String fragment) {
        checks++;
        for (Node node : detailNodes()) {
            if (node instanceof Label label && label.getText() != null
                    && label.getText().toLowerCase().contains(fragment.toLowerCase())) {
                System.out.println("  ok    " + what + ": \"" + label.getText() + "\"");
                return;
            }
        }
        failures++;
        System.out.println("  FAIL  " + what + ": nothing on the screen says \"" + fragment + "\"");
    }

    private static void near(String what, double actual, double expected) {
        checks++;
        if (Math.abs(actual - expected) > 0.005) {
            failures++;
            System.out.printf(java.util.Locale.US, "  FAIL  %s: expected %.2f, got %.2f%n",
                    what, expected, actual);
        } else {
            System.out.printf(java.util.Locale.US, "  ok    %s: %.2f%n", what, actual);
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
