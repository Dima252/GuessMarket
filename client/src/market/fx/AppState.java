package market.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import market.dto.AccountEntryDto;
import market.dto.ChatLineDto;
import market.dto.EventStateDto;
import market.dto.EventSummaryDto;
import market.dto.LedgerDto;
import market.dto.UserDetailsDto;
import market.dto.UserSummaryDto;
import market.fx.net.ServerApi;

/**
 * What the screens share: the server, the user logged in, and the latest picture
 * of the system, kept current by pulling it from the server once a second.
 * <p>
 * Each thing polled is a {@link Feed}, which passes a value on only when it has
 * changed, so screens redraw only what actually moved. Every screen reads the
 * system through this one object - in exercise 2 it held the engine itself, and
 * the screens did not have to change much when a server took its place.
 * <p>
 * A request that is still on its way is never sent again on the next tick, so a
 * slow server gets fewer requests rather than a growing queue of them.
 */
public final class AppState {

    /** The spec allows up to two seconds between pulls; one keeps the screens lively. */
    private static final Duration POLL_INTERVAL = Duration.seconds(1);

    private final ServerApi api;
    private final String userName;

    private final Feed<List<EventSummaryDto>> events = new Feed<>();
    private final Feed<List<UserSummaryDto>> users = new Feed<>();
    private final Feed<UserDetailsDto> me = new Feed<>();
    private final Feed<List<AccountEntryDto>> ledger = new Feed<>();
    private final Feed<List<ChatLineDto>> chat = new Feed<>();
    private final Feed<Boolean> reachable = new Feed<>();
    private final Map<String, WatchedEvent> watched = new HashMap<>();

    private final Set<String> inFlight = new HashSet<>();
    private final List<AccountEntryDto> ledgerLines = new ArrayList<>();
    private final List<ChatLineDto> chatLines = new ArrayList<>();
    private int ledgerSerial;
    private int chatSerial;
    private Timeline timeline;

    /** An event one of the screens is showing, and the feed of its state. */
    private static final class WatchedEvent {
        private final Feed<EventStateDto> feed = new Feed<>();
        private Integer eventId;
    }

    public AppState(ServerApi api, String userName) {
        this.api = api;
        this.userName = userName;
        api.onReachable(reachable::publish);
    }

    public ServerApi api() {
        return api;
    }

    public String userName() {
        return userName;
    }

    public Feed<List<EventSummaryDto>> events() {
        return events;
    }

    public Feed<List<UserSummaryDto>> users() {
        return users;
    }

    public Feed<UserDetailsDto> me() {
        return me;
    }

    /** Every line of the user's ledger so far, newest first. */
    public Feed<List<AccountEntryDto>> ledger() {
        return ledger;
    }

    /** Every chat line so far, oldest first. */
    public Feed<List<ChatLineDto>> chat() {
        return chat;
    }

    public Feed<Boolean> reachable() {
        return reachable;
    }

    /**
     * The feed of the event a screen is showing. Each screen has a key of its own,
     * so the events tab and the account tab can show different events at once.
     */
    public Feed<EventStateDto> eventFeed(String screen) {
        return watched.computeIfAbsent(screen, key -> new WatchedEvent()).feed;
    }

    /** Which event a screen is showing now, or {@code null} for none. It is fetched at once. */
    public void watch(String screen, Integer eventId) {
        WatchedEvent entry = watched.computeIfAbsent(screen, key -> new WatchedEvent());
        if (eventId != null && eventId.equals(entry.eventId)) {
            return;
        }
        entry.eventId = eventId;
        entry.feed.reset();
        if (eventId != null) {
            fetchEvent(screen, entry);
        }
    }

    // ----------------------------------------------------------------- polling

    public void start() {
        timeline = new Timeline(new KeyFrame(POLL_INTERVAL, tick -> poll()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        poll();
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
        }
    }

    /** Pulls everything now, rather than on the next tick - used right after the user acted. */
    public void poll() {
        fetch("events", api::events, events::publish);
        fetch("users", api::users, users::publish);
        fetch("me", api::me, me::publish);
        fetch("ledger", (onSuccess, onError) -> api.ledger(ledgerSerial, onSuccess, onError), (LedgerDto fresh) -> {
            ledgerSerial = fresh.lastSerial();
            if (!fresh.entries().isEmpty()) {
                ledgerLines.addAll(0, fresh.entries().reversed());
            }
            ledger.publish(List.copyOf(ledgerLines));
        });
        fetch("chat", (onSuccess, onError) -> api.chat(chatSerial, onSuccess, onError), (List<ChatLineDto> fresh) -> {
            if (!fresh.isEmpty()) {
                chatSerial = fresh.getLast().serial();
                chatLines.addAll(fresh);
            }
            chat.publish(List.copyOf(chatLines));
        });
        watched.forEach(this::fetchEvent);
    }

    private void fetchEvent(String screen, WatchedEvent entry) {
        Integer eventId = entry.eventId;
        if (eventId == null) {
            return;
        }
        fetch("event:" + screen, (onSuccess, onError) -> api.event(eventId, onSuccess, onError), (EventStateDto state) -> {
            // The screen may have moved on to another event while this was on its way.
            if (eventId.equals(entry.eventId)) {
                entry.feed.publish(state);
            }
        });
    }

    /**
     * Sends one request unless the same one is still on its way. Failures are not
     * reported one by one - the {@link #reachable()} feed says when the server is
     * gone, and the next tick simply tries again.
     */
    private <T> void fetch(String key, BiConsumer<Consumer<T>, Consumer<String>> call, Consumer<T> onResult) {
        if (!inFlight.add(key)) {
            return;
        }
        call.accept(result -> {
            inFlight.remove(key);
            onResult.accept(result);
        }, problem -> inFlight.remove(key));
    }
}
