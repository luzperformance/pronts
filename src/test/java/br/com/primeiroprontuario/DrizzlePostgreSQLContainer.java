package br.com.primeiroprontuario;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.testcontainers.postgresql.PostgreSQLContainer;

final class DrizzlePostgreSQLContainer extends PostgreSQLContainer {

    private static final String DATABASE_NAME = "primeiro_prontuario";
    private static final String MIGRATION_USERNAME = "primeiro_prontuario_migration";
    private static final String MIGRATION_PASSWORD = "ephemeral-migration-password";
    private static final String RUNTIME_USERNAME = "primeiro_prontuario_runtime";
    private static final String RUNTIME_PASSWORD = "ephemeral-runtime-password";
    private static final Path DATABASE_PACKAGE = Path.of(System.getProperty("user.dir"), "database");
    private static final Path DRIZZLE_EXECUTABLE = DATABASE_PACKAGE.resolve("node_modules/.bin/drizzle-kit");

    private boolean prepared;

    DrizzlePostgreSQLContainer() {
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
            applyDrizzleMigrations();
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

                    GRANT USAGE, CREATE
                    ON SCHEMA public
                    TO primeiro_prontuario_migration;
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create the disposable migration and runtime roles", exception);
        }
    }

    private void applyDrizzleMigrations() {
        if (!Files.isRegularFile(DATABASE_PACKAGE.resolve("drizzle/meta/_journal.json"))) {
            throw new IllegalStateException("Could not find the versioned Drizzle migrations in " + DATABASE_PACKAGE);
        }

        ensureDrizzleTooling();
        var processBuilder = new ProcessBuilder("npm", "run", "migrate")
                .directory(DATABASE_PACKAGE.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().put("DATABASE_URL", migrationConnectionUrl());
        run(processBuilder, "Drizzle migration failed");
    }

    private static synchronized void ensureDrizzleTooling() {
        if (Files.isExecutable(DRIZZLE_EXECUTABLE)) {
            return;
        }

        var processBuilder = new ProcessBuilder("npm", "ci")
                .directory(DATABASE_PACKAGE.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().remove("DATABASE_URL");
        run(processBuilder, "Could not install the pinned Drizzle test tooling");
    }

    private static void run(ProcessBuilder processBuilder, String failureMessage) {
        try {
            var process = processBuilder.start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(failureMessage + ":\n" + output);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(failureMessage, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing the Drizzle test tooling", exception);
        }
    }

    private String migrationConnectionUrl() {
        return "postgresql://"
                + MIGRATION_USERNAME
                + ":"
                + MIGRATION_PASSWORD
                + "@"
                + getHost()
                + ":"
                + getMappedPort(POSTGRESQL_PORT)
                + "/"
                + DATABASE_NAME;
    }
}
