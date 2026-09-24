import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class Server {

    static final String AMQP_URL = envOr("AMQP_URL", "amqp://guest:guest@localhost:5672/");
    static final String QUEUE = envOr("QUEUE", "device-status");
    static final String FLEET_URL = envOr("FLEET_URL", "http://localhost:4001");
    static final String PARTNER_URL = envOr("PARTNER_URL", "http://localhost:4002");

    record StatusChange(String serial, String status, String[] limitingFactors, String observedAt) {
    }

    static void handleStatusChange(Channel channel, Delivery delivery) throws IOException {
        String messageId = delivery.getProperties().getMessageId();
        StatusChange change = JSON.fromJson(
                new String(delivery.getBody(), StandardCharsets.UTF_8), StatusChange.class);

        System.out.printf("change=%s serial=%s status=%s factors=%s redelivered=%s%n",
                messageId, change.serial(), change.status(),
                String.join(",", change.limitingFactors()), delivery.getEnvelope().isRedeliver());

        // TODO: the steps in the README go here.

        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
    }

    public static void main(String[] args) throws Exception {
        Channel channel = connect();
        channel.basicQos(1);

        DeliverCallback onDelivery = (consumerTag, delivery) -> {
            try {
                handleStatusChange(channel, delivery);
            } catch (Exception e) {
                e.printStackTrace();
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            }
        };

        System.out.println("consuming " + QUEUE);
        channel.basicConsume(QUEUE, false, onDelivery, consumerTag -> {
        });
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
