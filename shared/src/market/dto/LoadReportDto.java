package market.dto;

import java.util.List;

/**
 * What came of an uploaded file: the names of the events it added, or every
 * problem found in it - in which case it added nothing at all.
 */
public record LoadReportDto(boolean success, List<String> eventNames, List<String> errors) {

    public static LoadReportDto success(List<String> eventNames) {
        return new LoadReportDto(true, List.copyOf(eventNames), List.of());
    }

    public static LoadReportDto failure(List<String> errors) {
        return new LoadReportDto(false, List.of(), List.copyOf(errors));
    }

    public int eventsLoaded() {
        return eventNames.size();
    }
}
