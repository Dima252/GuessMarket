package market.engine.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

/**
 * Reads an events file of the exercise 3 schema and checks that it makes sense.
 * <p>
 * The exercise 3 format has no users - they log in by themselves now - and no
 * event ids: files come from many users, so events are told apart by their
 * names, which must be unique within the file and among the events the system
 * already holds. Files of the earlier formats are refused with a message saying
 * so. An event has two options or more.
 * <p>
 * The file is guaranteed to be schema valid but not necessarily application
 * valid, so every rule of the specification is checked here. All problems found
 * are collected and reported together, and the contents are handed over only
 * when the file is completely sound.
 * <p>
 * Where the appendix of the specification and the schema published with the
 * course disagree on the spelling of a name, both spellings are accepted: the
 * schema of exercise 1 really did write {@code comision} with one m, and the
 * appendix of exercise 2 prints {@code inital} where the real schema writes
 * {@code initial}.
 */
public final class XmlEventsLoader {

    private static final String ROOT = "Guess-Market";
    private static final String EVENTS = "GM-events";
    private static final String EVENT = "GM-event";
    private static final String OPTIONS = "GM-options";
    private static final String OPTION = "GM-option";
    private static final String METHOD = "GM-method";
    private static final String LMSR = "GM-LMSR";
    private static final String ORDER_BOOK = "GM-order-book";
    private static final String USERS = "GM-users";

    private static final List<String> COMMISSION_NAMES = List.of("commission", "comision");
    private static final List<String> INITIAL_ATTRIBUTES = List.of("initial", "inital");

    private static final int MIN_COMMISSION = 0;
    private static final int MAX_COMMISSION = 90;
    private static final int MIN_OPTIONS = 2;

    /**
     * Reads a file that arrives as a stream: the server hands over the uploaded
     * contents without ever writing them to disk.
     *
     * @param takenNames the names of the events already in the system, in lower case
     */
    public LoadOutcome load(InputStream contents, Set<String> takenNames) {
        if (contents == null) {
            return LoadOutcome.failed(List.of("No file was sent."));
        }
        try {
            return read(parse(contents), takenNames);
        } catch (SAXParseException e) {
            return LoadOutcome.failed(List.of("The file is not a valid XML document: " + e.getMessage()
                    + " (line " + e.getLineNumber() + ", column " + e.getColumnNumber() + ")."));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            return LoadOutcome.failed(List.of("The file could not be read: " + e.getMessage()));
        }
    }

    private Document parse(InputStream contents) throws ParserConfigurationException, SAXException, IOException {
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
        return builder.parse(contents);
    }

    private LoadOutcome read(Document document, Set<String> takenNames) {
        Element root = document.getDocumentElement();
        if (root == null || !ROOT.equals(root.getTagName())) {
            String found = root == null ? "nothing" : "<" + root.getTagName() + ">";
            return LoadOutcome.failed(List.of("The root element must be <" + ROOT + ">, but " + found + " was found."));
        }
        if (firstChild(root, USERS).isPresent()) {
            return LoadOutcome.failed(List.of("The file contains a <" + USERS + "> element, so it is written in "
                    + "the exercise 2 format. Only files of the exercise 3 format are accepted: users log in "
                    + "by themselves, and the one who uploads a file becomes the market maker of its events."));
        }

        Optional<Element> eventsElement = firstChild(root, EVENTS);
        if (eventsElement.isEmpty()) {
            return LoadOutcome.failed(List.of("The file does not contain a <" + EVENTS + "> element."));
        }
        List<Element> eventElements = children(eventsElement.get(), EVENT);
        if (eventElements.isEmpty()) {
            return LoadOutcome.failed(List.of("The file does not contain any <" + EVENT + "> element."));
        }

        List<String> errors = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        Map<String, String> namesSeen = new HashMap<>();

        for (int position = 0; position < eventElements.size(); position++) {
            new EventReader(eventElements.get(position), position + 1, namesSeen, takenNames, errors)
                    .read()
                    .ifPresent(events::add);
        }

        if (!errors.isEmpty()) {
            return LoadOutcome.failed(errors);
        }
        return LoadOutcome.loaded(events);
    }

    private static final class EventReader {

        private final Element element;
        private final int position;
        private final Map<String, String> namesSeen;
        private final Set<String> takenNames;
        private final List<String> errors;
        private final String name;

        private EventReader(Element element, int position, Map<String, String> namesSeen, Set<String> takenNames,
                            List<String> errors) {
            this.element = element;
            this.position = position;
            this.namesSeen = namesSeen;
            this.takenNames = takenNames;
            this.errors = errors;
            this.name = element.getAttribute("name").trim();
        }

        private Optional<Event> read() {
            int errorsBefore = errors.size();

            readName();
            refuseId();
            String description = readDescription();
            Integer commission = readCommission();
            CommissionType commissionType = readCommissionType();
            List<EventOption> options = readOptions();
            TradingMethod method = readMethod(options.size());

            if (errors.size() != errorsBefore || commission == null
                    || commissionType == null || method == null) {
                return Optional.empty();
            }
            return Optional.of(new Event(name, description, commission, commissionType, options, method));
        }

        /**
         * Names are what tell events apart now, so they must be unique: within the
         * file, and among the events already in the system. They are compared
         * without regard to case or to surrounding spaces.
         */
        private void readName() {
            if (name.isEmpty()) {
                error("has an empty name attribute.");
                return;
            }
            String key = name.toLowerCase(Locale.US);
            if (takenNames.contains(key)) {
                error("has the same name as an event that is already in the system. "
                        + "Every event must have a name of its own.");
                return;
            }
            String previous = namesSeen.putIfAbsent(key, name);
            if (previous != null) {
                error("has the same name as an earlier event in this file (\"" + previous + "\"). "
                        + "Every event must have a name of its own.");
            }
        }

        /** An event of the exercise 3 format has no id: it is the name that identifies it. */
        private void refuseId() {
            if (firstChild(element, "id").isPresent()) {
                error("has an <id> element, which belongs to the formats of exercises 1 and 2. In the "
                        + "exercise 3 format events have no id and are told apart by their names.");
            }
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
            if (optionElements.size() < MIN_OPTIONS) {
                error("has " + optionElements.size() + " option" + (optionElements.size() == 1 ? "" : "s")
                        + ", but every event must have at least " + MIN_OPTIONS + ".");
                return List.of();
            }
            List<EventOption> options = new ArrayList<>();
            Map<String, String> optionNamesSeen = new HashMap<>();
            for (Element optionElement : optionElements) {
                String optionName = text(optionElement);
                if (optionName.isEmpty()) {
                    error("has an option with an empty name.");
                    return List.of();
                }
                if (optionNamesSeen.putIfAbsent(optionName.toLowerCase(Locale.US), optionName) != null) {
                    error("has the option \"" + optionName + "\" twice. Every option must have a name of its own.");
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
