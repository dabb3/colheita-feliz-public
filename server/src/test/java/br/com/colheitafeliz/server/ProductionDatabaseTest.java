package br.com.colheitafeliz.server;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProductionDatabaseTest {
    @Test void productionVerifiesCertificateAndHostnameWithoutOverrides() {
        assertDoesNotThrow(() -> ServerMain.validateProductionDatabaseUrl("jdbc:postgresql://db.example.com/farm?sslmode=verify-full&sslrootcert=/etc/secrets/root.crt"));
        for (String query : new String[]{"sslmode=require", "sslmode=verify-ca", "sslmode=verify-full&sslmode=require",
                "sslmode=verify-full&ssl=false", "sslmode=verify-full&sslfactory=org.postgresql.ssl.NonValidatingFactory", ""})
            assertThrows(IllegalArgumentException.class, () -> ServerMain.validateProductionDatabaseUrl("jdbc:postgresql://db.example.com/farm?" + query), query);
    }
}
