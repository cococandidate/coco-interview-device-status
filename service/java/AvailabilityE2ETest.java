import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("e2e")
class AvailabilityE2ETest {

    // "Within seconds", with room for HangryHippo's ~700ms writes and a few 503 retries.
    static final Duration WITHIN_SECONDS = Duration.ofSeconds(10);

    @BeforeEach
    void isolateTest() throws Exception {
        E2EHelpers.isolateTest();
    }

    @AfterEach
    void resumeTraffic() throws Exception {
        E2EHelpers.resumeTraffic();
    }

    @Test
    @DisplayName("given a robot online with nothing blocking it, when Fleet publishes that change, then HangryHippo marks it available")
    void onlineRobotWithNothingBlockingIsAvailable() throws Exception {
        E2EHelpers.publishChange("C10393", "ONLINE", List.of());

        assertEquals(true, E2EHelpers.settledAvailability("C10393", WITHIN_SECONDS));
    }

    @Test
    @DisplayName("given a robot that goes offline, when Fleet publishes that change, then HangryHippo marks it unavailable")
    void offlineRobotIsUnavailable() throws Exception {
        E2EHelpers.publishChange("C10393", "OFFLINE", List.of());

        assertEquals(false, E2EHelpers.settledAvailability("C10393", WITHIN_SECONDS));
    }

    @Test
    @DisplayName("given a robot online but low on battery, when Fleet publishes that change, then HangryHippo marks it unavailable")
    void blockingFactorMakesUnavailable() throws Exception {
        E2EHelpers.publishChange("C10393", "ONLINE", List.of("LOW_BATTERY"));

        assertEquals(false, E2EHelpers.settledAvailability("C10393", WITHIN_SECONDS));
    }

    @Test
    @DisplayName("given a robot online with only an informational factor, when Fleet publishes that change, then HangryHippo marks it available")
    void informationalFactorDoesNotBlock() throws Exception {
        E2EHelpers.publishChange("C10393", "ONLINE", List.of("CAMERA_DIRTY"));

        assertEquals(true, E2EHelpers.settledAvailability("C10393", WITHIN_SECONDS));
    }

    @Test
    @DisplayName("given an unavailable robot, when it becomes available again, then HangryHippo marks it available")
    void becomesAvailableAgain() throws Exception {
        E2EHelpers.publishChange("C10393", "ONLINE", List.of("MAINTENANCE"));
        assertEquals(false, E2EHelpers.settledAvailability("C10393", WITHIN_SECONDS));

        E2EHelpers.publishChange("C10393", "ONLINE", List.of());

        assertEquals(true, E2EHelpers.settledAvailability("C10393", WITHIN_SECONDS));
    }

    @Test
    @DisplayName("given a newer change was published first, when an older change arrives after it, then HangryHippo keeps the newer one")
    void lateOlderChangeDoesNotOverwrite() throws Exception {
        Instant now = Instant.now();
        E2EHelpers.publishChange("C11002", "OFFLINE", List.of(), now);
        E2EHelpers.publishChange("C11002", "ONLINE", List.of(), now.minusMillis(500));

        assertEquals(false, E2EHelpers.settledAvailability("C11002", WITHIN_SECONDS));
    }

    @Test
    @DisplayName("given Fleet publishes the same change twice, when both arrive, then HangryHippo ends up with that change")
    void duplicateChangeIsHarmless() throws Exception {
        Instant now = Instant.now();
        E2EHelpers.publishChange("C11002", "ONLINE", List.of("OUT_OF_ZONE"), now);
        E2EHelpers.publishChange("C11002", "ONLINE", List.of("OUT_OF_ZONE"), now);

        assertEquals(false, E2EHelpers.settledAvailability("C11002", WITHIN_SECONDS));
    }

    @Test
    @DisplayName("given several robots change at once, when Fleet publishes them, then HangryHippo has each robot's latest state")
    void severalRobotsAtOnce() throws Exception {
        E2EHelpers.publishChange("C10393", "ONLINE", List.of());
        E2EHelpers.publishChange("C11387", "ONLINE", List.of("HARDWARE_FAULT"));
        E2EHelpers.publishChange("C10350", "OFFLINE", List.of());
        E2EHelpers.publishChange("C11002", "ONLINE", List.of());
        E2EHelpers.publishChange("C10871", "ONLINE", List.of("PILOT_REVIEW"));
        E2EHelpers.publishChange("C11002", "ONLINE", List.of("LOW_BATTERY"));

        assertEquals(true, E2EHelpers.settledAvailability("C10393", WITHIN_SECONDS));
        assertEquals(false, E2EHelpers.settledAvailability("C11387", WITHIN_SECONDS));
        assertEquals(false, E2EHelpers.settledAvailability("C10350", WITHIN_SECONDS));
        assertEquals(false, E2EHelpers.settledAvailability("C11002", WITHIN_SECONDS));
        assertEquals(false, E2EHelpers.settledAvailability("C10871", WITHIN_SECONDS));
    }
}
