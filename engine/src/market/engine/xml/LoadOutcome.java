package market.engine.xml;

import java.util.List;

import market.engine.model.Event;
import market.engine.model.User;

/**
 * The raw result of reading a file: either everything it describes, or every
 * problem that was found in it. The contents are only handed over when the list
 * of errors is empty, so that a faulty file can never replace loaded data.
 */
public record LoadOutcome(List<Event> events, List<User> users, List<String> errors) {

    public static LoadOutcome loaded(List<Event> events, List<User> users) {
        return new LoadOutcome(List.copyOf(events), List.copyOf(users), List.of());
    }

    public static LoadOutcome failed(List<String> errors) {
        return new LoadOutcome(List.of(), List.of(), List.copyOf(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
