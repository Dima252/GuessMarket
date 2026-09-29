package market.fx.net;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 * Keeps the cookies the server sets - in practice only its session cookie - so
 * that every request after the login belongs to the same session. They live in
 * memory only, for as long as this client runs.
 */
final class SessionCookieJar implements CookieJar {

    private final List<Cookie> cookies = new ArrayList<>();

    @Override
    public synchronized void saveFromResponse(HttpUrl url, List<Cookie> received) {
        for (Cookie cookie : received) {
            cookies.removeIf(kept -> kept.name().equals(cookie.name()) && kept.domain().equals(cookie.domain())
                    && kept.path().equals(cookie.path()));
            cookies.add(cookie);
        }
    }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url) {
        long now = System.currentTimeMillis();
        cookies.removeIf(cookie -> cookie.expiresAt() < now);
        List<Cookie> matching = new ArrayList<>();
        for (Cookie cookie : cookies) {
            if (cookie.matches(url)) {
                matching.add(cookie);
            }
        }
        return matching;
    }
}
