package market.engine.dto;

import java.util.List;

/** Everything shown about a single user: the money, the standing, and where that user takes part. */
public record UserDetailsDto(String name,
                             double balance,
                             boolean blocked,
                             List<UserEventDto> events) {
}
