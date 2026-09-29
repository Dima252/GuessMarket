package market.server;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

/**
 * Who is logged in right now, and through which session.
 * <p>
 * A name belongs to one session at a time: while it is held, nobody else may
 * log in under it. Once that session ends - the user logs out, the client is
 * closed, or it stops polling and the session times out - the name can log in
 * again and finds the same account, holdings and events it left.
 */
public final class Sessions {

    private final Map<String, HttpSession> byName = new HashMap<>();

    /** Claims the name for the session, unless another live session already holds it. */
    public synchronized boolean claim(String userName, HttpSession session) {
        String key = key(userName);
        HttpSession holder = byName.get(key);
        if (holder != null && holder != session && isAlive(holder)) {
            return false;
        }
        byName.put(key, session);
        return true;
    }

    public synchronized void release(HttpSession session) {
        byName.values().removeIf(held -> held == session);
    }

    private static boolean isAlive(HttpSession session) {
        try {
            session.getCreationTime();
            return true;
        } catch (IllegalStateException invalidated) {
            return false;
        }
    }

    private static String key(String userName) {
        return userName.trim().toLowerCase(Locale.US);
    }
}
