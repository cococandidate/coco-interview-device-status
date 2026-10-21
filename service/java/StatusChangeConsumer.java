import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.function.Function;

// Parses each delivery, hands it to the worker for its serial, and acks or nacks once the worker is done.
// Each serial always maps to the same single-threaded worker, so one robot's changes are applied in the order
// they arrived while different robots proceed in parallel. HangryHippo takes up to ~850ms a write and Fleet
// publishes faster than one write at a time can keep up with.
final class StatusChangeConsumer {

    private final Channel channel;
    private final AvailabilitySync sync;
    private final Function<String, Executor> workerForSerial;

    StatusChangeConsumer(Channel channel, AvailabilitySync sync, Function<String, Executor> workerForSerial) {
        this.channel = channel;
        this.sync = sync;
        this.workerForSerial = workerForSerial;
    }

    void handle(Delivery delivery) {
        long tag = delivery.getEnvelope().getDeliveryTag();
        String messageId = delivery.getProperties().getMessageId();

        StatusChange change;
        try {
            change = StatusChange.parse(messageId, new String(delivery.getBody(), StandardCharsets.UTF_8));
        } catch (StatusChange.InvalidChange e) {
            System.out.printf("outcome=INVALID change=%s error=\"%s\"%n", messageId, e.getMessage());
            settle(tag, Settle.DEAD_LETTER);
            return;
        }

        sync.noteReceived(change);
        workerForSerial.apply(change.serial()).execute(() -> settle(tag, process(change)));
    }

    private Settle process(StatusChange change) {
        try {
            return switch (sync.apply(change)) {
                case WRITTEN, STALE, SUPERSEDED, NOT_REGISTERED -> Settle.ACK;
                case RETRY_LATER -> Settle.REQUEUE;
                case REJECTED -> Settle.DEAD_LETTER;
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Settle.REQUEUE;
        } catch (RuntimeException e) {
            // A bug, not a condition we planned for. Dead-letter it rather than requeue it into a loop.
            System.out.printf("outcome=ERROR change=%s serial=%s%n", change.messageId(), change.serial());
            e.printStackTrace();
            return Settle.DEAD_LETTER;
        }
    }

    private enum Settle { ACK, REQUEUE, DEAD_LETTER }

    private void settle(long tag, Settle settle) {
        // Workers settle from their own threads; serialize access to the shared channel.
        synchronized (channel) {
            try {
                switch (settle) {
                    case ACK -> channel.basicAck(tag, false);
                    case REQUEUE -> channel.basicNack(tag, false, true);
                    case DEAD_LETTER -> channel.basicNack(tag, false, false);
                }
            } catch (IOException e) {
                // The channel is gone. The broker redelivers anything unacked, and the staleness check absorbs it.
                System.out.printf("outcome=SETTLE_FAILED tag=%d settle=%s error=\"%s\"%n", tag, settle, e.getMessage());
            }
        }
    }
}
