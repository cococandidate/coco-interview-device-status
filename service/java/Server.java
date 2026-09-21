import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {

    static final int PORT = Integer.parseInt(envOr("PORT", "3000"));
    static final String FLEET_URL = envOr("FLEET_URL", "http://localhost:4001");
    static final String PARTNER_URL = envOr("PARTNER_URL", "http://localhost:4002");
    static final String BUS_URL = envOr("BUS_URL", "http://localhost:4003");

    static final Pattern STATUS_CHANGE = Pattern.compile("^/v1/devices/([^/]+)/status$");

    record StatusChange(String status, List<String> limitingFactors, String observedAt) {
    }

    static void handleStatusChange(HttpExchange exchange, String serial) throws IOException {
        String changeId = exchange.getRequestHeaders().getFirst("X-Change-Id");
        StatusChange change = JSON.fromJson(readBody(exchange), StatusChange.class);

        System.out.printf("change=%s serial=%s status=%s factors=%s%n",
                changeId, serial, change.status(), change.limitingFactors());

        // TODO: the three steps in the README go here.

        sendJson(exchange, 200, Map.of("status", "ok"));
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(16));

        server.createContext("/", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            try {
                Matcher match = STATUS_CHANGE.matcher(path);
                if (method.equals("POST") && match.matches()) {
                    handleStatusChange(exchange, match.group(1));
                    return;
                }

                if (method.equals("GET") && path.equals("/health")) {
                    sendJson(exchange, 200, Map.of("status", "ok"));
                    return;
                }

                sendJson(exchange, 404, Map.of("error", "not found", "path", path));
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, Map.of("error", String.valueOf(e)));
            }
        });

        System.out.println("listening on :" + PORT);
        server.start();
    }

    // --- plumbing, nothing below here is part of the exercise ---

    static final Gson JSON = new Gson();

    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    record Response(int status, JsonElement body) {

        public <T> T as(Class<T> type) {
            return body == null ? null : JSON.fromJson(body, type);
        }

        public String string(String field) {
            return body.getAsJsonObject().get(field).getAsString();
        }
    }

    static final long DEFAULT_TIMEOUT_MS = 3000;

    static Response get(String url) throws IOException, InterruptedException {
        return get(url, DEFAULT_TIMEOUT_MS);
    }

    static Response get(String url, long timeoutMs) throws IOException, InterruptedException {
        return call("GET", url, null, timeoutMs);
    }

    static Response put(String url, Object body) throws IOException, InterruptedException {
        return put(url, body, DEFAULT_TIMEOUT_MS);
    }

    static Response put(String url, Object body, long timeoutMs) throws IOException, InterruptedException {
        return call("PUT", url, body, timeoutMs);
    }

    static Response post(String url, Object body) throws IOException, InterruptedException {
        return post(url, body, DEFAULT_TIMEOUT_MS);
    }

    static Response post(String url, Object body, long timeoutMs) throws IOException, InterruptedException {
        return call("POST", url, body, timeoutMs);
    }

    static Response call(String method, String url, Object body, long timeoutMs) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JSON.toJson(body));

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .method(method, publisher);

        if (body != null) {
            request.header("Content-Type", "application/json");
        }

        HttpResponse<String> res = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        String text = res.body();
        return new Response(res.statusCode(), text == null || text.isBlank() ? null : JSON.fromJson(text, JsonElement.class));
    }

    static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void sendJson(HttpExchange exchange, int code, Object body) throws IOException {
        byte[] bytes = JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
