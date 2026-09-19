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
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    @SuppressWarnings("unchecked")
    static void handleStatusChange(HttpExchange exchange, String serial) throws IOException {
        String changeId = exchange.getRequestHeaders().getFirst("X-Change-Id");
        Map<String, Object> change = (Map<String, Object>) Json.parse(readBody(exchange));

        System.out.printf("change=%s serial=%s status=%s factors=%s%n",
                changeId, serial, change.get("status"), change.get("limitingFactors"));

        // TODO: the three steps in TASK.md go here.

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

    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    record Response(int status, Object body) {
    }

    static Response get(String url) throws IOException, InterruptedException {
        return call("GET", url, null);
    }

    static Response put(String url, Object body) throws IOException, InterruptedException {
        return call("PUT", url, body);
    }

    static Response post(String url, Object body) throws IOException, InterruptedException {
        return call("POST", url, body);
    }

    static Response call(String method, String url, Object body) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(Json.write(body));

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .method(method, publisher);

        if (body != null) {
            request.header("Content-Type", "application/json");
        }

        HttpResponse<String> res = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(res.statusCode(), Json.parse(res.body()));
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
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    static final class Json {
        private final String s;
        private int i;

        private Json(String s) {
            this.s = s;
        }

        static Object parse(String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            Json p = new Json(text);
            p.ws();
            return p.value();
        }

        private Object value() {
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    return object();
                case '[':
                    return array();
                case '"':
                    return string();
                case 't':
                    i += 4;
                    return Boolean.TRUE;
                case 'f':
                    i += 5;
                    return Boolean.FALSE;
                case 'n':
                    i += 4;
                    return null;
                default:
                    return number();
            }
        }

        private Map<String, Object> object() {
            Map<String, Object> out = new LinkedHashMap<>();
            i++;
            ws();
            if (s.charAt(i) == '}') {
                i++;
                return out;
            }
            while (true) {
                ws();
                String key = string();
                ws();
                i++;
                ws();
                out.put(key, value());
                ws();
                if (s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                i++;
                return out;
            }
        }

        private List<Object> array() {
            List<Object> out = new ArrayList<>();
            i++;
            ws();
            if (s.charAt(i) == ']') {
                i++;
                return out;
            }
            while (true) {
                ws();
                out.add(value());
                ws();
                if (s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                i++;
                return out;
            }
        }

        private String string() {
            StringBuilder b = new StringBuilder();
            i++;
            while (s.charAt(i) != '"') {
                char c = s.charAt(i++);
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> b.append(e);
                }
            }
            i++;
            return b.toString();
        }

        private Object number() {
            int start = i;
            while (i < s.length() && "+-.eE0123456789".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String n = s.substring(start, i);
            if (n.contains(".") || n.contains("e") || n.contains("E")) {
                return Double.parseDouble(n);
            }
            return Long.parseLong(n);
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        static String write(Object v) {
            StringBuilder b = new StringBuilder();
            writeTo(v, b);
            return b.toString();
        }

        private static void writeTo(Object v, StringBuilder b) {
            if (v == null) {
                b.append("null");
            } else if (v instanceof String str) {
                quote(str, b);
            } else if (v instanceof Boolean || v instanceof Number) {
                b.append(v);
            } else if (v instanceof Map<?, ?> m) {
                b.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) {
                        b.append(',');
                    }
                    first = false;
                    quote(String.valueOf(e.getKey()), b);
                    b.append(':');
                    writeTo(e.getValue(), b);
                }
                b.append('}');
            } else if (v instanceof Iterable<?> it) {
                b.append('[');
                boolean first = true;
                for (Object o : it) {
                    if (!first) {
                        b.append(',');
                    }
                    first = false;
                    writeTo(o, b);
                }
                b.append(']');
            } else {
                quote(String.valueOf(v), b);
            }
        }

        private static void quote(String s, StringBuilder b) {
            b.append('"');
            for (int j = 0; j < s.length(); j++) {
                char c = s.charAt(j);
                switch (c) {
                    case '"' -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    case '\n' -> b.append("\\n");
                    case '\r' -> b.append("\\r");
                    case '\t' -> b.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            b.append(String.format("\\u%04x", (int) c));
                        } else {
                            b.append(c);
                        }
                    }
                }
            }
            b.append('"');
        }
    }
}
