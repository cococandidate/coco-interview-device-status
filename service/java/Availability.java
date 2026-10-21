import java.util.List;
import java.util.Set;

final class Availability {

    // From issue #4. Any other factor is informational and does not block.
    static final Set<String> BLOCKING_FACTORS = Set.of(
            "PILOT_REVIEW",
            "LOW_BATTERY",
            "HARDWARE_FAULT",
            "MAINTENANCE",
            "OUT_OF_ZONE");

    static boolean isAvailable(String status, List<String> limitingFactors) {
        if (!"ONLINE".equals(status)) {
            return false;
        }
        return limitingFactors.stream().noneMatch(BLOCKING_FACTORS::contains);
    }

    private Availability() {
    }
}
