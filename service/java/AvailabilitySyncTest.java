import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AvailabilitySyncTest {

    static final Instant T0 = Instant.parse("2026-09-24T17:02:11.482Z");

    // Fleet maps C10393 to veh_8f21c4 and knows no other robot.
    static final AvailabilitySync.Fleet FLEET = serial -> Optional.ofNullable(Map.of("C10393", "veh_8f21c4").get(serial));

    // A HangryHippo that records successful writes and fails with whatever is queued in `failures` first.
    static final class FakeHangryHippo implements AvailabilitySync.HangryHippo {
        final List<String> writes = new ArrayList<>();
        final Deque<Exception> failures = new ArrayDeque<>();
        Runnable onWrite = () -> {
        };

        @Override
        public void setAvailability(String vehicleId, boolean available)
                throws AvailabilitySync.TransientFailure, AvailabilitySync.PermanentFailure {
            onWrite.run();
            Exception failure = failures.poll();
            if (failure instanceof AvailabilitySync.TransientFailure t) {
                throw t;
            }
            if (failure instanceof AvailabilitySync.PermanentFailure p) {
                throw p;
            }
            writes.add(vehicleId + "=" + available);
        }
    }

    final FakeHangryHippo hangryHippo = new FakeHangryHippo();
    final List<Long> sleeps = new ArrayList<>();
    final AvailabilitySync sync = new AvailabilitySync(FLEET, hangryHippo, sleeps::add);

    static StatusChange change(String status, List<String> factors, Instant observedAt) {
        return new StatusChange("chg_test", "C10393", status, factors, observedAt);
    }

    // Arrives and is applied straight away, as the consumer does when nothing else is queued for the robot.
    AvailabilitySync.Outcome receiveAndApply(StatusChange change) throws InterruptedException {
        sync.noteReceived(change);
        return sync.apply(change);
    }

    @Test
    @DisplayName("given an available robot, when its change is applied, then HangryHippo is told it is available")
    void writesAvailable() throws Exception {
        assertEquals(AvailabilitySync.Outcome.WRITTEN, receiveAndApply(change("ONLINE", List.of(), T0)));
        assertEquals(List.of("veh_8f21c4=true"), hangryHippo.writes);
    }

    @Test
    @DisplayName("given a robot with a blocking factor, when its change is applied, then HangryHippo is told it is unavailable")
    void writesUnavailable() throws Exception {
        assertEquals(AvailabilitySync.Outcome.WRITTEN, receiveAndApply(change("ONLINE", List.of("MAINTENANCE"), T0)));
        assertEquals(List.of("veh_8f21c4=false"), hangryHippo.writes);
    }

    @Test
    @DisplayName("given a robot Fleet has no HangryHippo account for, when its change is applied, then nothing is written")
    void skipsUnregisteredRobot() throws Exception {
        StatusChange change = new StatusChange("chg_test", "C99999", "ONLINE", List.of(), T0);
        sync.noteReceived(change);

        assertEquals(AvailabilitySync.Outcome.NOT_REGISTERED, sync.apply(change));
        assertEquals(List.of(), hangryHippo.writes);
    }

    @Test
    @DisplayName("given a change already written, when a duplicate arrives, then it is not written again")
    void dropsDuplicate() throws Exception {
        receiveAndApply(change("ONLINE", List.of(), T0));

        assertEquals(AvailabilitySync.Outcome.STALE, receiveAndApply(change("ONLINE", List.of(), T0)));
        assertEquals(List.of("veh_8f21c4=true"), hangryHippo.writes);
    }

    @Test
    @DisplayName("given a newer change already written, when an older one arrives late, then it does not overwrite it")
    void dropsLateArrival() throws Exception {
        receiveAndApply(change("OFFLINE", List.of(), T0.plusSeconds(5)));

        assertEquals(AvailabilitySync.Outcome.STALE, receiveAndApply(change("ONLINE", List.of(), T0)));
        assertEquals(List.of("veh_8f21c4=false"), hangryHippo.writes);
    }

    @Test
    @DisplayName("given a newer change has arrived, when an older one queued ahead of it is applied, then it is skipped")
    void skipsSupersededChange() throws Exception {
        StatusChange older = change("ONLINE", List.of(), T0);
        StatusChange newer = change("OFFLINE", List.of(), T0.plusSeconds(1));
        sync.noteReceived(older);
        sync.noteReceived(newer);

        assertEquals(AvailabilitySync.Outcome.SUPERSEDED, sync.apply(older));
        assertEquals(AvailabilitySync.Outcome.WRITTEN, sync.apply(newer));
        assertEquals(List.of("veh_8f21c4=false"), hangryHippo.writes);
    }

    @Test
    @DisplayName("given HangryHippo sheds load briefly, when a change is applied, then it backs off and retries until written")
    void retriesTransientFailure() throws Exception {
        hangryHippo.failures.add(new AvailabilitySync.TransientFailure("503"));
        hangryHippo.failures.add(new AvailabilitySync.TransientFailure("503"));

        assertEquals(AvailabilitySync.Outcome.WRITTEN, receiveAndApply(change("ONLINE", List.of(), T0)));
        assertEquals(List.of("veh_8f21c4=true"), hangryHippo.writes);
        assertEquals(List.of(200L, 400L), sleeps);
    }

    @Test
    @DisplayName("given HangryHippo keeps shedding load, when retries run out, then the change is to be retried later")
    void givesUpAfterRetries() throws Exception {
        for (int i = 0; i <= AvailabilitySync.BACKOFF_MS.length; i++) {
            hangryHippo.failures.add(new AvailabilitySync.TransientFailure("503"));
        }

        assertEquals(AvailabilitySync.Outcome.RETRY_LATER, receiveAndApply(change("ONLINE", List.of(), T0)));
        assertEquals(List.of(), hangryHippo.writes);
        assertEquals(AvailabilitySync.BACKOFF_MS.length, sleeps.size());
    }

    @Test
    @DisplayName("given a change that could not be written, when it is redelivered, then it is written")
    void writesOnRedelivery() throws Exception {
        for (int i = 0; i <= AvailabilitySync.BACKOFF_MS.length; i++) {
            hangryHippo.failures.add(new AvailabilitySync.TransientFailure("503"));
        }
        StatusChange change = change("ONLINE", List.of(), T0);
        receiveAndApply(change);

        assertEquals(AvailabilitySync.Outcome.WRITTEN, receiveAndApply(change));
        assertEquals(List.of("veh_8f21c4=true"), hangryHippo.writes);
    }

    @Test
    @DisplayName("given a newer change arrives while a write is retrying, when the retry comes round, then the older one stops")
    void stopsRetryingWhenSuperseded() throws Exception {
        StatusChange older = change("ONLINE", List.of(), T0);
        StatusChange newer = change("OFFLINE", List.of(), T0.plusSeconds(1));
        hangryHippo.failures.add(new AvailabilitySync.TransientFailure("503"));
        hangryHippo.onWrite = () -> sync.noteReceived(newer);

        assertEquals(AvailabilitySync.Outcome.SUPERSEDED, receiveAndApply(older));
        assertEquals(List.of(), hangryHippo.writes);
    }

    @Test
    @DisplayName("given HangryHippo does not know the vehicle id, when a change is applied, then it is rejected without retrying")
    void rejectsPermanentFailure() throws Exception {
        hangryHippo.failures.add(new AvailabilitySync.PermanentFailure("404"));

        assertEquals(AvailabilitySync.Outcome.REJECTED, receiveAndApply(change("ONLINE", List.of(), T0)));
        assertEquals(List.of(), sleeps);
    }
}
