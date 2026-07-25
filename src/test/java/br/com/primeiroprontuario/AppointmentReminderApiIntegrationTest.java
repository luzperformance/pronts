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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Import(AppointmentReminderApiIntegrationTest.TestClockConfiguration.class)
@SpringBootTest(
        classes = PrimeiroProntuarioApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.doctor.username=doctor",
            "app.doctor.password=valid-test-password",
            "app.time-zone=America/Sao_Paulo",
            "server.servlet.session.cookie.secure=false"
        })
class AppointmentReminderApiIntegrationTest extends FlywaySpringIntegrationTest {

    @Container
    @ServiceConnection(type = org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails.class)
    private static final FlywayPostgreSQLContainer POSTGRESQL = new FlywayPostgreSQLContainer();

    @org.springframework.test.context.DynamicPropertySource
    static void flywayProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        POSTGRESQL.registerFlywayProperties(registry);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void remindersIncludeOnlyActiveAppointmentsWhoseStartsAreInsideBothWindowLimits() throws Exception {
        clock.set(Instant.parse("2030-01-14T12:00:00Z"));
        var client = authenticatedClient();
        var patientId = createPatient(client, "529.982.247-25");
        var past = createAppointment(client, patientId, "2030-01-15T08:00:00");
        var lowerLimit = createAppointment(client, patientId, "2030-01-15T09:00:00");
        var confirmed = createAppointment(client, patientId, "2030-01-15T10:00:00");
        var completed = createAppointment(client, patientId, "2030-01-15T11:00:00");
        var cancelled = createAppointment(client, patientId, "2030-01-15T12:00:00");
        var noShow = createAppointment(client, patientId, "2030-01-15T13:00:00");
        var upperLimit = createAppointment(client, patientId, "2030-01-16T09:00:00");
        var afterWindow = createAppointment(client, patientId, "2030-01-16T09:15:00");
        setStatus(confirmed, "CONFIRMED");
        setStatus(completed, "COMPLETED");
        setStatus(cancelled, "CANCELLED");
        setStatus(noShow, "NO_SHOW");
        var confirmedRepresentation = get(client, "/api/v1/appointments/" + confirmed);
        clock.set(Instant.parse("2030-01-15T12:00:00Z"));

        var reminders = get(client, "/api/v1/appointments/reminders");

        assertThat(reminders.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(reminders.body(), "$.content[*].id"))
                .containsExactly(lowerLimit, confirmed, upperLimit)
                .doesNotContain(past, completed, cancelled, noShow, afterWindow);
        assertThat(JsonPath.<List<String>>read(reminders.body(), "$.content[*].startsAt"))
                .containsExactly("2030-01-15T09:00:00-03:00", "2030-01-15T10:00:00-03:00", "2030-01-16T09:00:00-03:00");
        assertThat(JsonPath.<Integer>read(reminders.body(), "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(reminders.body(), "$.size")).isEqualTo(20);
        assertThat(JsonPath.<Integer>read(reminders.body(), "$.totalElements")).isEqualTo(3);

        var confirmedAfterRead = get(client, "/api/v1/appointments/" + confirmed);
        assertThat(JsonPath.<String>read(confirmedAfterRead.body(), "$.status")).isEqualTo("CONFIRMED");
        assertThat(JsonPath.<Integer>read(confirmedAfterRead.body(), "$.version"))
                .isEqualTo(JsonPath.<Integer>read(confirmedRepresentation.body(), "$.version"));
    }

    @Test
    void remindersAreDeterministicallyPagedByStart() throws Exception {
        clock.set(Instant.parse("2030-02-14T12:00:00Z"));
        var client = authenticatedClient();
        var patientId = createPatient(client, "862.883.667-57");
        var last = createAppointment(client, patientId, "2030-02-15T10:00:00");
        var first = createAppointment(client, patientId, "2030-02-15T09:00:00");
        var second = createAppointment(client, patientId, "2030-02-15T09:30:00");
        clock.set(Instant.parse("2030-02-15T12:00:00Z"));

        var firstRead = get(client, "/api/v1/appointments/reminders?page=0&size=2");
        var repeatedRead = get(client, "/api/v1/appointments/reminders?page=0&size=2");
        var secondPage = get(client, "/api/v1/appointments/reminders?page=1&size=2");

        assertThat(JsonPath.<List<String>>read(firstRead.body(), "$.content[*].id"))
                .containsExactly(first, second);
        assertThat(JsonPath.<List<String>>read(repeatedRead.body(), "$.content[*].id"))
                .containsExactly(first, second);
        assertThat(JsonPath.<List<String>>read(secondPage.body(), "$.content[*].id"))
                .containsExactly(last);
        assertThat(JsonPath.<Integer>read(firstRead.body(), "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(firstRead.body(), "$.size")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(firstRead.body(), "$.totalElements")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(firstRead.body(), "$.totalPages")).isEqualTo(2);
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

    private String createPatient(HttpClient client, String cpf) throws Exception {
        var created = mutation(client, "POST", "/api/v1/patients", """
                {
                  "fullName": "Ana Souza",
                  "motherName": "Maria Souza",
                  "birthDate": "1990-05-20",
                  "cpf": "%s",
                  "phone": "(11) 99999-1234"
                }
                """.formatted(cpf));
        assertThat(created.statusCode())
                .withFailMessage("Patient creation failed: %s", created.body())
                .isEqualTo(201);
        return JsonPath.read(created.body(), "$.id");
    }

    private String createAppointment(HttpClient client, String patientId, String startsAt) throws Exception {
        var created = mutation(client, "POST", "/api/v1/appointments", """
                {
                  "patientId": "%s",
                  "startsAt": "%s",
                  "durationMinutes": 15
                }
                """.formatted(patientId, startsAt));
        assertThat(created.statusCode())
                .withFailMessage("Appointment creation failed: %s", created.body())
                .isEqualTo(201);
        return JsonPath.read(created.body(), "$.id");
    }

    private void setStatus(String appointmentId, String status) {
        assertThat(jdbc.update(
                        "UPDATE appointment SET status = ? WHERE id = ?", status, UUID.fromString(appointmentId)))
                .isEqualTo(1);
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

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        TestClock appointmentReminderTestClock() {
            return new TestClock(Instant.parse("2030-01-14T12:00:00Z"));
        }
    }

    static class TestClock extends Clock {

        private final AtomicReference<Instant> current;

        TestClock(Instant initialInstant) {
            current = new AtomicReference<>(initialInstant);
        }

        void set(Instant instant) {
            current.set(instant);
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
            return current.get();
        }
    }
}
