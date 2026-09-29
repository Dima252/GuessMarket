package market.fx.net;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import javafx.application.Platform;
import market.dto.ChatLineDto;
import market.dto.CloseResultDto;
import market.dto.ErrorDto;
import market.dto.EventStateDto;
import market.dto.EventSummaryDto;
import market.dto.LedgerDto;
import market.dto.LoadReportDto;
import market.dto.OrderResultDto;
import market.dto.OrderSide;
import market.dto.PurchaseResultDto;
import market.dto.UserDetailsDto;
import market.dto.UserSummaryDto;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Everything the client asks of the server, and the only class that knows it is
 * talking over HTTP.
 * <p>
 * The server answers in JSON built from the shared DTO records, and every answer
 * is turned back into those same records here - so the screens work with exactly
 * the objects they worked with when the engine lived in the same process.
 * <p>
 * Every call is asynchronous: it goes out on OkHttp's own threads, and its
 * outcome is handed back on the JavaFX thread, either as the result or as a
 * message meant for the user as it is. Nothing here ever blocks the window,
 * except {@link #logoutAndWait()}, which is only used while the window closes.
 */
public final class ServerApi {

    /** The spec lets the client assume the server and the name of the WAR it was given. */
    public static final String BASE_URL = "http://localhost:8080/guess-market/api/";

    private static final MediaType XML = MediaType.parse("text/xml; charset=utf-8");
    private static final String UNREACHABLE = "The server cannot be reached. "
            + "Make sure Tomcat is running at localhost:8080 with guess-market.war deployed.";

    private final Gson gson = new Gson();
    private final OkHttpClient http = new OkHttpClient.Builder()
            .cookieJar(new SessionCookieJar())
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private Runnable onSessionLost = () -> { };
    private Consumer<Boolean> onReachable = reachable -> { };

    /** What to do when the server no longer knows this client - its session has ended. */
    public void onSessionLost(Runnable handler) {
        this.onSessionLost = handler;
    }

    /** Told, on the JavaFX thread, whether the last request reached the server at all. */
    public void onReachable(Consumer<Boolean> handler) {
        this.onReachable = handler;
    }

    /**
     * Lets the threads OkHttp keeps go. They are not daemon threads, so without
     * this the program would linger for a minute after its window has closed.
     */
    public void shutdown() {
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }

    // ------------------------------------------------------------------ login

    public void login(String name, Consumer<UserDetailsDto> onSuccess, Consumer<String> onError) {
        post("login", Map.of("name", name), UserDetailsDto.class, onSuccess, onError);
    }

    public void logout(Runnable onDone) {
        post("logout", Map.of(), Map.class, done -> onDone.run(), error -> onDone.run());
    }

    /** Logs out and waits a moment for it, for when the window is closing and nothing else will run. */
    public void logoutAndWait() {
        Request request = new Request.Builder().url(url("logout", Map.of()))
                .post(new FormBody.Builder().build()).build();
        try {
            // Only the session ending matters; there is nobody left to tell.
            http.newCall(request).execute().close();
        } catch (IOException ignored) {
            // The server is gone already, and with it the session.
        }
    }

    // ------------------------------------------------------------------- reads

    public void events(Consumer<List<EventSummaryDto>> onSuccess, Consumer<String> onError) {
        get("events", Map.of(), new TypeToken<List<EventSummaryDto>>() { }.getType(), onSuccess, onError);
    }

    public void event(int eventId, Consumer<EventStateDto> onSuccess, Consumer<String> onError) {
        get("event", Map.of("id", Integer.toString(eventId)), EventStateDto.class, onSuccess, onError);
    }

    public void users(Consumer<List<UserSummaryDto>> onSuccess, Consumer<String> onError) {
        get("users", Map.of(), new TypeToken<List<UserSummaryDto>>() { }.getType(), onSuccess, onError);
    }

    public void me(Consumer<UserDetailsDto> onSuccess, Consumer<String> onError) {
        get("me", Map.of(), UserDetailsDto.class, onSuccess, onError);
    }

    public void ledger(int afterSerial, Consumer<LedgerDto> onSuccess, Consumer<String> onError) {
        get("ledger", Map.of("after", Integer.toString(afterSerial)), LedgerDto.class, onSuccess, onError);
    }

    public void chat(int afterSerial, Consumer<List<ChatLineDto>> onSuccess, Consumer<String> onError) {
        get("chat", Map.of("after", Integer.toString(afterSerial)),
                new TypeToken<List<ChatLineDto>>() { }.getType(), onSuccess, onError);
    }

    // ----------------------------------------------------------------- actions

    public void deposit(String amount, Consumer<UserSummaryDto> onSuccess, Consumer<String> onError) {
        post("deposit", Map.of("amount", amount), UserSummaryDto.class, onSuccess, onError);
    }

    public void openEvent(int eventId, Consumer<EventStateDto> onSuccess, Consumer<String> onError) {
        post("event/open", Map.of("id", Integer.toString(eventId)), EventStateDto.class, onSuccess, onError);
    }

    public void closeEvent(int eventId, int winnerIndex, Consumer<CloseResultDto> onSuccess,
                           Consumer<String> onError) {
        post("event/close", Map.of("id", Integer.toString(eventId), "winner", Integer.toString(winnerIndex)),
                CloseResultDto.class, onSuccess, onError);
    }

    public void buy(int eventId, int optionIndex, long quantity, Consumer<PurchaseResultDto> onSuccess,
                    Consumer<String> onError) {
        post("lmsr/buy", Map.of("id", Integer.toString(eventId), "option", Integer.toString(optionIndex),
                "quantity", Long.toString(quantity)), PurchaseResultDto.class, onSuccess, onError);
    }

    public void placeOrder(int eventId, int optionIndex, OrderSide side, long quantity, String price,
                           Consumer<OrderResultDto> onSuccess, Consumer<String> onError) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("id", Integer.toString(eventId));
        form.put("option", Integer.toString(optionIndex));
        form.put("side", side.name());
        form.put("quantity", Long.toString(quantity));
        form.put("price", price);
        post("orderbook/order", form, OrderResultDto.class, onSuccess, onError);
    }

    public void say(String text, Consumer<ChatLineDto> onSuccess, Consumer<String> onError) {
        post("chat", Map.of("text", text), ChatLineDto.class, onSuccess, onError);
    }

    /**
     * Sends a file of events, waiting for the answer. It is meant to be called
     * from a JavaFX task, which is what keeps the window alive meanwhile.
     */
    public LoadReportDto uploadAndWait(File file) throws IOException {
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), RequestBody.create(file, XML))
                .build();
        Request request = new Request.Builder().url(url("upload", Map.of())).post(body).build();
        try (Response response = http.newCall(request).execute()) {
            String text = bodyOf(response);
            if (response.code() == 200) {
                return gson.fromJson(text, LoadReportDto.class);
            }
            if (response.code() == 401) {
                Platform.runLater(onSessionLost);
            }
            throw new IOException(errorMessage(response.code(), text));
        } catch (JsonParseException e) {
            throw new IOException("The server answered with something that could not be read.", e);
        }
    }

    // ------------------------------------------------------------------ plumbing

    private <T> void get(String path, Map<String, String> query, Type type,
                         Consumer<T> onSuccess, Consumer<String> onError) {
        send(new Request.Builder().url(url(path, query)).get().build(), type, onSuccess, onError);
    }

    private <T> void post(String path, Map<String, String> form, Type type,
                          Consumer<T> onSuccess, Consumer<String> onError) {
        FormBody.Builder body = new FormBody.Builder();
        form.forEach(body::add);
        send(new Request.Builder().url(url(path, Map.of())).post(body.build()).build(), type, onSuccess, onError);
    }

    private <T> void send(Request request, Type type, Consumer<T> onSuccess, Consumer<String> onError) {
        http.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Platform.runLater(() -> {
                    onReachable.accept(false);
                    onError.accept(UNREACHABLE);
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                Platform.runLater(() -> onReachable.accept(true));
                try (response) {
                    String text = bodyOf(response);
                    if (response.code() == 200) {
                        T result = gson.fromJson(text, type);
                        Platform.runLater(() -> onSuccess.accept(result));
                        return;
                    }
                    if (response.code() == 401) {
                        Platform.runLater(onSessionLost);
                    }
                    String message = errorMessage(response.code(), text);
                    Platform.runLater(() -> onError.accept(message));
                } catch (IOException | JsonParseException e) {
                    Platform.runLater(() -> onError.accept("The server answered with something that could "
                            + "not be read: " + e.getMessage()));
                }
            }
        });
    }

    private HttpUrl url(String path, Map<String, String> query) {
        HttpUrl base = HttpUrl.parse(BASE_URL + path);
        if (base == null) {
            throw new IllegalStateException("Not a URL: " + BASE_URL + path);
        }
        HttpUrl.Builder builder = base.newBuilder();
        query.forEach(builder::addQueryParameter);
        return builder.build();
    }

    private static String bodyOf(Response response) throws IOException {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    /** The server explains every refusal in an {@link ErrorDto}; anything else is its container talking. */
    private String errorMessage(int code, String text) {
        try {
            ErrorDto error = gson.fromJson(text, ErrorDto.class);
            if (error != null && error.message() != null) {
                return error.message();
            }
        } catch (JsonParseException notOurs) {
            // Fall through to the generic message.
        }
        return "The server refused the request (HTTP " + code + ").";
    }
}
