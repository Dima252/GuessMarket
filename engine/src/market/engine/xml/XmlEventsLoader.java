package market.engine.xml;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import market.engine.model.CommissionType;
import market.engine.model.Event;
import market.engine.model.EventOption;
import market.engine.model.LmsrMethod;
import market.engine.model.OrderBookMethod;
import market.engine.model.TradingMethod;
import market.engine.model.User;

/**
 * Reads an events file of the exercise 2 schema and checks that it makes sense.
 * <p>
 * The file is guaranteed to be schema valid but not necessarily application
 * valid, so every rule of the specification is checked here. All problems found
 * are collected and reported together, and the contents are handed over only
 * when the file is completely sound.
 * <p>
 * Where the appendix of the specification and the schema published with the
 * course disagree on the spelling of a name, both spellings are accepted: the
 * schema of exercise 1 really did write {@code comision} with one m, and the
 * appendix of exercise 2 prints {@code GM-mareket-maker} and {@code inital}
 * where the real schema writes them correctly.
 */
public final class XmlEventsLoader {

    private static final String XML_SUFFIX = ".xml";
    private static final String ROOT = "Guess-Market";
    private static final String EVENTS = "GM-events";
    private static final String EVENT = "GM-event";
    private static final String OPTIONS = "GM-options";
    private static final String OPTION = "GM-option";
    private static final String METHOD = "GM-method";
    private static final String LMSR = "GM-LMSR";
    private static final String ORDER_BOOK = "GM-order-book";
    private static final String USERS = "GM-users";
    private static final String USER = "GM-user";
    private static final String INITIAL_CASH = "initial-cash";

    private static final List<String> COMMISSION_NAMES = List.of("commission", "comision");
    private static final List<String> MARKET_MAKER_NAMES = List.of("GM-market-maker", "GM-mareket-maker");
    private static final List<String> INITIAL_ATTRIBUTES = List.of("initial", "inital");

    private static final int MIN_COMMISSION = 0;
    private static final int MAX_COMMISSION = 90;
    private static final int REQUIRED_OPTIONS = 2;

    public LoadOutcome load(String path) {
        List<String> pathProblems = validatePath(path);
        if (!pathProblems.isEmpty()) {
            return LoadOutcome.failed(pathProblems);
        }
        try {
            return read(parse(new File(path.trim())));
        } catch (SAXParseException e) {
            return LoadOutcome.failed(List.of("The file is not a valid XML document: " + e.getMessage()
                    + " (line " + e.getLineNumber() + ", column " + e.getColumnNumber() + ")."));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            return LoadOutcome.failed(List.of("The file could not be read: " + e.getMessage()));
        }
    }

    private List<String> validatePath(String path) {
        List<String> problems = new ArrayList<>();
        if (path == null || path.trim().isEmpty()) {
            problems.add("No file path was entered.");
            return problems;
        }
        String trimmed = path.trim();
        if (!trimmed.toLowerCase(Locale.US).endsWith(XML_SUFFIX)) {
            problems.add("The file must be an XML file, but \"" + trimmed + "\" does not end with .xml.");
            return problems;
        }
        File file = new File(trimmed);
        if (!file.exists()) {
            problems.add("No file was found at \"" + trimmed + "\".");
        } else if (file.isDirectory()) {
            problems.add("\"" + trimmed + "\" is a folder, not a file.");
        } else if (!file.canRead()) {
            problems.add("The file \"" + trimmed + "\" exists but cannot be read. Check its permissions.");
        }
        return problems;
    }

    private Document parse(File file) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        // Without this the parser writes warnings straight to the console, and the
        // engine module is not allowed to print anything.
        builder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
                // ignored on purpose
            }

            @Override
            public void error(SAXParseException e) throws SAXException {
                throw e;
            }

            @Override
            public void fatalError(SAXParseException e) throws SAXException {
                throw e;
            }
        });
        return builder.parse(file);
    }

    private LoadOutcome read(Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !ROOT.equals(root.getTagName())) {
            String found = root == null ? "nothing" : "<" + root.getTagName() + ">";
            return LoadOutcome.failed(List.of("The root element must be <" + ROOT + ">, but " + found + " was found."));
        }

        Optional<Element> eventsElement = firstChild(root, EVENTS);
        if (eventsElement.isEmpty()) {
            return LoadOutcome.failed(List.of("The file does not contain a <" + EVENTS + "> element."));
        }
        Optional<Element> usersElement = firstChild(root, USERS);
        if (usersElement.isEmpty()) {
            return LoadOutcome.failed(List.of("The file does not contain a <" + USERS + "> element. "
                    + "This exercise reads files of the exercise 2 format, which describes the users of the "
                    + "system as well as its events."));
        }

        List<Element> eventElements = children(eventsElement.get(), EVENT);
        if (eventElements.isEmpty()) {
            return LoadOutcome.failed(List.of("The file does not contain any <" + EVENT + "> element."));
        }
        List<Element> userElements = children(usersElement.get(), USER);
        if (userElements.isEmpty()) {
            return LoadOutcome.failed(List.of("The file does not contain any <" + USER + "> element."));
        }

        List<String> errors = new ArrayList<>();
        Map<Integer, Event> eventsById = new LinkedHashMap<>();
        Map<Integer, String> idsSeen = new HashMap<>();

        for (int position = 0; position < eventElements.size(); position++) {
            new EventReader(eventElements.get(position), position + 1, idsSeen, errors)
                    .read()
                    .ifPresent(event -> eventsById.put(event.id(), event));
        }

        List<User> users = new ArrayList<>();
        Map<String, String> namesSeen = new HashMap<>();
        Map<Integer, List<String>> marketMakersByEvent = new LinkedHashMap<>();

        for (int position = 0; position < userElements.size(); position++) {
            ReadUser read = new UserReader(userElements.get(position), position + 1, namesSeen, errors).read();
            if (read.user() != null) {
                users.add(read.user());
            }
            // The claims are registered even when the user was refused. Otherwise
            // refusing one user would make every event they carry look as though
            // the file had left it without a market maker, which it had not.
            for (int eventId : read.marketMakerOf()) {
                marketMakersByEvent
                        .computeIfAbsent(eventId, key -> new ArrayList<>())
                        .add(read.name());
            }
        }

        checkMarketMakers(eventsById, marketMakersByEvent, users, errors);

        if (!errors.isEmpty()) {
            return LoadOutcome.failed(errors);
        }
        return LoadOutcome.loaded(new ArrayList<>(eventsById.values()), users);
    }

    /**
     * Every event needs exactly one market maker, and a market maker may only
     * point at an event that is really there.
     */
    private void checkMarketMakers(Map<Integer, Event> eventsById,
                                   Map<Integer, List<String>> marketMakersByEvent,
                                   List<User> users,
                                   List<String> errors) {
        Map<String, User> usersByName = new LinkedHashMap<>();
        for (User user : users) {
            usersByName.put(user.name(), user);
        }

        for (Map.Entry<Integer, List<String>> entry : marketMakersByEvent.entrySet()) {
            if (!eventsById.containsKey(entry.getKey())) {
                errors.add("The user \"" + entry.getValue().get(0) + "\" is set as the market maker of event "
                        + entry.getKey() + ", but there is no event with that id in the file.");
            }
        }

        for (Event event : eventsById.values()) {
            List<String> makers = marketMakersByEvent.getOrDefault(event.id(), List.of());
            if (makers.isEmpty()) {
                errors.add("The event \"" + event.name() + "\" (id " + event.id()
                        + ") has no market maker. Every event must be assigned to exactly one user.");
            } else if (makers.size() > 1) {
                errors.add("The event \"" + event.name() + "\" (id " + event.id()
                        + ") has " + makers.size() + " market makers (" + String.join(", ", makers)
                        + "), but every event must be assigned to exactly one user.");
            } else {
                event.assignMarketMaker(usersByName.get(makers.get(0)));
            }
        }
    }

    /** Reads and validates one event element, appending anything wrong with it to the shared error list. */
    private static final class EventReader {

        private final Element element;
        private final int position;
        private final Map<Integer, String> idsSeen;
        private final List<String> errors;
        private final String name;

        private EventReader(Element element, int position, Map<Integer, String> idsSeen, List<String> errors) {
            this.element = element;
            this.position = position;
            this.idsSeen = idsSeen;
            this.errors = errors;
            this.name = element.getAttribute("name").trim();
        }

        private Optional<Event> read() {
            int errorsBefore = errors.size();

            if (name.isEmpty()) {
                error("has an empty name attribute.");
            }
            Integer id = readId();
            String description = readDescription();
            Integer commission = readCommission();
            CommissionType commissionType = readCommissionType();
            List<EventOption> options = readOptions();
            TradingMethod method = readMethod(options.size());

            if (errors.size() != errorsBefore || id == null || commission == null
                    || commissionType == null || method == null) {
                return Optional.empty();
            }
            return Optional.of(new Event(id, name, description, commission, commissionType, options, method));
        }

        private Integer readId() {
            Optional<Element> idElement = firstChild(element, "id");
            if (idElement.isEmpty()) {
                error("is missing its <id> element.");
                return null;
            }
            String text = text(idElement.get());
            Integer id = parseInteger(text);
            if (id == null) {
                error("has the id \"" + text + "\", which is not a whole number.");
                return null;
            }
            String previous = idsSeen.putIfAbsent(id, name);
            if (previous != null) {
                error("uses the id " + id + ", which is already used by the event \"" + previous + "\". "
                        + "Every event must have a unique id.");
                return null;
            }
            return id;
        }

        private String readDescription() {
            return firstChild(element, "description").map(XmlEventsLoader::text).orElse("");
        }

        private Integer readCommission() {
            Optional<Element> commissionElement = commissionElement();
            if (commissionElement.isEmpty()) {
                error("is missing its <" + COMMISSION_NAMES.get(0) + "> element.");
                return null;
            }
            String text = text(commissionElement.get());
            Integer commission = parseInteger(text);
            if (commission == null) {
                error("has the commission \"" + text + "\", which is not a whole number.");
                return null;
            }
            if (commission < MIN_COMMISSION || commission > MAX_COMMISSION) {
                error("has a commission of " + commission + " percent, but it must be between "
                        + MIN_COMMISSION + " and " + MAX_COMMISSION + ".");
                return null;
            }
            return commission;
        }

        private CommissionType readCommissionType() {
            Optional<Element> commissionElement = commissionElement();
            if (commissionElement.isEmpty()) {
                return null;
            }
            String type = commissionElement.get().getAttribute("type");
            Optional<CommissionType> parsed = CommissionType.parse(type);
            if (parsed.isEmpty()) {
                error("has the commission type \"" + type.trim() + "\", but only "
                        + CommissionType.legalValues() + " are allowed.");
                return null;
            }
            return parsed.get();
        }

        private Optional<Element> commissionElement() {
            return firstChildOfAny(element, COMMISSION_NAMES);
        }

        private List<EventOption> readOptions() {
            Optional<Element> optionsElement = firstChild(element, OPTIONS);
            if (optionsElement.isEmpty()) {
                error("is missing its <" + OPTIONS + "> element.");
                return List.of();
            }
            List<Element> optionElements = children(optionsElement.get(), OPTION);
            if (optionElements.size() != REQUIRED_OPTIONS) {
                error("has " + optionElements.size() + " options, but every event must have exactly "
                        + REQUIRED_OPTIONS + ".");
                return List.of();
            }
            List<EventOption> options = new ArrayList<>();
            for (Element optionElement : optionElements) {
                String optionName = text(optionElement);
                if (optionName.isEmpty()) {
                    error("has an option with an empty name.");
                    return List.of();
                }
                options.add(new EventOption(optionName));
            }
            return options;
        }

        /** Either an LMSR method or an order book method, and exactly one of the two. */
        private TradingMethod readMethod(int optionCount) {
            Optional<Element> method = firstChild(element, METHOD);
            if (method.isEmpty()) {
                error("is missing its <" + METHOD + "> element.");
                return null;
            }
            Optional<Element> lmsr = firstChild(method.get(), LMSR);
            Optional<Element> book = firstChild(method.get(), ORDER_BOOK);

            if (lmsr.isPresent() && book.isPresent()) {
                error("declares both a <" + LMSR + "> and a <" + ORDER_BOOK
                        + "> method, but an event is traded in exactly one way.");
                return null;
            }
            if (lmsr.isPresent()) {
                return readLmsr(lmsr.get(), optionCount);
            }
            if (book.isPresent()) {
                return readOrderBook(book.get(), optionCount);
            }
            error("declares neither a <" + LMSR + "> nor a <" + ORDER_BOOK + "> method.");
            return null;
        }

        private TradingMethod readLmsr(Element lmsr, int optionCount) {
            Optional<Element> bElement = firstChild(lmsr, "b");
            if (bElement.isEmpty()) {
                error("is missing the liquidity value <b> of its LMSR method.");
                return null;
            }
            String text = text(bElement.get());
            Integer b = parseInteger(text);
            if (b == null) {
                error("has the liquidity value \"" + text + "\", which is not a whole number.");
                return null;
            }
            if (b <= 0) {
                error("has a liquidity value of " + b + ", but it must be a positive number.");
                return null;
            }
            return new LmsrMethod(b, optionCount);
        }

        private TradingMethod readOrderBook(Element book, int optionCount) {
            Integer d = readIntegerAttribute(book, List.of("d"), "base value d");
            Integer initial = readIntegerAttribute(book, INITIAL_ATTRIBUTES, "initial investment");
            Boolean allowMint = readBooleanAttribute(book);

            if (d == null || initial == null || allowMint == null) {
                return null;
            }
            if (d <= 0) {
                error("has a base value d of " + d + ", but it must be a positive number.");
                return null;
            }
            if (initial < 0) {
                error("has an initial investment of " + initial + ", but it cannot be negative.");
                return null;
            }
            if (initial % d != 0) {
                error("has an initial investment of " + initial + ", which does not divide into whole pairs "
                        + "of shares at a base value of " + d + ".");
                return null;
            }
            return new OrderBookMethod(d, initial, allowMint, optionCount);
        }

        private Integer readIntegerAttribute(Element element, List<String> names, String description) {
            for (String name : names) {
                if (element.hasAttribute(name)) {
                    String text = element.getAttribute(name).trim();
                    Integer value = parseInteger(text);
                    if (value == null) {
                        error("has the " + description + " \"" + text + "\", which is not a whole number.");
                    }
                    return value;
                }
            }
            error("is missing the " + description + " (\"" + names.get(0) + "\") of its order book.");
            return null;
        }

        private Boolean readBooleanAttribute(Element element) {
            if (!element.hasAttribute("allow-mint")) {
                error("is missing the \"allow-mint\" setting of its order book.");
                return null;
            }
            String text = element.getAttribute("allow-mint").trim();
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
            error("has \"" + text + "\" as its allow-mint setting, but only true or false are allowed.");
            return null;
        }

        private void error(String problem) {
            String title = name.isEmpty() ? "" : " (\"" + name + "\")";
            errors.add("Event number " + position + title + " " + problem);
        }
    }

    /** Reads and validates one user element, together with the events it carries. */
    private static final class UserReader {

        private final Element element;
        private final int position;
        private final Map<String, String> namesSeen;
        private final List<String> errors;
        private final String name;

        private UserReader(Element element, int position, Map<String, String> namesSeen, List<String> errors) {
            this.element = element;
            this.position = position;
            this.namesSeen = namesSeen;
            this.errors = errors;
            this.name = element.getAttribute("name").trim();
        }

        private ReadUser read() {
            int errorsBefore = errors.size();

            if (name.isEmpty()) {
                error("has an empty name attribute.");
            } else {
                String previous = namesSeen.putIfAbsent(name.toLowerCase(Locale.US), name);
                if (previous != null) {
                    error("has the same name as an earlier user (\"" + previous
                            + "\"). Every user must have a name of its own.");
                }
            }
            Integer cash = readInitialCash();
            List<Integer> marketMakerOf = readMarketMakerEvents();

            boolean sound = errors.size() == errorsBefore && cash != null;
            return new ReadUser(sound ? new User(name, cash) : null, name, marketMakerOf);
        }

        private Integer readInitialCash() {
            Optional<Element> cashElement = firstChild(element, INITIAL_CASH);
            if (cashElement.isEmpty()) {
                error("is missing its <" + INITIAL_CASH + "> element.");
                return null;
            }
            String text = text(cashElement.get());
            Integer cash = parseInteger(text);
            if (cash == null) {
                error("has the initial cash \"" + text + "\", which is not a whole number.");
                return null;
            }
            if (cash <= 0) {
                error("starts with " + cash + " in the account, but every user must start with more than 0.");
                return null;
            }
            return cash;
        }

        private List<Integer> readMarketMakerEvents() {
            Optional<Element> marketMaker = firstChildOfAny(element, MARKET_MAKER_NAMES);
            if (marketMaker.isEmpty()) {
                return List.of();
            }
            List<Integer> eventIds = new ArrayList<>();
            for (Element event : children(marketMaker.get(), "event")) {
                String text = event.getAttribute("id").trim();
                Integer id = parseInteger(text);
                if (id == null) {
                    error("is set as the market maker of the event \"" + text
                            + "\", which is not a whole number.");
                    continue;
                }
                if (eventIds.contains(id)) {
                    error("is set as the market maker of event " + id + " more than once.");
                    continue;
                }
                eventIds.add(id);
            }
            return eventIds;
        }

        private void error(String problem) {
            String title = name.isEmpty() ? "" : " (\"" + name + "\")";
            errors.add("User number " + position + title + " " + problem);
        }
    }

    /**
     * A user as the file described them: the user themselves when the description
     * was sound, the name it gave whether or not it was, and the events it says
     * they carry.
     */
    private record ReadUser(User user, String name, List<Integer> marketMakerOf) {
    }

    private static Integer parseInteger(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Optional<Element> firstChild(Element parent, String name) {
        List<Element> found = children(parent, name);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private static Optional<Element> firstChildOfAny(Element parent, List<String> names) {
        for (String name : names) {
            Optional<Element> found = firstChild(parent, name);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                found.add((Element) node);
            }
        }
        return found;
    }

    private static String text(Element element) {
        String content = element.getTextContent();
        return content == null ? "" : content.trim();
    }
}
