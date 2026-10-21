import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityTest {

    @Test
    @DisplayName("given a robot online with no limiting factors, then it is available")
    void onlineWithNothingIsAvailable() {
        assertTrue(Availability.isAvailable("ONLINE", List.of()));
    }

    @Test
    @DisplayName("given a robot offline with no limiting factors, then it is unavailable")
    void offlineIsUnavailable() {
        assertFalse(Availability.isAvailable("OFFLINE", List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PILOT_REVIEW", "LOW_BATTERY", "HARDWARE_FAULT", "MAINTENANCE", "OUT_OF_ZONE"})
    @DisplayName("given a robot online with a blocking factor, then it is unavailable")
    void blockingFactorMakesUnavailable(String factor) {
        assertFalse(Availability.isAvailable("ONLINE", List.of(factor)));
    }

    @Test
    @DisplayName("given a robot online with only informational factors, then it is available")
    void informationalFactorsDoNotBlock() {
        assertTrue(Availability.isAvailable("ONLINE", List.of("CAMERA_DIRTY", "SOMETHING_NEW")));
    }

    @Test
    @DisplayName("given a robot online with an informational and a blocking factor, then it is unavailable")
    void anyBlockingFactorBlocks() {
        assertFalse(Availability.isAvailable("ONLINE", List.of("CAMERA_DIRTY", "LOW_BATTERY")));
    }

    @Test
    @DisplayName("given a robot offline with only informational factors, then it is unavailable")
    void offlineWithInformationalFactorsIsUnavailable() {
        assertFalse(Availability.isAvailable("OFFLINE", List.of("CAMERA_DIRTY")));
    }
}
