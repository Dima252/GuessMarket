package market.engine.dto;

import java.util.List;

/**
 * The outcome of a load attempt. On failure it carries every problem that was
 * found in the file, so that the user can fix them all in one go.
 */
public record LoadReportDto(boolean success, int eventsLoaded, List<String> errors) {

    public static LoadReportDto success(int eventsLoaded) {
        return new LoadReportDto(true, eventsLoaded, List.of());
    }

    public static LoadReportDto failure(List<String> errors) {
        return new LoadReportDto(false, 0, List.copyOf(errors));
    }
}
