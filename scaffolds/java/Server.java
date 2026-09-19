import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class Server {

    static final int PORT = Integer.parseInt(envOr("PORT", "3000"));
    static final String FLEET_URL = envOr("FLEET_URL", "http://localhost:4001");
    static final String PARTNER_URL = envOr("PARTNER_URL", "http://localhost:4002");
    static final String BUS_URL = envOr("BUS_URL", "http://localhost:4003");

    static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(16));

        server.createContext("/health", exchange ->
                sendJson(exchange, 200, "{\"status\":\"ok\"}"));

        server.createContext("/", exchange ->
                sendJson(exchange, 404, "{\"error\":\"not found\"}"));

        System.out.println("listening on :" + PORT);
        server.start();
    }

    static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void sendJson(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
