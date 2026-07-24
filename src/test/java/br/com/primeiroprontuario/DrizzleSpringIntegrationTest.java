package br.com.primeiroprontuario;

import java.sql.SQLException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

abstract class DrizzleSpringIntegrationTest {

    @DynamicPropertySource
    static void drizzleDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> false);
    }

    static void executeAsMigration(DrizzlePostgreSQLContainer database, String sql) {
        try (var connection = database.openMigrationConnection();
                var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not prepare the integration test through the migration role", exception);
        }
    }
}
