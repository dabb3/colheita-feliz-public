package br.com.colheitafeliz.persistence;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomationJsonValidationTest {
    @Test void rejectsCoercionUnknownServiceAndIncompleteMetadata() {
        for (String metadata : java.util.List.of(
                "{\"cursor\":123,\"expiresAt\":{},\"weedsSince\":{},\"insectsSince\":{}}",
                "{\"cursor\":\"2026-01-01T00:00:00Z\",\"expiresAt\":{\"free\":\"2026-02-01T00:00:00Z\"},\"weedsSince\":{},\"insectsSince\":{}}",
                "{\"cursor\":\"2026-01-01T00:00:00Z\",\"expiresAt\":{},\"weedsSince\":{\"01\":\"2026-01-01T00:00:00Z\"},\"insectsSince\":{}}",
                "{\"cursor\":\"2026-01-01T00:00:00Z\",\"expiresAt\":{}}"))
            assertThrows(IllegalArgumentException.class, () -> AutomationJsonValidation.validate(JsonParser.parseString("{\"automations\":"+metadata+"}")));
        assertDoesNotThrow(() -> AutomationJsonValidation.validate(JsonParser.parseString("{}")));
    }
}
