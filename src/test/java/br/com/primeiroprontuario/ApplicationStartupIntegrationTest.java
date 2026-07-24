package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

class ApplicationStartupIntegrationTest {

    @Test
    void startsWithTheRuntimeRoleOnAnEmptyDatabasePreparedByDrizzle() throws Exception {
        try (var postgresql = new DrizzlePostgreSQLContainer()) {
            postgresql.start();

            try (var application = startApplication(postgresql)) {
                assertThat(application.isActive()).isTrue();
                assertThat(application.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
                        .isEqualTo("validate");

                var jdbc = application.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForObject("SELECT current_user", String.class))
                        .isEqualTo("primeiro_prontuario_runtime");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM doctor_account", Long.class))
                        .isEqualTo(1);
                assertThat(jdbc.queryForObject("""
                                SELECT
                                    (SELECT count(*) FROM patient)
                                    + (SELECT count(*) FROM appointment)
                                    + (SELECT count(*) FROM schedule_block)
                                    + (SELECT count(*) FROM consultation)
                                    + (SELECT count(*) FROM addendum)
                                    + (SELECT count(*) FROM attachment)
                                    + (SELECT count(*) FROM audit_event)
                                """, Long.class)).isZero();
            }

            try (var connection = postgresql.openMigrationConnection();
                    var statement = connection.createStatement();
                    var migrations = statement.executeQuery("SELECT count(*) FROM drizzle.__drizzle_migrations")) {
                assertThat(migrations.next()).isTrue();
                assertThat(migrations.getLong(1)).isEqualTo(1);
                try (var flyway =
                        statement.executeQuery("SELECT to_regclass('public.flyway_schema_history') IS NULL")) {
                    assertThat(flyway.next()).isTrue();
                    assertThat(flyway.getBoolean(1)).isTrue();
                }
            }
        }
    }

    @Test
    void restartWithTheSameSecretKeepsTheSingleDoctorAccountUnchanged() {
        try (var postgresql = new DrizzlePostgreSQLContainer()) {
            postgresql.start();
            String initialPasswordHash;

            try (var application = startApplication(postgresql)) {
                var jdbc = application.getBean(JdbcTemplate.class);
                initialPasswordHash = jdbc.queryForObject(
                        "SELECT password_hash FROM doctor_account WHERE username = 'doctor'", String.class);
            }

            try (var restartedApplication = startApplication(postgresql)) {
                var jdbc = restartedApplication.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM doctor_account", Long.class))
                        .isEqualTo(1);
                assertThat(jdbc.queryForObject(
                                "SELECT password_hash FROM doctor_account WHERE username = 'doctor'", String.class))
                        .isEqualTo(initialPasswordHash);
            }
        }
    }

    @Test
    void changedSecretReplacesTheHashAndOnlyTheNewPasswordAuthenticates() throws Exception {
        try (var postgresql = new DrizzlePostgreSQLContainer()) {
            postgresql.start();
            String initialPasswordHash;

            try (var application = startApplication(postgresql)) {
                initialPasswordHash = application
                        .getBean(JdbcTemplate.class)
                        .queryForObject(
                                "SELECT password_hash FROM doctor_account WHERE username = 'doctor'", String.class);
            }

            try (var restartedApplication = startApplication(postgresql, "doctor", "rotated-test-password")) {
                var jdbc = restartedApplication.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForObject(
                                "SELECT password_hash FROM doctor_account WHERE username = 'doctor'", String.class))
                        .isNotEqualTo(initialPasswordHash);
                assertThat(login(restartedApplication, "doctor", "valid-test-password")
                                .statusCode())
                        .isEqualTo(401);
                assertThat(login(restartedApplication, "doctor", "rotated-test-password")
                                .statusCode())
                        .isEqualTo(200);
            }
        }
    }

    @Test
    void refusesToStartWhenMoreThanOneDoctorAccountExists() throws Exception {
        try (var postgresql = new DrizzlePostgreSQLContainer()) {
            postgresql.start();
            try (var application = startApplication(postgresql)) {
                assertThat(application.isActive()).isTrue();
            }

            try (var connection = postgresql.openMigrationConnection();
                    var statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE doctor_account
                        DROP CONSTRAINT doctor_account_singleton_key_key;
                        ALTER TABLE doctor_account
                        DROP CONSTRAINT doctor_account_singleton_key_check;
                        INSERT INTO doctor_account (
                            id,
                            username,
                            password_hash,
                            active,
                            created_at,
                            singleton_key
                        )
                        VALUES (
                            '00000000-0000-0000-0000-000000000002',
                            'second-doctor',
                            'not-used-during-this-test',
                            TRUE,
                            CURRENT_TIMESTAMP,
                            FALSE
                        );
                        """);
            }

            assertThatThrownBy(() -> startApplication(postgresql))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("The MVP supports exactly one doctor account");
        }
    }

    @Test
    void refusesToStartWhenTheConfiguredUsernameChanges() {
        try (var postgresql = new DrizzlePostgreSQLContainer()) {
            postgresql.start();
            try (var application = startApplication(postgresql)) {
                assertThat(application.isActive()).isTrue();
            }

            assertThatThrownBy(() -> startApplication(postgresql, "another-doctor", "valid-test-password"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Configured doctor does not match the provisioned account");
        }
    }

    @Test
    void refusesToStartWhenTheSchemaIsIncompatible() throws Exception {
        try (var postgresql = new DrizzlePostgreSQLContainer()) {
            postgresql.start();

            try (var application = startApplication(postgresql)) {
                assertThat(application.isActive()).isTrue();
            }

            try (var connection = postgresql.openMigrationConnection();
                    var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE schema_marker DROP COLUMN installed_at");
            }

            assertThatThrownBy(() -> startApplication(postgresql))
                    .rootCause()
                    .hasMessageContaining("missing column")
                    .hasMessageContaining("installed_at")
                    .hasMessageContaining("schema_marker");
        }
    }

    private ConfigurableApplicationContext startApplication(PostgreSQLContainer postgresql) {
        return startApplication(postgresql, "doctor", "valid-test-password");
    }

    private ConfigurableApplicationContext startApplication(
            PostgreSQLContainer postgresql, String doctorUsername, String doctorPassword) {
        return SpringApplication.run(
                PrimeiroProntuarioApplication.class,
                "--server.port=0",
                "--server.servlet.session.cookie.secure=false",
                "--DB_URL=" + postgresql.getJdbcUrl(),
                "--DB_USERNAME=" + postgresql.getUsername(),
                "--DB_PASSWORD=" + postgresql.getPassword(),
                "--app.doctor.username=" + doctorUsername,
                "--app.doctor.password=" + doctorPassword);
    }

    private HttpResponse<String> login(ConfigurableApplicationContext application, String username, String password)
            throws Exception {
        var port = application.getEnvironment().getRequiredProperty("local.server.port");
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password)))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
