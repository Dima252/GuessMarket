package market.server.servlets;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import market.server.ApiServlet;
import market.server.ServerContext;

/**
 * The chat every logged in user shares.
 * {@code GET /api/chat?after=} returns the lines after the serial the client
 * already has; {@code POST /api/chat}, parameter {@code text}, adds one.
 */
@WebServlet("/api/chat")
public final class ChatServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req -> {
            currentUser(req);
            return ServerContext.chat(getServletContext()).after(optionalInteger(req, "after"));
        });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req ->
                ServerContext.chat(getServletContext()).say(currentUser(req), text(req, "text")));
    }
}
