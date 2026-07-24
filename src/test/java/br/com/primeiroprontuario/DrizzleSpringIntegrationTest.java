package br.com.primeiroprontuario;

import java.sql.SQLException;

abstract class DrizzleSpringIntegrationTest {

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
