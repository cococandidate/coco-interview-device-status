import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
class FleetIntegrationTest {

    @Test
    @DisplayName("given a robot registered with HangryHippo, when the fleet is asked for its HangryHippo account, then it returns the vehicle id")
    void registeredSerialMapsToVehicleId() throws Exception {
        Server.Response res = Server.get(Server.FLEET_URL + "/v1/devices/C10393/partner-accounts/hangry-hippo");

        assertEquals(200, res.status());
        assertEquals("veh_8f21c4", res.string("vehicleId"));
    }
}
