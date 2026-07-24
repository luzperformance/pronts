package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

class ApplicationStartupIntegrationTest {

    @Test
    void startsOnAnEmptyPostgreSqlDatabase() {
        try (var postgresql = new PostgreSQLContainer("postgres:18.4")) {
            postgresql.start();

            try (var application = startApplication(postgresql)) {
                assertThat(application.isActive()).isTrue();
            }
        }
    }

    @Test
    void refusesToStartWhenTheSchemaIsIncompatible() throws Exception {
        try (var postgresql = new PostgreSQLContainer("postgres:18.4")) {
            postgresql.start();

            try (var application = startApplication(postgresql)) {
                assertThat(application.isActive()).isTrue();
            }

            try (var connection = DriverManager.getConnection(
                            postgresql.getJdbcUrl(), postgresql.getUsername(), postgresql.getPassword());
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
        return SpringApplication.run(
                PrimeiroProntuarioApplication.class,
                "--server.port=0",
                "--DB_URL=" + postgresql.getJdbcUrl(),
                "--DB_USERNAME=" + postgresql.getUsername(),
                "--DB_PASSWORD=" + postgresql.getPassword(),
                "--app.doctor.username=doctor",
                "--app.doctor.password=valid-test-password");
    }
}
