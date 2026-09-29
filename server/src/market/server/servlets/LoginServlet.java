package market.server.servlets;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import market.dto.UserDetailsDto;
import market.engine.api.GuessMarketEngine;
import market.server.ApiException;
import market.server.ApiServlet;
import market.server.ServerContext;
import market.server.Sessions;

/**
 * Logs in by name alone - there are no passwords and no sign up. A new name
 * becomes a new user with an empty account. A name somebody is logged in under
 * right now is refused; a name whose session has ended logs back in to the same
 * account.
 * <p>
 * {@code POST /api/login}, parameter {@code name}; answers with the user's details.
 */
@WebServlet("/api/login")
public final class LoginServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    /** Clients poll every second, so a session silent for this long belongs to a client that is gone. */
    private static final int SESSION_SECONDS = 120;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req -> {
            String name = text(req, "name");
            if (name.isEmpty()) {
                throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Enter a name to log in with.");
            }
            HttpSession session = req.getSession(true);
            Object current = session.getAttribute(USER_ATTRIBUTE);
            if (current != null && !((String) current).equalsIgnoreCase(name)) {
                throw new ApiException(HttpServletResponse.SC_CONFLICT,
                        "This client is already logged in as " + current + ". Log out first.");
            }

            GuessMarketEngine engine = engine();
            Sessions sessions = ServerContext.sessions(getServletContext());
            // Checking the name and claiming it happen as one step, so that two
            // clients asking for the same new name at once cannot both get it.
            synchronized (sessions) {
                if (!sessions.claim(name, session)) {
                    throw new ApiException(HttpServletResponse.SC_CONFLICT,
                            "The name \"" + name + "\" is already taken. Choose another one.");
                }
                if (!engine.userExists(name)) {
                    engine.addUser(name);
                }
            }
            UserDetailsDto details = engine.userDetails(name);
            session.setAttribute(USER_ATTRIBUTE, details.name());
            session.setMaxInactiveInterval(SESSION_SECONDS);
            return details;
        });
    }
}
