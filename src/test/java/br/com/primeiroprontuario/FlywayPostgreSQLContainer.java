package br.com.primeiroprontuario;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

final class FlywayPostgreSQLContainer extends PostgreSQLContainer {

    private static final String DATABASE_NAME = "primeiro_prontuario";
    private static final String MIGRATION_USERNAME = "primeiro_prontuario_migration";
    private static final String MIGRATION_PASSWORD = "ephemeral-migration-password";
    private static final String RUNTIME_USERNAME = "primeiro_prontuario_runtime";
    private static final String RUNTIME_PASSWORD = "ephemeral-runtime-password";
    private boolean prepared;

    FlywayPostgreSQLContainer() {
        super("postgres:18.4");
        withDatabaseName(DATABASE_NAME);
    }

    @Override
    public void start() {
        if (isRunning()) {
            return;
        }

        super.start();
        try {
            createExternalRoles();
            prepared = true;
        } catch (RuntimeException exception) {
            super.stop();
            throw exception;
        }
    }

    @Override
    public String getUsername() {
        return prepared ? RUNTIME_USERNAME : super.getUsername();
    }

    @Override
    public String getPassword() {
        return prepared ? RUNTIME_PASSWORD : super.getPassword();
    }

    Connection openMigrationConnection() throws SQLException {
        return DriverManager.getConnection(super.getJdbcUrl(), MIGRATION_USERNAME, MIGRATION_PASSWORD);
    }

    Connection openRuntimeConnection() throws SQLException {
        return DriverManager.getConnection(super.getJdbcUrl(), RUNTIME_USERNAME, RUNTIME_PASSWORD);
    }

    void registerFlywayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", () -> super.getJdbcUrl());
        registry.add("spring.flyway.user", () -> MIGRATION_USERNAME);
        registry.add("spring.flyway.password", () -> MIGRATION_PASSWORD);
    }

    String getMigrationJdbcUrl() {
        return super.getJdbcUrl();
    }

    String getMigrationUsername() {
        return MIGRATION_USERNAME;
    }

    String getMigrationPassword() {
        return MIGRATION_PASSWORD;
    }

    private void createExternalRoles() {
        try (var connection =
                        DriverManager.getConnection(super.getJdbcUrl(), super.getUsername(), super.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE ROLE primeiro_prontuario_migration
                    LOGIN
                    PASSWORD 'ephemeral-migration-password'
                    NOSUPERUSER
                    NOCREATEDB
                    NOCREATEROLE
                    NOREPLICATION
                    NOBYPASSRLS;

                    CREATE ROLE primeiro_prontuario_runtime
                    LOGIN
                    PASSWORD 'ephemeral-runtime-password'
                    NOSUPERUSER
                    NOCREATEDB
                    NOCREATEROLE
                    NOREPLICATION
                    NOBYPASSRLS;

                    REVOKE CONNECT, TEMPORARY
                    ON DATABASE primeiro_prontuario
                    FROM PUBLIC;

                    GRANT CONNECT, CREATE
                    ON DATABASE primeiro_prontuario
                    TO primeiro_prontuario_migration;

                    GRANT CONNECT
                    ON DATABASE primeiro_prontuario
                    TO primeiro_prontuario_runtime;

                    REVOKE CREATE
                    ON SCHEMA public
                    FROM PUBLIC;

                    GRANT USAGE, CREATE
                    ON SCHEMA public
                    TO primeiro_prontuario_migration;

                    GRANT USAGE
                    ON SCHEMA public
                    TO primeiro_prontuario_runtime;

                    ALTER DEFAULT PRIVILEGES
                    FOR ROLE primeiro_prontuario_migration
                    IN SCHEMA public
                    GRANT SELECT, INSERT, UPDATE, DELETE
                    ON TABLES
                    TO primeiro_prontuario_runtime;

                    ALTER DEFAULT PRIVILEGES
                    FOR ROLE primeiro_prontuario_migration
                    IN SCHEMA public
                    GRANT USAGE, SELECT, UPDATE
                    ON SEQUENCES
                    TO primeiro_prontuario_runtime;
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create the disposable migration and runtime roles", exception);
        }
    }
}
