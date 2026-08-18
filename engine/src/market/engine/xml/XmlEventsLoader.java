package market.engine.xml;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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

/**
 * Reads an events file of the exercise 1 schema and checks that it makes sense.
 * <p>
 * The file is guaranteed to be schema valid but not necessarily application
 * valid, so every rule of the specification is checked here. All problems found
 * are collected and reported together, and the events are handed over only when
 * the file is completely sound.
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

    /** The schema spells it with a single m; the table in the specification uses two. Both are accepted. */
    private static final List<String> COMMISSION_NAMES = List.of("comision", "commission");

    private static final int MIN_COMMISSION = 0;
    private static final int MAX_COMMISSION = 90;
    private static final int REQUIRED_OPTIONS = 2;

    public LoadOutcome load(String path) {
        List<String> pathProblems = validatePath(path);
        if (!pathProblems.isEmpty()) {
            return LoadOutcome.failed(pathProblems);
        }
        try {
            Document document = parse(new File(path.trim()));
            return readEvents(document);
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

    private LoadOutcome readEvents(Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !ROOT.equals(root.getTagName())) {
            String found = root == null ? "nothing" : "<" + root.getTagName() + ">";
            return LoadOutcome.failed(List.of("The root element must be <" + ROOT + ">, but " + found + " was found."));
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
        Map<Integer, String> idsSeen = new HashMap<>();

        for (int position = 0; position < eventElements.size(); position++) {
            new EventReader(eventElements.get(position), position + 1, idsSeen, errors)
                    .read()
                    .ifPresent(events::add);
        }
        return errors.isEmpty() ? LoadOutcome.loaded(events) : LoadOutcome.failed(errors);
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
            String attribute = element.getAttribute("name").trim();
            this.name = attribute.isEmpty() ? "" : attribute;
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
            Integer b = readLiquidity();

            if (errors.size() != errorsBefore || id == null || commission == null
                    || commissionType == null || b == null) {
                return Optional.empty();
            }
            return Optional.of(new Event(
                    id, name, description, commission, commissionType, options, b));
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
            Optional<CommissionType> parsed =
                    CommissionType.parse(type);
            if (parsed.isEmpty()) {
                error("has the commission type \"" + type.trim() + "\", but only "
                        + CommissionType.legalValues() + " are allowed.");
                return null;
            }
            return parsed.get();
        }

        private Optional<Element> commissionElement() {
            for (String candidate : COMMISSION_NAMES) {
                Optional<Element> found = firstChild(element, candidate);
                if (found.isPresent()) {
                    return found;
                }
            }
            return Optional.empty();
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

        private Integer readLiquidity() {
            Optional<Element> method = firstChild(element, METHOD);
            if (method.isEmpty()) {
                error("is missing its <" + METHOD + "> element.");
                return null;
            }
            Optional<Element> lmsr = firstChild(method.get(), LMSR);
            if (lmsr.isEmpty()) {
                error("is missing its <" + LMSR + "> element. Exercise 1 supports LMSR events only.");
                return null;
            }
            Optional<Element> bElement = firstChild(lmsr.get(), "b");
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
            return b;
        }

        private void error(String problem) {
            String title = name.isEmpty() ? "" : " (\"" + name + "\")";
            errors.add("Event number " + position + title + " " + problem);
        }

        private static Integer parseInteger(String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private static Optional<Element> firstChild(Element parent, String name) {
        List<Element> found = children(parent, name);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
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
