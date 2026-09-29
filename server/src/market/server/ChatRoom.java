package market.server;

import java.util.ArrayList;
import java.util.List;

import market.dto.ChatLineDto;

/**
 * The chat every logged in user shares. It lives on the server only, and like
 * everything else there it is forgotten when the server stops.
 */
public final class ChatRoom {

    private static final int MAX_LENGTH = 500;

    private final List<ChatLineDto> lines = new ArrayList<>();

    public synchronized ChatLineDto say(String userName, String text) throws ApiException {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(400, "A message cannot be empty.");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new ApiException(400, "A message can be at most " + MAX_LENGTH + " characters long.");
        }
        ChatLineDto line = new ChatLineDto(lines.size() + 1, userName, trimmed, System.currentTimeMillis());
        lines.add(line);
        return line;
    }

    /** The lines after the given serial number, oldest first. */
    public synchronized List<ChatLineDto> after(int serial) {
        if (serial >= lines.size()) {
            return List.of();
        }
        return List.copyOf(lines.subList(Math.max(serial, 0), lines.size()));
    }
}
