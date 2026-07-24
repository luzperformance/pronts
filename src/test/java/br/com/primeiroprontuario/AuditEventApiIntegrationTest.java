package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@Import(AuditEventApiIntegrationTest.MutableClockConfiguration.class)
@SpringBootTest(
        classes = PrimeiroProntuarioApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.doctor.username=doctor",
            "app.doctor.password=valid-test-password",
            "server.servlet.session.cookie.secure=false"
        })
class AuditEventApiIntegrationTest {

    private static final MutableClock CLOCK = new MutableClock();

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18.4");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearBusinessData() {
        jdbc.execute("""
                TRUNCATE TABLE
                    attachment,
                    addendum,
                    consultation,
                    schedule_block,
                    appointment,
                    patient,
                    audit_event
                """);
        CLOCK.set(Instant.parse("2030-01-15T10:00:00Z"));
    }

    @Test
    void auditEventsCanBeFilteredByPeriodActionOutcomeTargetTypeAndTargetIdTogether() throws Exception {
        var client = authenticatedClient();
        CLOCK.set(Instant.parse("2030-01-15T10:05:00Z"));
        var requestedPatientId = createPatient(client, "529.982.247-25", "Paciente filtrado");
        CLOCK.set(Instant.parse("2030-01-15T10:10:00Z"));
        createPatient(client, "862.883.667-57", "Outro paciente");

        var response = get(
                client,
                "/api/v1/audit-events?from=2030-01-15T10:05:00Z"
                        + "&to=2030-01-15T10:10:00Z"
                        + "&action=PATIENT_CREATED"
                        + "&outcome=SUCCESS"
                        + "&targetType=PATIENT"
                        + "&targetId="
                        + requestedPatientId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(response.body(), "$.totalElements")).isEqualTo(1);
        assertThat(JsonPath.<String>read(response.body(), "$.content[0].targetId"))
                .isEqualTo(requestedPatientId);
        assertThat(JsonPath.<String>read(response.body(), "$.content[0].occurredAt"))
                .isEqualTo("2030-01-15T10:05:00Z");
        assertThat(response.body())
                .doesNotContain("Paciente filtrado")
                .doesNotContain("529.982.247-25")
                .doesNotContain("payload")
                .doesNotContain("password");
    }

    @Test
    void persistedAuditEventsRejectUpdateAndDelete() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.001-08", "Paciente append-only");
        var audit = get(client, "/api/v1/audit-events?action=PATIENT_CREATED&targetId=" + patientId);
        var eventId = JsonPath.<String>read(audit.body(), "$.content[0].id");

        assertThat(catchingDataAccessException(() ->
                        jdbc.update("UPDATE audit_event SET target_type = 'CHANGED' WHERE id = ?::uuid", eventId)))
                .isNotNull();
        assertThat(catchingDataAccessException(
                        () -> jdbc.update("DELETE FROM audit_event WHERE id = ?::uuid", eventId)))
                .isNotNull();

        var unchanged = get(client, "/api/v1/audit-events?targetId=" + patientId);
        assertThat(JsonPath.<Integer>read(unchanged.body(), "$.totalElements")).isEqualTo(1);
        assertThat(JsonPath.<String>read(unchanged.body(), "$.content[0].targetType"))
                .isEqualTo("PATIENT");
    }

    @Test
    void everyAuditFilterAlsoWorksInIsolation() throws Exception {
        var client = authenticatedClient();
        CLOCK.set(Instant.parse("2030-01-15T10:05:00Z"));
        var requestedPatientId = createPatient(client, "100.000.002-80", "Paciente alvo");
        CLOCK.set(Instant.parse("2030-01-15T10:10:00Z"));
        createPatient(client, "100.000.003-61", "Paciente posterior");
        CLOCK.set(Instant.parse("2030-01-15T10:15:00Z"));
        var failedLogin = send(HttpClient.newHttpClient(), "POST", "/api/v1/auth/login", """
                {"username":"doctor","password":"senha-incorreta"}
                """, null, null);
        assertThat(failedLogin.statusCode()).isEqualTo(401);

        assertTotal(client, "/api/v1/audit-events?from=2030-01-15T10:10:00Z", 2);
        assertTotal(client, "/api/v1/audit-events?to=2030-01-15T10:05:00Z", 1);
        assertTotal(client, "/api/v1/audit-events?action=PATIENT_CREATED", 2);
        assertTotal(client, "/api/v1/audit-events?outcome=FAILURE", 1);
        assertTotal(client, "/api/v1/audit-events?targetType=PATIENT", 2);
        assertTotal(client, "/api/v1/audit-events?targetId=" + requestedPatientId, 1);
    }

    @Test
    void auditPaginationIsRecentFirstAndStableWhenInstantsTie() throws Exception {
        var client = authenticatedClient();
        CLOCK.set(Instant.parse("2030-01-15T10:05:00Z"));
        createPatient(client, "100.000.004-42", "Primeiro paciente");
        createPatient(client, "100.000.005-23", "Segundo paciente");
        CLOCK.set(Instant.parse("2030-01-15T10:10:00Z"));
        createPatient(client, "100.000.006-04", "Paciente mais recente");
        var path = "/api/v1/audit-events?action=PATIENT_CREATED";

        var firstRead = get(client, path + "&size=100");
        var secondRead = get(client, path + "&size=100");
        var firstPage = get(client, path + "&size=1&page=0");
        var secondPage = get(client, path + "&size=1&page=1");
        var ids = JsonPath.<List<String>>read(firstRead.body(), "$.content[*].id");
        var tiedIds = new ArrayList<>(ids.subList(1, 3));
        tiedIds.sort(Comparator.reverseOrder());

        assertThat(JsonPath.<List<String>>read(firstRead.body(), "$.content[*].occurredAt"))
                .containsExactly("2030-01-15T10:10:00Z", "2030-01-15T10:05:00Z", "2030-01-15T10:05:00Z");
        assertThat(ids.subList(1, 3)).containsExactlyElementsOf(tiedIds);
        assertThat(JsonPath.<List<String>>read(secondRead.body(), "$.content[*].id"))
                .containsExactlyElementsOf(ids);
        assertThat(JsonPath.<String>read(firstPage.body(), "$.content[0].id")).isEqualTo(ids.get(0));
        assertThat(JsonPath.<String>read(secondPage.body(), "$.content[0].id")).isEqualTo(ids.get(1));
    }

    @Test
    void invalidAuditFiltersReturnFieldSpecificErrors() throws Exception {
        var client = authenticatedClient();

        assertInvalidFilter(client, "from=not-an-instant", "from");
        assertInvalidFilter(client, "from=2030-01-15T10:00:00Z&to=2030-01-15T10:00:00Z", "to");
        assertInvalidFilter(client, "action=UNKNOWN", "action");
        assertInvalidFilter(client, "outcome=UNKNOWN", "outcome");
        assertInvalidFilter(client, "targetId=not-a-uuid", "targetId");
    }

    @Test
    void auditApiHasNoMutableOperation() throws Exception {
        var client = authenticatedClient();

        assertThat(mutation(client, "POST", "/api/v1/audit-events", "{}").statusCode())
                .isEqualTo(403);
        assertThat(mutation(client, "PUT", "/api/v1/audit-events/" + java.util.UUID.randomUUID(), "{}")
                        .statusCode())
                .isEqualTo(403);
        assertThat(mutation(client, "DELETE", "/api/v1/audit-events/" + java.util.UUID.randomUUID(), "{}")
                        .statusCode())
                .isEqualTo(403);
    }

    private void assertTotal(HttpClient client, String path, int expected) throws Exception {
        var response = get(client, path);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(response.body(), "$.totalElements")).isEqualTo(expected);
    }

    private void assertInvalidFilter(HttpClient client, String query, String expectedField) throws Exception {
        var response = get(client, "/api/v1/audit-events?" + query);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].field")).isEqualTo(expectedField);
    }

    private DataAccessException catchingDataAccessException(Runnable mutation) {
        try {
            mutation.run();
            return null;
        } catch (DataAccessException exception) {
            return exception;
        }
    }

    private HttpClient authenticatedClient() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        var login = send(client, "POST", "/api/v1/auth/login", """
                {"username":"doctor","password":"valid-test-password"}
                """, null, null);
        assertThat(login.statusCode()).isEqualTo(200);
        return client;
    }

    private String createPatient(HttpClient client, String cpf, String fullName) throws Exception {
        var created = mutation(client, "POST", "/api/v1/patients", """
                {
                  "fullName": "%s",
                  "motherName": "Mãe fictícia",
                  "birthDate": "1990-05-20",
                  "cpf": "%s",
                  "phone": "(11) 99999-1234"
                }
                """.formatted(fullName, cpf));
        assertThat(created.statusCode()).isEqualTo(201);
        return JsonPath.read(created.body(), "$.id");
    }

    private HttpResponse<String> mutation(HttpClient client, String method, String path, String body) throws Exception {
        var csrf = get(client, "/api/v1/auth/csrf");
        return send(
                client,
                method,
                path,
                body,
                JsonPath.read(csrf.body(), "$.headerName"),
                JsonPath.read(csrf.body(), "$.token"));
    }

    private HttpResponse<String> send(
            HttpClient client, String method, String path, String body, String csrfHeader, String csrfToken)
            throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(apiUri(path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (csrfHeader != null) {
            request.header(csrfHeader, csrfToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        var request = HttpRequest.newBuilder().uri(apiUri(path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI apiUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.EPOCH);

        void set(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class MutableClockConfiguration {

        @Bean
        @Primary
        Clock auditTestClock() {
            return CLOCK;
        }
    }
}
