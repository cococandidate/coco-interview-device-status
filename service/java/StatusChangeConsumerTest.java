import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusChangeConsumerTest {

    // A Channel that records acks and nacks and rejects every other call, so a test fails loudly if the consumer uses more of it.
    static Channel recordingChannel(List<String> settled) {
        return (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[]{Channel.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "basicAck" -> settled.add("ack " + args[0]);
                        case "basicNack" -> settled.add(((Boolean) args[2] ? "requeue " : "dead-letter ") + args[0]);
                        default -> throw new UnsupportedOperationException("fake channel does not support " + method.getName());
                    }
                    return null;
                });
    }

    static Delivery delivery(String body, long deliveryTag) {
        Envelope envelope = new Envelope(deliveryTag, false, "fleet", "device.status.changed");
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().messageId("chg_test").build();
        return new Delivery(envelope, properties, body.getBytes(StandardCharsets.UTF_8));
    }

    static final String ONLINE =
            "{\"serial\":\"C10393\",\"status\":\"ONLINE\",\"limitingFactors\":[],\"observedAt\":\"2026-09-24T17:02:11.482Z\"}";

    final List<String> settled = new ArrayList<>();

    // Runs each change on the calling thread, so settling has happened by the time handle returns.
    StatusChangeConsumer consumer(AvailabilitySync.HangryHippo hangryHippo) {
        AvailabilitySync sync = new AvailabilitySync(serial -> Optional.of("veh_8f21c4"), hangryHippo, millis -> {
        });
        return new StatusChangeConsumer(recordingChannel(settled), sync, serial -> Runnable::run);
    }

    @Test
    @DisplayName("given a well-formed status change, when HangryHippo records it, then the message is acked")
    void writtenChangeIsAcked() {
        consumer((vehicleId, available) -> {
        }).handle(delivery(ONLINE, 7));

        assertEquals(List.of("ack 7"), settled);
    }

    @Test
    @DisplayName("given a message we cannot read, when it is handled, then it is dead-lettered")
    void unreadableChangeIsDeadLettered() {
        consumer((vehicleId, available) -> {
        }).handle(delivery("{\"serial\":\"C10393\"}", 7));

        assertEquals(List.of("dead-letter 7"), settled);
    }

    @Test
    @DisplayName("given HangryHippo keeps shedding load, when a change is handled, then it is requeued")
    void unwrittenChangeIsRequeued() {
        consumer((vehicleId, available) -> {
            throw new AvailabilitySync.TransientFailure("503");
        }).handle(delivery(ONLINE, 7));

        assertEquals(List.of("requeue 7"), settled);
    }

    @Test
    @DisplayName("given HangryHippo does not know the vehicle, when a change is handled, then it is dead-lettered")
    void rejectedChangeIsDeadLettered() {
        consumer((vehicleId, available) -> {
            throw new AvailabilitySync.PermanentFailure("404");
        }).handle(delivery(ONLINE, 7));

        assertEquals(List.of("dead-letter 7"), settled);
    }

    @Test
    @DisplayName("given an unexpected error while applying a change, when it is handled, then it is dead-lettered rather than looped")
    void bugIsDeadLettered() {
        consumer((vehicleId, available) -> {
            throw new IllegalStateException("boom");
        }).handle(delivery(ONLINE, 7));

        assertEquals(List.of("dead-letter 7"), settled);
    }

    @Test
    @DisplayName("given a duplicate of a change already written, when it is handled, then it is acked without a second write")
    void duplicateIsAcked() {
        List<Boolean> writes = new ArrayList<>();
        StatusChangeConsumer consumer = consumer((vehicleId, available) -> writes.add(available));

        consumer.handle(delivery(ONLINE, 7));
        consumer.handle(delivery(ONLINE, 8));

        assertEquals(List.of("ack 7", "ack 8"), settled);
        assertEquals(List.of(true), writes);
    }
}
