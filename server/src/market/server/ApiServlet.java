package market.server;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import market.dto.ErrorDto;
import market.engine.api.EngineException;
import market.engine.api.GuessMarketEngine;

/**
 * What every endpoint has in common: finding the engine, knowing who is logged
 * in, reading parameters, and answering in JSON.
 * <p>
 * Every answer is JSON. A request that succeeds gets 200 and the result; one that
 * is refused gets an {@link ErrorDto} whose message is meant for the user as it
 * is - 400 for a request that is malformed, 401 when nobody is logged in, and
 * 409 when the engine refuses it, in the engine's own words.
 * <p>
 * The acting user always comes from the session and never from a parameter, so
 * nobody can act in somebody else's name.
 */
public abstract class ApiServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String USER_ATTRIBUTE = "userName";

    @FunctionalInterface
    protected interface Handler {
        Object handle(HttpServletRequest request) throws ApiException, EngineException, IOException, ServletException;
    }

    protected final void respond(HttpServletRequest request, HttpServletResponse response, Handler handler)
            throws IOException {
        request.setCharacterEncoding("UTF-8");
        try {
            Json.write(response, HttpServletResponse.SC_OK, handler.handle(request));
        } catch (ApiException e) {
            Json.write(response, e.status(), new ErrorDto(e.getMessage()));
        } catch (EngineException e) {
            Json.write(response, HttpServletResponse.SC_CONFLICT, new ErrorDto(e.getMessage()));
        } catch (ServletException | IllegalStateException e) {
            Json.write(response, HttpServletResponse.SC_BAD_REQUEST,
                    new ErrorDto("The request could not be read: " + e.getMessage()));
        }
    }

    protected final GuessMarketEngine engine() {
        return ServerContext.engine(getServletContext());
    }

    /** The name of the user logged in through this session. */
    protected static String currentUser(HttpServletRequest request) throws ApiException {
        HttpSession session = request.getSession(false);
        Object name = session == null ? null : session.getAttribute(USER_ATTRIBUTE);
        if (name == null) {
            throw new ApiException(HttpServletResponse.SC_UNAUTHORIZED, "You are not logged in. Log in first.");
        }
        return (String) name;
    }

    protected static String text(HttpServletRequest request, String name) throws ApiException {
        String value = request.getParameter(name);
        if (value == null) {
            throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, "The parameter \"" + name + "\" is missing.");
        }
        return value.trim();
    }

    protected static int integer(HttpServletRequest request, String name) throws ApiException {
        String value = text(request, name);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw notANumber(name, value, "a whole number");
        }
    }

    protected static long wholeNumber(HttpServletRequest request, String name) throws ApiException {
        String value = text(request, name);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw notANumber(name, value, "a whole number");
        }
    }

    protected static double number(HttpServletRequest request, String name) throws ApiException {
        String value = text(request, name);
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                throw notANumber(name, value, "a number");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw notANumber(name, value, "a number");
        }
    }

    /** An optional whole number, such as the serial a poll asks from; zero when absent. */
    protected static int optionalInteger(HttpServletRequest request, String name) throws ApiException {
        return request.getParameter(name) == null ? 0 : integer(request, name);
    }

    private static ApiException notANumber(String name, String value, String expected) {
        return new ApiException(HttpServletResponse.SC_BAD_REQUEST,
                "The parameter \"" + name + "\" must be " + expected + ", but \"" + value + "\" was given.");
    }
}
