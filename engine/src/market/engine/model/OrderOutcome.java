package market.engine.model;

import java.util.List;

/**
 * What became of an order that was handed to a book: the executions it caused,
 * and how much of it is still waiting there.
 * <p>
 * The remaining amount is read from the order itself rather than counted back out
 * of the executions, because a mint produces one line for each of the two buyers
 * and only one of them belongs to the order that has just arrived.
 */
public record OrderOutcome(List<Trade> executed, long remaining) {

    public OrderOutcome {
        executed = List.copyOf(executed);
    }
}
