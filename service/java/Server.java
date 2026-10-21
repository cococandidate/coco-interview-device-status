import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    static final String AMQP_URL = envOr("AMQP_URL", "amqp://guest:guest@localhost:5672/");
    static final String QUEUE = envOr("QUEUE", "device-status");
    static final String FLEET_URL = envOr("FLEET_URL", "http://localhost:4001");
    static final String PARTNER_URL = envOr("PARTNER_URL", "http://localhost:4002");

    // Writes are serialized per serial, so this bounds how many robots are written to at once.
    static final int WORKERS = Integer.parseInt(envOr("WORKERS", "16"));
    // Enough unacked deliveries to keep every worker busy and let a backlog for one robot coalesce.
    static final int PREFETCH = Integer.parseInt(envOr("PREFETCH", "64"));
    // HangryHippo's 204 typically takes 600 to 800ms. Past this, treat the write as failed and retry it.
    static final long PARTNER_TIMEOUT_MS = 2000;
    static final long FLEET_TIMEOUT_MS = 2000;

    static Optional<String> fleetVehicleId(String serial)
            throws AvailabilitySync.TransientFailure, AvailabilitySync.PermanentFailure, InterruptedException {
        Response res;
        try {
            res = get(FLEET_URL + "/v1/devices/" + encode(serial) + "/partner-accounts/hangry-hippo", FLEET_TIMEOUT_MS);
        } catch (IOException e) {
            throw new AvailabilitySync.TransientFailure("fleet: " + e);
        }
        if (res.status() == 200) {
            return Optional.of(res.string("vehicleId"));
        }
        if (res.status() == 404) {
            return Optional.empty();
        }
        if (res.status() >= 500 || res.status() == 429) {
            throw new AvailabilitySync.TransientFailure("fleet returned " + res.status());
        }
        throw new AvailabilitySync.PermanentFailure("fleet returned " + res.status());
    }

    static void setPartnerAvailability(String vehicleId, boolean available)
            throws AvailabilitySync.TransientFailure, AvailabilitySync.PermanentFailure, InterruptedException {
        Response res;
        try {
            res = put(PARTNER_URL + "/v1/vehicles/" + encode(vehicleId) + "/availability",
                    Map.of("available", available), PARTNER_TIMEOUT_MS);
        } catch (IOException e) {
            // Includes timeouts. HangryHippo may still record a write we gave up on; the retry writes the same value.
            throw new AvailabilitySync.TransientFailure("hangry-hippo: " + e);
        }
        if (res.status() >= 200 && res.status() < 300) {
            return;
        }
        if (res.status() >= 500 || res.status() == 429) {
            throw new AvailabilitySync.TransientFailure("hangry-hippo returned " + res.status());
        }
        throw new AvailabilitySync.PermanentFailure("hangry-hippo returned " + res.status());
    }

    public static void main(String[] args) throws Exception {
        Channel channel = connect();
        channel.basicQos(PREFETCH);

        AvailabilitySync sync = new AvailabilitySync(Server::fleetVehicleId, Server::setPartnerAvailability, Thread::sleep);

        ExecutorService[] workers = new ExecutorService[WORKERS];
        for (int i = 0; i < WORKERS; i++) {
            workers[i] = Executors.newSingleThreadExecutor();
        }
        StatusChangeConsumer consumer = new StatusChangeConsumer(channel, sync,
                serial -> workers[Math.floorMod(serial.hashCode(), WORKERS)]);

        DeliverCallback onDelivery = (consumerTag, delivery) -> consumer.handle(delivery);

        System.out.printf("consuming %s workers=%d prefetch=%d%n", QUEUE, WORKERS, PREFETCH);
        channel.basicConsume(QUEUE, false, onDelivery, consumerTag -> {
        });
    }

    static String encode(String pathSegment) {
        return URLEncoder.encode(pathSegment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // --- plumbing, nothing below here is part of the exercise ---

    static final Gson JSON = new Gson();

    static Channel connect() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(AMQP_URL);
        // This client reads a trailing slash as an empty vhost, unlike the others.
        if (factory.getVirtualHost().isEmpty()) {
            factory.setVirtualHost("/");
        }
        for (int attempt = 1; ; attempt++) {
            try {
                Connection conn = factory.newConnection();
                return conn.createChannel();
            } catch (Exception e) {
                if (attempt >= 30) {
                    throw e;
                }
                Thread.sleep(1000);
            }
        }
    }

    static final long DEFAULT_TIMEOUT_MS = 3000;

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
}
