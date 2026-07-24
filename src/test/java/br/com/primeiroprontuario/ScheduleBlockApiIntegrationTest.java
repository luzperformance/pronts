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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@Import(ScheduleBlockApiIntegrationTest.FixedClockConfiguration.class)
@SpringBootTest(
        classes = PrimeiroProntuarioApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.doctor.username=doctor",
            "app.doctor.password=valid-test-password",
            "app.time-zone=America/Sao_Paulo",
            "server.servlet.session.cookie.secure=false"
        })
class ScheduleBlockApiIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18.4");

    @LocalServerPort
    private int port;

    @Autowired
    private TestClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetClock() {
        clock.set(Instant.parse("2030-01-15T12:00:00Z"));
    }

    @Test
    void futureFreeIntervalCanBeBlockedListedAndAudited() throws Exception {
        var client = authenticatedClient();

        var created = createBlock(client, "2030-01-15T10:00:00", "2030-01-15T10:45:00", "Reunião administrativa");

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location")).isPresent();
        var blockId = JsonPath.<String>read(created.body(), "$.id");
        assertThat(JsonPath.<String>read(created.body(), "$.startsAt")).isEqualTo("2030-01-15T10:00:00-03:00");
        assertThat(JsonPath.<String>read(created.body(), "$.endsAt")).isEqualTo("2030-01-15T10:45:00-03:00");
        assertThat(JsonPath.<String>read(created.body(), "$.reason")).isEqualTo("Reunião administrativa");
        assertThat(JsonPath.<String>read(created.body(), "$.createdAt")).isEqualTo("2030-01-15T09:00:00-03:00");

        var listed = get(client, "/api/v1/schedule-blocks?from=2030-01-15T09:45:00&to=2030-01-15T10:15:00");
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(listed.body(), "$[*].id")).containsExactly(blockId);

        var audit = get(client, "/api/v1/audit-events?action=SCHEDULE_BLOCK_CREATED&size=100");
        assertThat(audit.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(audit.body(), "$.content[?(@.targetId == '" + blockId + "')].action"))
                .containsExactly("SCHEDULE_BLOCK_CREATED");
    }

    @Test
    void blockPreventsAppointmentsUntilAFutureRemovalReleasesTheInterval() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "280.012.389-38");
        var createdBlock = createBlock(client, "2030-01-16T10:00:00", "2030-01-16T10:30:00", "Compromisso externo");
        var blockId = JsonPath.<String>read(createdBlock.body(), "$.id");

        var conflictingAppointment = createAppointment(client, patientId, "2030-01-16T10:15:00", 30);

        assertThat(conflictingAppointment.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(conflictingAppointment.body(), "$.type"))
                .isEqualTo("urn:problem:conflict");

        var removed = mutation(client, "DELETE", "/api/v1/schedule-blocks/" + blockId, "");
        assertThat(removed.statusCode()).isEqualTo(204);

        var releasedAppointment = createAppointment(client, patientId, "2030-01-16T10:15:00", 30);
        assertThat(releasedAppointment.statusCode()).isEqualTo(201);

        var audit = get(client, "/api/v1/audit-events?action=SCHEDULE_BLOCK_REMOVED&size=100");
        assertThat(JsonPath.<List<String>>read(audit.body(), "$.content[?(@.targetId == '" + blockId + "')].action"))
                .containsExactly("SCHEDULE_BLOCK_REMOVED");
    }

    @Test
    void blockOverAnActiveAppointmentOrAnotherBlockReturnsConflict() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "862.883.667-57");
        assertThat(createAppointment(client, patientId, "2030-01-17T10:00:00", 30)
                        .statusCode())
                .isEqualTo(201);

        var overAppointment = createBlock(client, "2030-01-17T10:15:00", "2030-01-17T10:45:00", "Intervalo ocupado");
        assertThat(overAppointment.statusCode()).isEqualTo(409);

        assertThat(createBlock(client, "2030-01-17T11:00:00", "2030-01-17T11:30:00", "Primeiro bloqueio")
                        .statusCode())
                .isEqualTo(201);
        var overBlock = createBlock(client, "2030-01-17T11:15:00", "2030-01-17T11:45:00", "Segundo bloqueio");

        assertThat(overBlock.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(overBlock.body(), "$.type")).isEqualTo("urn:problem:conflict");
    }

    @Test
    void intervalsTouchingABlockBoundaryRemainAvailable() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "168.995.350-09");
        assertThat(createBlock(client, "2030-01-18T10:00:00", "2030-01-18T11:00:00", "Reunião")
                        .statusCode())
                .isEqualTo(201);

        var endingAtBlockStart = createAppointment(client, patientId, "2030-01-18T09:45:00", 15);
        var startingAtBlockEnd = createAppointment(client, patientId, "2030-01-18T11:00:00", 15);
        assertThat(createBlock(client, "2030-01-18T12:00:00", "2030-01-18T13:00:00", "Almoço")
                        .statusCode())
                .isEqualTo(201);
        var blockEndingAtBlockStart = createBlock(client, "2030-01-18T11:30:00", "2030-01-18T12:00:00", "Preparação");
        var blockStartingAtBlockEnd = createBlock(client, "2030-01-18T13:00:00", "2030-01-18T13:30:00", "Retorno");

        assertThat(endingAtBlockStart.statusCode()).isEqualTo(201);
        assertThat(startingAtBlockEnd.statusCode()).isEqualTo(201);
        assertThat(blockEndingAtBlockStart.statusCode()).isEqualTo(201);
        assertThat(blockStartingAtBlockEnd.statusCode()).isEqualTo(201);
    }

    @Test
    void blockAndSearchRequireAValidFutureIntervalAndReason() throws Exception {
        var client = authenticatedClient();

        var notFuture = createBlock(client, "2030-01-15T09:00:00", "2030-01-15T09:30:00", "Horário atual");
        var reversed = createBlock(client, "2030-01-19T11:00:00", "2030-01-19T10:00:00", "Intervalo inválido");
        var blankReason = createBlock(client, "2030-01-19T10:00:00", "2030-01-19T10:30:00", "   ");
        var missingPeriod = get(client, "/api/v1/schedule-blocks?from=2030-01-19T10:00:00");

        assertThat(notFuture.statusCode()).isEqualTo(409);
        assertThat(reversed.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(reversed.body(), "$.errors[*].field"))
                .containsExactly("endsAt");
        assertThat(blankReason.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(blankReason.body(), "$.errors[*].field"))
                .containsExactly("reason");
        assertThat(missingPeriod.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(missingPeriod.body(), "$.errors[*].field"))
                .containsExactly("to");
    }

    @Test
    void blockSearchRejectsAnOversizedPage() throws Exception {
        var client = authenticatedClient();

        var response = get(client, "/api/v1/schedule-blocks?from=2030-01-19T10:00:00&to=2030-01-19T11:00:00&size=101");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].field")).isEqualTo("size");
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].message"))
                .isEqualTo("deve estar entre 1 e 100");
    }

    @Test
    void aBlockThatIsNoLongerFutureCannotBeRemoved() throws Exception {
        var client = authenticatedClient();
        var created = createBlock(client, "2030-01-15T09:15:00", "2030-01-15T09:30:00", "Pausa reservada");
        var blockId = JsonPath.<String>read(created.body(), "$.id");
        clock.set(Instant.parse("2030-01-15T12:16:00Z"));

        var removal = mutation(client, "DELETE", "/api/v1/schedule-blocks/" + blockId, "");

        assertThat(removal.statusCode()).isEqualTo(409);
        var listed = get(client, "/api/v1/schedule-blocks?from=2030-01-15T09:00:00&to=2030-01-15T10:00:00");
        assertThat(JsonPath.<List<String>>read(listed.body(), "$[*].id")).contains(blockId);
    }

    @Test
    void blockAndCreationAuditRollBackTogether() throws Exception {
        var client = authenticatedClient();
        jdbc.execute("""
                ALTER TABLE audit_event
                ADD CONSTRAINT audit_event_reject_schedule_block_created
                CHECK (action <> 'SCHEDULE_BLOCK_CREATED')
                NOT VALID
                """);

        HttpResponse<String> failedCreation;
        try {
            failedCreation = createBlock(client, "2030-01-21T10:00:00", "2030-01-21T10:30:00", "Auditoria obrigatória");
        } finally {
            jdbc.execute("ALTER TABLE audit_event DROP CONSTRAINT audit_event_reject_schedule_block_created");
        }

        var retriedCreation =
                createBlock(client, "2030-01-21T10:00:00", "2030-01-21T10:30:00", "Auditoria obrigatória");

        assertThat(failedCreation.statusCode()).isEqualTo(500);
        assertThat(retriedCreation.statusCode()).isEqualTo(201);
    }

    @Test
    void blockAndRemovalAuditRollBackTogether() throws Exception {
        var client = authenticatedClient();
        var created = createBlock(client, "2030-01-22T10:00:00", "2030-01-22T10:30:00", "Auditoria obrigatória");
        assertThat(created.statusCode()).isEqualTo(201);
        var blockId = JsonPath.<String>read(created.body(), "$.id");
        jdbc.execute("""
                ALTER TABLE audit_event
                ADD CONSTRAINT audit_event_reject_schedule_block_removed
                CHECK (action <> 'SCHEDULE_BLOCK_REMOVED')
                NOT VALID
                """);

        HttpResponse<String> failedRemoval;
        try {
            failedRemoval = mutation(client, "DELETE", "/api/v1/schedule-blocks/" + blockId, "");
        } finally {
            jdbc.execute("ALTER TABLE audit_event DROP CONSTRAINT audit_event_reject_schedule_block_removed");
        }

        var listed = get(client, "/api/v1/schedule-blocks?from=2030-01-22T09:00:00&to=2030-01-22T11:00:00");
        var retriedRemoval = mutation(client, "DELETE", "/api/v1/schedule-blocks/" + blockId, "");

        assertThat(failedRemoval.statusCode()).isEqualTo(500);
        assertThat(JsonPath.<List<String>>read(listed.body(), "$[*].id")).containsExactly(blockId);
        assertThat(retriedRemoval.statusCode()).isEqualTo(204);
    }

    @Test
    void concurrentBlockAndAppointmentRequestsCannotCreateAnOverlap() throws Exception {
        var blockClient = authenticatedClient();
        var appointmentClient = authenticatedClient();
        var patientId = createPatient(appointmentClient, "935.411.347-80");
        var blockCsrf = csrf(blockClient);
        var appointmentCsrf = csrf(appointmentClient);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        HttpResponse<String> blockResponse;
        HttpResponse<String> appointmentResponse;
        try {
            var blockFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return send(
                        blockClient,
                        "POST",
                        "/api/v1/schedule-blocks",
                        blockJson("2030-01-23T10:00:00", "2030-01-23T10:30:00", "Disputa de agenda"),
                        blockCsrf.headerName(),
                        blockCsrf.token());
            });
            var appointmentFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return send(
                        appointmentClient,
                        "POST",
                        "/api/v1/appointments",
                        appointmentJson(patientId, "2030-01-23T10:00:00", 30),
                        appointmentCsrf.headerName(),
                        appointmentCsrf.token());
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            blockResponse = blockFuture.get(10, TimeUnit.SECONDS);
            appointmentResponse = appointmentFuture.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(List.of(blockResponse.statusCode(), appointmentResponse.statusCode()).stream()
                        .sorted()
                        .toList())
                .containsExactly(201, 409);
        var blocks = get(blockClient, "/api/v1/schedule-blocks?from=2030-01-23T10:00:00&to=2030-01-23T10:30:00");
        var appointments = get(
                appointmentClient,
                "/api/v1/appointments?from=2030-01-23T10:00:00" + "&to=2030-01-23T10:30:00&patientId=" + patientId);
        var occupiedIntervals =
                JsonPath.<List<String>>read(blocks.body(), "$[*].id").size()
                        + JsonPath.<List<String>>read(appointments.body(), "$.content[*].id")
                                .size();
        assertThat(occupiedIntervals).isEqualTo(1);
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

    private HttpResponse<String> createBlock(HttpClient client, String startsAt, String endsAt, String reason)
            throws Exception {
        return mutation(client, "POST", "/api/v1/schedule-blocks", blockJson(startsAt, endsAt, reason));
    }

    private String blockJson(String startsAt, String endsAt, String reason) {
        return """
                {
                  "startsAt": "%s",
                  "endsAt": "%s",
                  "reason": "%s"
                }
                """.formatted(startsAt, endsAt, reason);
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

    private HttpResponse<String> createAppointment(
            HttpClient client, String patientId, String startsAt, int durationMinutes) throws Exception {
        return mutation(client, "POST", "/api/v1/appointments", appointmentJson(patientId, startsAt, durationMinutes));
    }

    private String appointmentJson(String patientId, String startsAt, int durationMinutes) {
        return """
                {
                  "patientId": "%s",
                  "startsAt": "%s",
                  "durationMinutes": %d
                }
                """.formatted(patientId, startsAt, durationMinutes);
    }

    private Csrf csrf(HttpClient client) throws Exception {
        var response = get(client, "/api/v1/auth/csrf");
        return new Csrf(JsonPath.read(response.body(), "$.headerName"), JsonPath.read(response.body(), "$.token"));
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

    private record Csrf(String headerName, String token) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        TestClock scheduleBlockTestClock() {
            return new TestClock(Instant.parse("2030-01-15T12:00:00Z"));
        }
    }

    static class TestClock extends Clock {

        private final AtomicReference<Instant> current;

        TestClock(Instant initialInstant) {
            this.current = new AtomicReference<>(initialInstant);
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
