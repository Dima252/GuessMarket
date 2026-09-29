package market.server;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import market.engine.api.GuessMarketEngine;
import market.engine.api.GuessMarketEngineImpl;

/**
 * Creates what the whole server shares when the application starts - the engine,
 * the chat, and the table of who is logged in - and keeps that table current as
 * sessions end. Nothing is persisted: when the server stops, all of it is gone.
 */
@WebListener
public final class ServerContext implements ServletContextListener, HttpSessionListener {

    private static final String ENGINE = "guessMarket.engine";
    private static final String CHAT = "guessMarket.chat";
    private static final String SESSIONS = "guessMarket.sessions";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        context.setAttribute(ENGINE, new GuessMarketEngineImpl());
        context.setAttribute(CHAT, new ChatRoom());
        context.setAttribute(SESSIONS, new Sessions());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        sessions(event.getSession().getServletContext()).release(event.getSession());
    }

    public static GuessMarketEngine engine(ServletContext context) {
        return (GuessMarketEngine) context.getAttribute(ENGINE);
    }

    public static ChatRoom chat(ServletContext context) {
        return (ChatRoom) context.getAttribute(CHAT);
    }

    public static Sessions sessions(ServletContext context) {
        return (Sessions) context.getAttribute(SESSIONS);
    }
}
