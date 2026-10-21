import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class E2EHelpers {

    static void isolateTest() throws Exception {
        Server.post(Server.FLEET_URL + "/v1/_debug/traffic", Map.of("enabled", false));
        Channel channel = Server.connect();
        channel.queuePurge(Server.QUEUE);
        channel.getConnection().close();
        // Lets a change your consumer already had in flight land before HangryHippo is cleared.
        Thread.sleep(2000);
        Server.post(Server.PARTNER_URL + "/v1/_debug/reset", null);
    }

    static void resumeTraffic() throws Exception {
        Server.post(Server.FLEET_URL + "/v1/_debug/traffic", Map.of("enabled", true));
    }

    static void publishChange(String serial, String status, List<String> limitingFactors) throws Exception {
        publishChange(serial, status, limitingFactors, Instant.now());
    }

    static void publishChange(String serial, String status, List<String> limitingFactors, Instant observedAt) throws Exception {
        Map<String, Object> change = Map.of(
                "serial", serial,
                "status", status,
                "limitingFactors", limitingFactors,
                "observedAt", observedAt.truncatedTo(ChronoUnit.MILLIS).toString());
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .messageId("chg_e2e_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .contentType("application/json")
                .build();

        Channel channel = Server.connect();
        channel.confirmSelect();
        channel.basicPublish("fleet", "device.status.changed", properties,
                Server.JSON.toJson(change).getBytes(StandardCharsets.UTF_8));
        channel.waitForConfirmsOrDie(5000);
        channel.getConnection().close();
    }

    static Server.Response getPartnerAccount(String serial) throws Exception {
        return Server.get(Server.FLEET_URL + "/v1/devices/" + serial + "/partner-accounts/hangry-hippo");
    }

    static Server.Response getVehicleAvailability(String vehicleId) throws Exception {
        return Server.get(Server.PARTNER_URL + "/v1/vehicles/" + vehicleId + "/availability");
    }

    // What HangryHippo believes about the robot once it stops changing for a moment, or null if nothing was written.
    // Waiting for it to settle, not just for a first value, lets a test catch a late write overturning the right one.
    static Boolean settledAvailability(String serial, Duration timeout) throws Exception {
        String vehicleId = getPartnerAccount(serial).string("vehicleId");
        Instant deadline = Instant.now().plus(timeout);
        Boolean last = null;
        Instant lastChanged = Instant.now();
        while (Instant.now().isBefore(deadline)) {
            Server.Response res = getVehicleAvailability(vehicleId);
            Boolean current = res.status() == 200 ? res.body().getAsJsonObject().get("available").getAsBoolean() : null;
            if (!Objects.equals(current, last)) {
                last = current;
                lastChanged = Instant.now();
            } else if (current != null && Duration.between(lastChanged, Instant.now()).compareTo(SETTLED) >= 0) {
                return current;
            }
            Thread.sleep(100);
        }
        return last;
    }

    static final Duration SETTLED = Duration.ofMillis(2500);
}
