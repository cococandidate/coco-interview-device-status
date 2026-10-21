import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Writes one status change to HangryHippo, unless a newer change for the same robot makes it pointless.
// Callers must not apply two changes for the same serial at once; StatusChangeConsumer serializes them.
final class AvailabilitySync {

    enum Outcome {
        // HangryHippo recorded the change.
        WRITTEN,
        // We already wrote this change, or a newer one, for this robot. Covers duplicates and late arrivals.
        STALE,
        // A newer change for this robot has been received and will be written instead.
        SUPERSEDED,
        // Fleet has no HangryHippo account for this robot, so there is nothing to write.
        NOT_REGISTERED,
        // Fleet or HangryHippo kept failing in a way that may clear up. Requeue.
        RETRY_LATER,
        // Fleet or HangryHippo refused it in a way retrying will not fix. Dead-letter.
        REJECTED,
    }

    // A failure worth retrying: a 5xx, a timeout, a dropped connection.
    static final class TransientFailure extends Exception {
        TransientFailure(String message) {
            super(message);
        }
    }

    // A failure retrying will not fix, such as HangryHippo not knowing the vehicle id Fleet gave us.
    static final class PermanentFailure extends Exception {
        PermanentFailure(String message) {
            super(message);
        }
    }

    interface Fleet {
        // HangryHippo's vehicle id for the serial, or empty if the robot is not registered with HangryHippo.
        Optional<String> vehicleId(String serial) throws TransientFailure, PermanentFailure, InterruptedException;
    }

    interface HangryHippo {
        void setAvailability(String vehicleId, boolean available) throws TransientFailure, PermanentFailure, InterruptedException;
    }

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    // HangryHippo's 503s come back in under 100ms, so a few quick retries usually get through. After these
    // the message is requeued, which frees the worker and tries again without holding the robot's slot forever.
    static final long[] BACKOFF_MS = {200, 400, 800, 1600};

    private final Fleet fleet;
    private final HangryHippo hangryHippo;
    private final Sleeper sleeper;

    // Both are in memory only, so a restart forgets them. See the PR for why that is acceptable for now.
    private final ConcurrentHashMap<String, Instant> latestReceived = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastWritten = new ConcurrentHashMap<>();

    AvailabilitySync(Fleet fleet, HangryHippo hangryHippo, Sleeper sleeper) {
        this.fleet = fleet;
        this.hangryHippo = hangryHippo;
        this.sleeper = sleeper;
    }

    // Called as each change arrives, before it waits its turn, so older changes queued behind it can be skipped.
    void noteReceived(StatusChange change) {
        latestReceived.merge(change.serial(), change.observedAt(), AvailabilitySync::later);
    }

    Outcome apply(StatusChange change) throws InterruptedException {
        boolean available = Availability.isAvailable(change.status(), change.limitingFactors());

        for (int attempt = 1; ; attempt++) {
            // Checked again before each retry, since a newer change may have arrived while we backed off.
            Outcome skip = skipReason(change);
            if (skip != null) {
                log(change, skip, "available=" + available);
                return skip;
            }

            String vehicleId = null;
            try {
                Optional<String> found = fleet.vehicleId(change.serial());
                if (found.isEmpty()) {
                    log(change, Outcome.NOT_REGISTERED, "available=" + available);
                    return Outcome.NOT_REGISTERED;
                }
                vehicleId = found.get();
                hangryHippo.setAvailability(vehicleId, available);
                lastWritten.merge(change.serial(), change.observedAt(), AvailabilitySync::later);
                log(change, Outcome.WRITTEN, "vehicle=" + vehicleId + " available=" + available + " attempts=" + attempt);
                return Outcome.WRITTEN;
            } catch (PermanentFailure e) {
                log(change, Outcome.REJECTED, "vehicle=" + vehicleId + " available=" + available + " error=\"" + e.getMessage() + "\"");
                return Outcome.REJECTED;
            } catch (TransientFailure e) {
                if (attempt > BACKOFF_MS.length) {
                    log(change, Outcome.RETRY_LATER, "vehicle=" + vehicleId + " available=" + available
                            + " attempts=" + attempt + " error=\"" + e.getMessage() + "\"");
                    return Outcome.RETRY_LATER;
                }
                System.out.printf("outcome=RETRYING change=%s serial=%s vehicle=%s attempt=%d error=\"%s\"%n",
                        change.messageId(), change.serial(), vehicleId, attempt, e.getMessage());
                sleeper.sleep(BACKOFF_MS[attempt - 1]);
            }
        }
    }

    private Outcome skipReason(StatusChange change) {
        Instant written = lastWritten.get(change.serial());
        if (written != null && !change.observedAt().isAfter(written)) {
            return Outcome.STALE;
        }
        Instant received = latestReceived.get(change.serial());
        if (received != null && received.isAfter(change.observedAt())) {
            return Outcome.SUPERSEDED;
        }
        return null;
    }

    private static void log(StatusChange change, Outcome outcome, String detail) {
        System.out.printf("outcome=%s change=%s serial=%s status=%s factors=%s observedAt=%s %s%n",
                outcome, change.messageId(), change.serial(), change.status(),
                String.join(",", change.limitingFactors()), change.observedAt(), detail);
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
