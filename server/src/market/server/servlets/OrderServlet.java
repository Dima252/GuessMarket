package market.server.servlets;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import market.dto.OrderSide;
import market.server.ApiException;
import market.server.ApiServlet;

/**
 * Places an order in the book of one option of an order book event.
 * {@code POST /api/orderbook/order}, parameters {@code id}, {@code option} (a zero based
 * index), {@code side} (buy or sell), {@code quantity} and {@code price}.
 */
@WebServlet("/api/orderbook/order")
public final class OrderServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, req -> {
            String user = currentUser(req);
            String sideText = text(req, "side");
            OrderSide side = OrderSide.parse(sideText).orElseThrow(() -> new ApiException(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "An order either buys or sells, but \"" + sideText + "\" was given."));
            return engine().placeOrder(user, integer(req, "id"), integer(req, "option"), side,
                    wholeNumber(req, "quantity"), number(req, "price"));
        });
    }
}
