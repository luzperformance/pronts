package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("prod")
@SpringBootTest(
        classes = PrimeiroProntuarioApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.doctor.username=doctor",
            "app.doctor.password=valid-test-password",
            "spring.datasource.hikari.connection-timeout=1000"
        })
class ProductionHealthProbeIntegrationTest {

    @Container
    @ServiceConnection(type = org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails.class)
    private static final FlywayPostgreSQLContainer POSTGRESQL = new FlywayPostgreSQLContainer();

    @org.springframework.test.context.DynamicPropertySource
    static void flywayProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        POSTGRESQL.registerFlywayProperties(registry);
    }

    @Autowired
    private DataSource dataSource;

    @LocalServerPort
    private int port;

    @Test
    void runtimeUsesSmallPoolAfterFlywayAndKeepsLivenessDuringDatabaseOutage() throws Exception {
        var hikari = (HikariDataSource) dataSource;
        assertThat(hikari.getMinimumIdle()).isEqualTo(1);
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(5);
        assertThat(flywayHistoryExists()).isTrue();

        assertHealth("/actuator/health/liveness", 200, "{\"status\":\"UP\"}");
        assertHealth("/actuator/health/readiness", 200, "{\"status\":\"UP\"}");

        POSTGRESQL.stop();

        assertHealth("/actuator/health/readiness", 503, "{\"status\":\"DOWN\"}");
        assertHealth("/actuator/health/liveness", 200, "{\"status\":\"UP\"}");
    }

    private void assertHealth(String path, int expectedStatus, String expectedBody) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        assertThat(response.body()).isEqualTo(expectedBody);
    }

    private boolean flywayHistoryExists() throws Exception {
        try (var connection = POSTGRESQL.openMigrationConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT to_regclass('public.flyway_schema_history') IS NOT NULL")) {
            assertThat(result.next()).isTrue();
            return result.getBoolean(1);
        }
    }
}
