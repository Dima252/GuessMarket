import java.io.File;
import java.lang.reflect.Field;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import market.dto.ChatLineDto;
import market.dto.EventSummaryDto;
import market.dto.LoadReportDto;
import market.fx.GuessMarketApp;
import market.fx.components.TradeForm;
import market.fx.net.ServerApi;
import market.fx.tasks.UploadTask;

/**
 * Drives the real client against the real server, the way a person would: logs
 * in through the login screen, loads funds, trades, chats and logs out, while a
 * second user - a market maker, played here over plain HTTP - uploads a file and
 * trades against them. What the screens show is checked after every step, and a
 * picture of every tab is saved, at full size and squeezed to the smallest
 * window allowed, into build\screens.
 * <p>
 * Tomcat must be running with guess-market.war deployed. The names used are made
 * unique, so the check can run against a server that already holds data.
 */
public final class ClientCheck {

    private static final String API = "http://localhost:8080/guess-market/api/";
    private static final Path SCREENS = Path.of("build", "screens");
    private static final long TIMEOUT_MILLIS = 8000;

    private static int checks;
    private static int failures;

    private static Stage stage;
    private static GuessMarketApp app;
    private static final String SUFFIX = Long.toString(System.currentTimeMillis() % 1_000_000);
    private static final String ALICE = "Alice" + SUFFIX;
    private static final String ZOE = "Zoe" + SUFFIX;
    private static final String LMSR_EVENT = "Colours " + SUFFIX;
    private static final String BOOK_EVENT = "Rain " + SUFFIX;

    public static void main(String[] args) throws Exception {
        Files.createDirectories(SCREENS);
        Http zoe = new Http();
        try {
            if (zoe.get("events").statusCode() != 401) {
                throw new IllegalStateException("unexpected answer");
            }
        } catch (Exception e) {
            System.out.println("The server does not answer at " + API + " - start Tomcat first.");
            System.exit(2);
        }

        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        onFx(() -> {
            stage = new Stage();
            try {
                app = new GuessMarketApp();
                app.start(stage);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });

        loginScreen(zoe);
        loadFunds();
        eventsArrive(zoe);
        buyInLmsr();
        typingSurvivesPolling(zoe);
        orderBookTrade(zoe);
        chat(zoe);
        runningOwnEvent();
        pictures();
        logout();

        onFx(() -> {
            stage.close();
            return null;
        });
        System.out.println();
        System.out.println(failures == 0
                ? "All " + checks + " client checks passed. Pictures are in " + SCREENS.toAbsolutePath()
                : failures + " of " + checks + " client checks FAILED.");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ steps

    private static void loginScreen(Http zoe) throws Exception {
        section("Logging in");
        zoe.post("login", Map.of("name", ZOE));
        snapshot("0-login");

        type("#nameField", "  " + ZOE.toLowerCase(Locale.US) + " ");
        click("#loginButton");
        waitFor("a name somebody is logged in under is refused, whatever its case",
                () -> text("#messageLabel").contains("already taken"));

        type("#nameField", ALICE);
        click("#loginButton");
        waitFor("a free name logs in and opens the main screen", () -> lookup("#tabs") != null);
        waitFor("the header names the user", () -> text("#userLabel").contains(ALICE));
        waitFor("and shows an empty account", () -> text("#balanceLabel").contains("Balance 0.00"));
    }

    private static void loadFunds() throws Exception {
        section("Loading funds");
        selectTab(1);
        type("#depositField", "abc");
        click("#depositButton");
        waitFor("an amount that is not a number is caught at once",
                () -> text("#depositStatus").contains("not an amount"));
        type("#depositField", "-5");
        click("#depositButton");
        waitFor("a negative amount is refused by the server",
                () -> text("#depositStatus").contains("positive"));
        type("#depositField", "250");
        click("#depositButton");
        waitFor("a good one is loaded", () -> text("#depositStatus").contains("Loaded 250.00"));
        waitFor("the header follows", () -> text("#balanceLabel").contains("Balance 250.00"));
        waitFor("and the ledger has its first line", () -> rows("#ledgerTable") == 1);
        waitFor("the users table lists both users", () -> tableContains("#usersTable", ALICE + "  (you)")
                && tableContains("#usersTable", ZOE));
    }

    private static void eventsArrive(Http zoe) throws Exception {
        section("Events uploaded by somebody else appear by themselves");
        zoe.post("deposit", Map.of("amount", "1000"));
        String xml = Files.readString(Path.of("extra-test-files/EX3/three-options-lmsr.xml"))
                .replace("Three colours, LMSR", LMSR_EVENT)
                .replace("<GM-events>", "<GM-events>" + Files.readString(
                        Path.of("extra-test-files/EX3/simulation-order-book-on-purchase.xml"))
                        .replaceAll("(?s).*<GM-events>(.*)</GM-events>.*", "$1")
                        .replace("Will it rain tomorrow ?", BOOK_EVENT));
        HttpResponse<String> upload = zoe.upload("events.xml", xml);
        equal("Zoe's upload is accepted", upload.body().contains("\"success\":true"), true);
        selectTab(0);
        waitFor("both events show on Alice's events tab without her doing anything",
                () -> tableContains("#eventsTable", LMSR_EVENT) && tableContains("#eventsTable", BOOK_EVENT));
        int lmsr = eventId(zoe, LMSR_EVENT);
        int book = eventId(zoe, BOOK_EVENT);
        zoe.post("event/open", Map.of("id", Integer.toString(lmsr)));
        zoe.post("event/open", Map.of("id", Integer.toString(book)));
        waitFor("and so does their opening", () -> tableRow("#eventsTable", LMSR_EVENT).contains("Active"));
    }

    private static void buyInLmsr() throws Exception {
        section("Buying in an LMSR event with three options, from the events tab");
        selectRow("#eventsTable", LMSR_EVENT);
        waitFor("the trade form offers a purchase", () -> button("Buy") != null);
        equal("with all three options to choose from", optionCount(), 3);
        typeInForm("how many", "50");
        clickButton("Buy");
        waitFor("the purchase is reported", () -> formOutcome().startsWith("Bought 50 of \"Red\" for 19.58"));
        waitFor("and paid from the balance", () -> text("#balanceLabel").contains("Balance 230.42"));
        selectTab(1);
        waitFor("the ledger shows the purchase", () -> tableContains("#ledgerTable", "Bought 50 \"Red\""));
        waitFor("and the event is among Alice's own", () -> tableContains("#myEventsTable", LMSR_EVENT));
    }

    /**
     * The whole point of refreshing only what changed: the other user trades, the
     * event is polled again and again, and what Alice is typing is still there.
     */
    private static void typingSurvivesPolling(Http zoe) throws Exception {
        section("Polling does not wipe what is being typed");
        selectTab(0);
        selectRow("#eventsTable", BOOK_EVENT);
        waitFor("the order form is up", () -> button("Place the order") != null);
        typeInForm("how many", "12");
        typeInForm("per share", "0.4");
        int book = eventId(zoe, BOOK_EVENT);
        zoe.post("orderbook/order", Map.of("id", Integer.toString(book), "option", "0", "side", "sell",
                "quantity", "30", "price", "0.55"));
        waitFor("Zoe's order reaches Alice's screen", () -> bookShows("0.55"));
        Thread.sleep(2500);
        equal("the quantity typed is still there", formField("how many"), "12");
        equal("and so is the price", formField("per share"), "0.4");
    }

    private static void orderBookTrade(Http zoe) throws Exception {
        section("Trading through an order book");
        typeInForm("how many", "10");
        typeInForm("per share", "1.5");
        clickButton("Place the order");
        waitFor("a price above the base value is refused in the server's words",
                () -> formOutcome().contains("must be between"));
        typeInForm("per share", "0.55");
        clickButton("Place the order");
        waitFor("a good order executes against Zoe's", () -> formOutcome().startsWith("1 execution"));
        waitFor("and the balance pays for it, with the 1% commission",
                () -> text("#balanceLabel").contains("Balance 224.87"));
        int book = eventId(zoe, BOOK_EVENT);
        zoe.post("event/close", Map.of("id", Integer.toString(book), "winner", "0"));
        waitFor("when Zoe closes it, Alice's form says so", () -> formText().contains("winning option was \"Yes\""));
        waitFor("and Alice is paid without doing anything", () -> text("#balanceLabel").contains("Balance 234.87"));
        selectTab(1);
        waitFor("the payout shows in her ledger", () -> tableContains("#ledgerTable", "Payout"));
    }

    private static void chat(Http zoe) throws Exception {
        section("Chat");
        selectTab(2);
        zoe.post("chat", Map.of("text", "hello from " + ZOE));
        waitFor("a line Zoe wrote appears", () -> chatContains(ZOE, "hello from " + ZOE));
        type("#messageField", "hi " + ZOE);
        click("#sendButton");
        waitFor("and Alice's own line joins it", () -> chatContains(ALICE, "hi " + ZOE));
    }

    /**
     * A file chooser cannot be driven from here, so the file is handed straight
     * to the same task the "Load file" button starts, through the client's own
     * connection - which is the whole of what the button does once a file is chosen.
     */
    private static void runningOwnEvent() throws Exception {
        section("Uploading a file and running the event it brings");
        Field field = GuessMarketApp.class.getDeclaredField("api");
        field.setAccessible(true);
        ServerApi api = (ServerApi) field.get(app);

        String ownEvent = "Mujtaba " + SUFFIX;
        Path good = Path.of("build", "verify-client", "own-" + SUFFIX + ".xml");
        Files.writeString(good, Files.readString(Path.of("testing_files/EX3/small.xml"))
                .replace("Mujtaba is Dead", ownEvent));
        LoadReportDto added = upload(api, good.toFile());
        equal("the client's own upload is accepted", added.success(), true);
        LoadReportDto again = upload(api, good.toFile());
        equal("the same file again is refused, its name being taken", again.success(), false);
        LoadReportDto faulty = upload(api, new File("extra-test-files/EX3/bad-many-problems.xml"));
        equal("a faulty file is refused with every problem in it", faulty.errors().size() == 3, true);
        try {
            upload(api, new File("extra-test-files/EX3/not-an-xml.txt"));
            equal("a file that is not XML is refused", false, true);
        } catch (Exception refused) {
            equal("a file that is not XML is refused", refused.getMessage().contains("XML file"), true);
        }

        selectTab(1);
        waitFor("the new event is among Alice's own, as market maker",
                () -> tableRow("#myEventsTable", ownEvent).contains("Market maker"));
        selectRow("#myEventsTable", ownEvent);
        waitFor("she is offered to open it", () -> button("Open the event") != null);
        clickButton("Open the event");
        waitFor("opening it pays the subsidy b ln 2 = 69.31",
                () -> text("#balanceLabel").contains("Balance 165.55"));
        waitFor("and she is offered to close it", () -> button("Close on this option") != null);
        clickButton("Close on this option");
        waitFor("closing it with nobody holding anything returns the whole subsidy",
                () -> text("#balanceLabel").contains("Balance 234.87"));
        waitFor("the users table marks her as a market maker",
                () -> tableRow("#usersTable", ALICE).contains("yes"));
    }

    private static LoadReportDto upload(ServerApi api, File file) throws Exception {
        UploadTask task = new UploadTask(api, file);
        Thread worker = new Thread(task);
        worker.start();
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void pictures() throws Exception {
        section("Pictures, full size and squeezed");
        selectTab(0);
        selectRow("#eventsTable", BOOK_EVENT);
        Thread.sleep(1500);
        snapshot("1-events");
        selectRow("#eventsTable", LMSR_EVENT);
        Thread.sleep(1500);
        snapshot("2-events-lmsr");
        selectTab(1);
        selectRow("#myEventsTable", BOOK_EVENT);
        Thread.sleep(1500);
        snapshot("3-account");
        selectTab(2);
        snapshot("4-chat");

        onFx(() -> {
            stage.setWidth(stage.getMinWidth());
            stage.setHeight(stage.getMinHeight());
            return null;
        });
        Thread.sleep(800);
        selectTab(0);
        snapshot("5-events-small");
        selectTab(1);
        snapshot("6-account-small");
        onFx(() -> {
            stage.setWidth(1180);
            stage.setHeight(740);
            return null;
        });
    }

    private static void logout() throws Exception {
        section("Logging out");
        clickButtonAnywhere("Log out");
        waitFor("brings the login screen back", () -> lookup("#nameField") != null);
        waitFor("saying so", () -> text("#messageLabel").contains("logged out"));
        type("#nameField", ALICE);
        click("#loginButton");
        waitFor("and the name can log in again, to the same account",
                () -> lookup("#balanceLabel") != null && text("#balanceLabel").contains("Balance 234.87"));
    }

    // ---------------------------------------------------------- the screens

    private static Node lookup(String selector) {
        return onFxQuietly(() -> stage.getScene().lookup(selector));
    }

    private static String text(String selector) {
        return onFxQuietly(() -> {
            Node node = stage.getScene().lookup(selector);
            return node instanceof Label label ? String.valueOf(label.getText()) : "";
        });
    }

    private static void type(String selector, String value) throws Exception {
        onFx(() -> {
            ((TextField) stage.getScene().lookup(selector)).setText(value);
            return null;
        });
    }

    private static void click(String selector) throws Exception {
        onFx(() -> {
            ((Button) stage.getScene().lookup(selector)).fire();
            return null;
        });
    }

    private static void selectTab(int index) throws Exception {
        onFx(() -> {
            ((TabPane) stage.getScene().lookup("#tabs")).getSelectionModel().select(index);
            return null;
        });
        Thread.sleep(200);
    }

    private static int rows(String selector) {
        return onFxQuietly(() -> ((TableView<?>) stage.getScene().lookup(selector)).getItems().size());
    }

    private static boolean tableContains(String selector, String fragment) {
        return !tableRow(selector, fragment).isEmpty();
    }

    /** The text of the first row whose cells mention the fragment, as the columns show it. */
    private static String tableRow(String selector, String fragment) {
        return onFxQuietly(() -> {
            TableView<?> table = (TableView<?>) stage.getScene().lookup(selector);
            for (int row = 0; row < table.getItems().size(); row++) {
                StringBuilder line = new StringBuilder();
                for (var column : table.getColumns()) {
                    line.append(column.getCellData(row)).append(" | ");
                }
                if (line.toString().contains(fragment)) {
                    return line.toString();
                }
            }
            return "";
        });
    }

    private static void selectRow(String selector, String fragment) throws Exception {
        onFx(() -> {
            @SuppressWarnings("unchecked")
            TableView<Object> table = (TableView<Object>) stage.getScene().lookup(selector);
            for (int row = 0; row < table.getItems().size(); row++) {
                for (var column : table.getColumns()) {
                    if (String.valueOf(column.getCellData(row)).contains(fragment)) {
                        table.getSelectionModel().select(row);
                        return null;
                    }
                }
            }
            throw new IllegalStateException("no row mentions " + fragment);
        });
    }

    private static boolean chatContains(String user, String text) {
        return onFxQuietly(() -> {
            ListView<?> list = (ListView<?>) stage.getScene().lookup("#linesList");
            for (Object item : list.getItems()) {
                ChatLineDto line = (ChatLineDto) item;
                if (line.userName().equals(user) && line.text().equals(text)) {
                    return true;
                }
            }
            return false;
        });
    }

    /** The trade form that is on screen right now - the one of the tab in front. */
    private static TradeForm form() {
        List<TradeForm> forms = new ArrayList<>();
        collect(stage.getScene().getRoot(), TradeForm.class, forms);
        for (TradeForm form : forms) {
            if (form.getScene() != null && form.isVisible() && isShowing(form)) {
                return form;
            }
        }
        return null;
    }

    private static boolean isShowing(Node node) {
        for (Node at = node; at != null; at = at.getParent()) {
            if (!at.isVisible()) {
                return false;
            }
        }
        // A tab that is not selected keeps its content out of the scene graph.
        return node.getScene() != null && node.localToScene(0, 0) != null
                && isUnderSelectedTab(node);
    }

    private static boolean isUnderSelectedTab(Node node) {
        TabPane tabs = (TabPane) stage.getScene().lookup("#tabs");
        Node content = tabs.getSelectionModel().getSelectedItem().getContent();
        for (Node at = node; at != null; at = at.getParent()) {
            if (at == content) {
                return true;
            }
        }
        return false;
    }

    private static Button button(String text) {
        return onFxQuietly(() -> {
            TradeForm form = form();
            if (form == null) {
                return null;
            }
            List<Button> buttons = new ArrayList<>();
            collect(form, Button.class, buttons);
            for (Button button : buttons) {
                if (text.equals(button.getText())) {
                    return button;
                }
            }
            return null;
        });
    }

    private static void clickButton(String text) throws Exception {
        onFx(() -> {
            button(text).fire();
            return null;
        });
    }

    private static void clickButtonAnywhere(String text) throws Exception {
        onFx(() -> {
            List<Button> buttons = new ArrayList<>();
            collect(stage.getScene().getRoot(), Button.class, buttons);
            for (Button button : buttons) {
                if (text.equals(button.getText())) {
                    button.fire();
                    return null;
                }
            }
            throw new IllegalStateException("no button " + text);
        });
    }

    private static TextField formFieldNode(String prompt) {
        List<TextField> fields = new ArrayList<>();
        collect(form(), TextField.class, fields);
        for (TextField field : fields) {
            if (prompt.equals(field.getPromptText())) {
                return field;
            }
        }
        throw new IllegalStateException("no field " + prompt);
    }

    private static void typeInForm(String prompt, String value) throws Exception {
        onFx(() -> {
            formFieldNode(prompt).setText(value);
            return null;
        });
    }

    private static String formField(String prompt) {
        return onFxQuietly(() -> formFieldNode(prompt).getText());
    }

    private static int optionCount() {
        return onFxQuietly(() -> {
            List<javafx.scene.control.ChoiceBox<?>> boxes = new ArrayList<>();
            collectChoiceBoxes(form(), boxes);
            return boxes.isEmpty() ? 0 : boxes.get(0).getItems().size();
        });
    }

    private static void collectChoiceBoxes(Node node, List<javafx.scene.control.ChoiceBox<?>> found) {
        if (node instanceof javafx.scene.control.ChoiceBox<?> box) {
            found.add(box);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectChoiceBoxes(child, found);
            }
        }
    }

    /** Every label of the trade form, joined: the outcome of the last action is the last of them. */
    private static String formText() {
        return onFxQuietly(() -> {
            TradeForm form = form();
            if (form == null) {
                return "";
            }
            List<Label> labels = new ArrayList<>();
            collect(form, Label.class, labels);
            StringBuilder all = new StringBuilder();
            for (Label label : labels) {
                all.append(label.getText()).append('\n');
            }
            return all.toString();
        });
    }

    private static String formOutcome() {
        return onFxQuietly(() -> {
            TradeForm form = form();
            if (form == null || form.getChildren().isEmpty()) {
                return "";
            }
            Node last = form.getChildren().get(form.getChildren().size() - 1);
            return last instanceof Label label ? label.getText() : "";
        });
    }

    private static boolean bookShows(String price) {
        return onFxQuietly(() -> {
            List<TableView<?>> tables = new ArrayList<>();
            collectTables(stage.getScene().getRoot(), tables);
            for (TableView<?> table : tables) {
                for (int row = 0; row < table.getItems().size(); row++) {
                    for (var column : table.getColumns()) {
                        if ("Price".equals(column.getText()) && price.equals(String.valueOf(column.getCellData(row)))
                                && isUnderSelectedTab(table)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        });
    }

    private static void collectTables(Node node, List<TableView<?>> found) {
        if (node instanceof TableView<?> table) {
            found.add(table);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectTables(child, found);
            }
        }
    }

    private static <T> void collect(Node node, Class<T> type, List<T> found) {
        if (node == null) {
            return;
        }
        if (type.isInstance(node)) {
            found.add(type.cast(node));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, type, found);
            }
        }
    }

    private static void snapshot(String name) throws Exception {
        WritableImage image = onFx(() -> {
            Scene scene = stage.getScene();
            return scene.snapshot(null);
        });
        File file = SCREENS.resolve(name + ".png").toFile();
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
        System.out.println("  saved " + file);
    }

    // ------------------------------------------------------------ the other user

    /** The second user, over plain HTTP with a session cookie of its own. */
    private static final class Http {
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create(API + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, Map<String, String> form) throws Exception {
            StringBuilder body = new StringBuilder();
            form.forEach((key, value) -> body.append(body.isEmpty() ? "" : "&")
                    .append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8)));
            return client.send(HttpRequest.newBuilder(URI.create(API + path))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> upload(String fileName, String contents) throws Exception {
            String boundary = "----check" + SUFFIX;
            String body = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: text/xml\r\n\r\n" + contents + "\r\n--" + boundary + "--\r\n";
            return client.send(HttpRequest.newBuilder(URI.create(API + "upload"))
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private static int eventId(Http http, String name) throws Exception {
        String body = http.get("events").body();
        EventSummaryDto[] events = new com.google.gson.Gson().fromJson(body, EventSummaryDto[].class);
        for (EventSummaryDto event : events) {
            if (event.name().equals(name)) {
                return event.id();
            }
        }
        throw new IllegalStateException("no event " + name);
    }

    // ----------------------------------------------------------------- tools

    private static <T> T onFx(Supplier<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> problem = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(work.get());
            } catch (Throwable t) {
                problem.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("the JavaFX thread did not answer");
        }
        if (problem.get() != null) {
            throw new IllegalStateException(problem.get());
        }
        return result.get();
    }

    private static <T> T onFxQuietly(Supplier<T> work) {
        if (Platform.isFxApplicationThread()) {
            return work.get();
        }
        try {
            return onFx(work);
        } catch (Exception e) {
            return null;
        }
    }

    private static void waitFor(String what, BooleanSupplier condition) throws InterruptedException {
        checks++;
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            Boolean met = onFxQuietly(condition::getAsBoolean);
            if (Boolean.TRUE.equals(met)) {
                System.out.println("  ok    " + what);
                return;
            }
            Thread.sleep(100);
        }
        failures++;
        System.out.println("  FAIL  " + what + " - not within " + TIMEOUT_MILLIS / 1000 + " seconds");
    }

    private static <T> void equal(String what, T actual, T expected) {
        checks++;
        if (actual == null ? expected != null : !actual.equals(expected)) {
            failures++;
            System.out.println("  FAIL  " + what + ": expected " + expected + ", got " + actual);
            return;
        }
        System.out.println("  ok    " + what + ": " + actual);
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title);
    }
}
