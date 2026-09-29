package market.server;

import java.io.IOException;

import com.google.gson.Gson;

import jakarta.servlet.http.HttpServletResponse;

/** The one Gson of the server, and the one way an answer is written. */
public final class Json {

    private static final Gson GSON = new Gson();

    private Json() {
    }

    public static void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(GSON.toJson(body));
    }
}
