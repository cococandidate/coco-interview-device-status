import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatusChangeTest {

    @Test
    @DisplayName("given a well-formed status change, when it is parsed, then every field is read")
    void parsesWellFormedChange() throws Exception {
        StatusChange change = StatusChange.parse("chg_1",
                "{\"serial\":\"C10393\",\"status\":\"ONLINE\",\"limitingFactors\":[\"LOW_BATTERY\"],\"observedAt\":\"2026-09-24T17:02:11.482Z\"}");

        assertEquals(new StatusChange("chg_1", "C10393", "ONLINE", List.of("LOW_BATTERY"),
                Instant.parse("2026-09-24T17:02:11.482Z")), change);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "[]",
            "{\"status\":\"ONLINE\",\"limitingFactors\":[],\"observedAt\":\"2026-09-24T17:02:11.482Z\"}",
            "{\"serial\":\"C10393\",\"status\":\"REBOOTING\",\"limitingFactors\":[],\"observedAt\":\"2026-09-24T17:02:11.482Z\"}",
            "{\"serial\":\"C10393\",\"status\":\"ONLINE\",\"observedAt\":\"2026-09-24T17:02:11.482Z\"}",
            "{\"serial\":\"C10393\",\"status\":\"ONLINE\",\"limitingFactors\":[1],\"observedAt\":\"2026-09-24T17:02:11.482Z\"}",
            "{\"serial\":\"C10393\",\"status\":\"ONLINE\",\"limitingFactors\":[],\"observedAt\":\"yesterday\"}",
            "{\"serial\":\"C10393\",\"status\":\"ONLINE\",\"limitingFactors\":[]}",
    })
    @DisplayName("given a change we cannot read, when it is parsed, then it is invalid")
    void rejectsUnreadableChange(String body) {
        assertThrows(StatusChange.InvalidChange.class, () -> StatusChange.parse("chg_1", body));
    }
}
