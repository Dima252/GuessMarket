package market.engine.xml;

import java.util.List;

import market.engine.model.Event;

/**
 * The raw result of reading a file: either the events it describes, or every
 * problem that was found in it. The events are only handed over when the list
 * of errors is empty, so that a faulty file can never replace loaded data.
 */
public record LoadOutcome(List<Event> events, List<String> errors) {

    public static LoadOutcome loaded(List<Event> events) {
        return new LoadOutcome(List.copyOf(events), List.of());
    }

    public static LoadOutcome failed(List<String> errors) {
        return new LoadOutcome(List.of(), List.copyOf(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
