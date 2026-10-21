import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

record StatusChange(String messageId, String serial, String status, List<String> limitingFactors, Instant observedAt) {

    static final class InvalidChange extends Exception {
        InvalidChange(String message) {
            super(message);
        }
    }

    // Strict on purpose: a change we cannot read is rejected to the DLQ rather than guessed at.
    static StatusChange parse(String messageId, String body) throws InvalidChange {
        JsonObject json;
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject()) {
                throw new InvalidChange("body is not a JSON object");
            }
            json = element.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new InvalidChange("body is not JSON: " + e.getMessage());
        }

        String serial = requiredString(json, "serial");
        String status = requiredString(json, "status");
        if (!status.equals("ONLINE") && !status.equals("OFFLINE")) {
            throw new InvalidChange("unknown status " + status);
        }

        JsonElement factorsJson = json.get("limitingFactors");
        if (factorsJson == null || !factorsJson.isJsonArray()) {
            throw new InvalidChange("limitingFactors is missing or not a list");
        }
        List<String> factors = new ArrayList<>();
        for (JsonElement factor : (JsonArray) factorsJson) {
            if (!factor.isJsonPrimitive() || !factor.getAsJsonPrimitive().isString()) {
                throw new InvalidChange("limitingFactors holds a non-string");
            }
            factors.add(factor.getAsString());
        }

        Instant observedAt;
        try {
            observedAt = Instant.parse(requiredString(json, "observedAt"));
        } catch (DateTimeParseException e) {
            throw new InvalidChange("observedAt is not an RFC 3339 timestamp");
        }

        return new StatusChange(messageId, serial, status, List.copyOf(factors), observedAt);
    }

    private static String requiredString(JsonObject json, String field) throws InvalidChange {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isEmpty()) {
            throw new InvalidChange(field + " is missing or not a string");
        }
        return value.getAsString();
    }
}
