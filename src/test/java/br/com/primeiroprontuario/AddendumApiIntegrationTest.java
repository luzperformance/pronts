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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

@Testcontainers
@SpringBootTest(
        classes = PrimeiroProntuarioApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.doctor.username=doctor",
            "app.doctor.password=valid-test-password",
            "server.servlet.session.cookie.secure=false"
        })
@Import(AddendumApiIntegrationTest.TestClockConfiguration.class)
class AddendumApiIntegrationTest extends DrizzleSpringIntegrationTest {

    @Container
    @ServiceConnection
    private static final DrizzlePostgreSQLContainer POSTGRESQL = new DrizzlePostgreSQLContainer();

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
    void draftConsultationRejectsAddendumWithConflict() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.021-43");
        var consultationId = createConsultation(client, patientId, "{}");

        var rejected = addAddendum(client, consultationId, """
                {
                  "content": "Complementação clínica",
                  "justification": "Informação recebida depois"
                }
                """);

        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(rejected.body(), "$.type")).isEqualTo("urn:problem:conflict");
        assertThat(rejected.body()).doesNotContain("Complementação clínica", "Informação recebida depois");
    }

    @Test
    void blankContentAndJustificationReturnInvalidRequest() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.022-24");
        var consultationId = createConsultation(client, patientId, completeClinicalContent());
        finalizeConsultation(client, consultationId);

        var rejected = addAddendum(client, consultationId, """
                {
                  "content": "   ",
                  "justification": " "
                }
                """);

        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(rejected.body(), "$.errors[*].field"))
                .containsExactly("content", "justification");
        assertThat(rejected.body()).doesNotContain("História clínica");
    }

    @Test
    void validAddendumUsesServerMetadataAndAppearsWithOriginalInDetailsAndMinimalAudit() throws Exception {
        var client = authenticatedClient();
        var doctorId = JsonPath.<String>read(get(client, "/api/v1/auth/me").body(), "$.id");
        var patientId = createPatient(client, "100.000.023-05");
        var consultationId = createConsultation(client, patientId, completeClinicalContent());
        finalizeConsultation(client, consultationId);
        clock.set(Instant.parse("2030-01-16T14:15:16.123456Z"));

        var created = addAddendum(client, consultationId, """
                {
                  "content": "Complementação clínica após laudo",
                  "justification": "Laudo recebido depois da finalização"
                }
                """);

        assertThat(created.statusCode()).isEqualTo(201);
        var addendumId = JsonPath.<String>read(created.body(), "$.id");
        assertThat(created.headers().firstValue("Location"))
                .contains("/api/v1/consultations/" + consultationId + "/addenda/" + addendumId);
        assertThat(JsonPath.<String>read(created.body(), "$.consultationId")).isEqualTo(consultationId);
        assertThat(JsonPath.<String>read(created.body(), "$.authorId")).isEqualTo(doctorId);
        assertThat(JsonPath.<String>read(created.body(), "$.createdAt")).isEqualTo("2030-01-16T14:15:16.123456Z");

        var details = get(client, "/api/v1/consultations/" + consultationId);
        assertThat(JsonPath.<String>read(details.body(), "$.anamnesis")).isEqualTo("História clínica");
        assertThat(JsonPath.<String>read(details.body(), "$.chiefComplaint")).isEqualTo("Cefaleia");
        assertThat(JsonPath.<List<String>>read(details.body(), "$.addenda[*].id"))
                .containsExactly(addendumId);
        assertThat(JsonPath.<String>read(details.body(), "$.addenda[0].content"))
                .isEqualTo("Complementação clínica após laudo");
        assertThat(JsonPath.<String>read(details.body(), "$.addenda[0].justification"))
                .isEqualTo("Laudo recebido depois da finalização");
        assertThat(JsonPath.<String>read(details.body(), "$.addenda[0].authorId"))
                .isEqualTo(doctorId);
        assertThat(JsonPath.<String>read(details.body(), "$.addenda[0].createdAt"))
                .isEqualTo("2030-01-16T14:15:16.123456Z");

        var audit = get(client, "/api/v1/audit-events?action=ADDENDUM_ADDED");
        var matchingEvents = JsonPath.<List<Map<String, Object>>>read(
                audit.body(), "$.content[?(@.targetId == '" + addendumId + "')]");
        assertThat(matchingEvents).singleElement().satisfies(event -> {
            assertThat(event.get("actorId")).isEqualTo(doctorId);
            assertThat(event.get("action")).isEqualTo("ADDENDUM_ADDED");
            assertThat(event.get("targetType")).isEqualTo("ADDENDUM");
            assertThat(event.get("changedFields")).isEqualTo(List.of());
        });
        assertThat(audit.body())
                .doesNotContain("Complementação clínica após laudo", "Laudo recebido depois da finalização");
    }

    @Test
    void medicalRecordShowsAddendaInStableInstantAndIdOrderAlongsideOriginal() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.024-96");
        var consultationId = createConsultation(client, patientId, completeClinicalContent());
        finalizeConsultation(client, consultationId);
        clock.set(Instant.parse("2030-01-16T14:15:16Z"));

        var firstCreated = addAddendum(client, consultationId, """
                {"content":"Adendo inserido primeiro","justification":"Primeira justificativa"}
                """);
        var secondCreated = addAddendum(client, consultationId, """
                {"content":"Adendo inserido depois","justification":"Segunda justificativa"}
                """);
        var firstId = JsonPath.<String>read(firstCreated.body(), "$.id");
        var secondId = JsonPath.<String>read(secondCreated.body(), "$.id");

        var medicalRecord = get(client, "/api/v1/patients/" + patientId + "/medical-record");

        assertThat(medicalRecord.statusCode()).isEqualTo(200);
        var orderedIds = List.of(firstId, secondId).stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        assertThat(JsonPath.<List<String>>read(medicalRecord.body(), "$.content[0].addenda[*].id"))
                .containsExactlyElementsOf(orderedIds);
        assertThat(JsonPath.<List<String>>read(medicalRecord.body(), "$.content[0].addenda[*].content"))
                .containsExactly(
                        orderedIds.get(0).equals(firstId) ? "Adendo inserido primeiro" : "Adendo inserido depois",
                        orderedIds.get(1).equals(firstId) ? "Adendo inserido primeiro" : "Adendo inserido depois");
        assertThat(JsonPath.<List<String>>read(medicalRecord.body(), "$.content[0].addenda[*].createdAt"))
                .containsExactly("2030-01-16T11:15:16-03:00", "2030-01-16T11:15:16-03:00");
        assertThat(JsonPath.<String>read(medicalRecord.body(), "$.content[0].anamnesis"))
                .isEqualTo("História clínica");
    }

    @Test
    void auditFailureRollsBackAddendumAndAuditTogether() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.025-77");
        var consultationId = createConsultation(client, patientId, completeClinicalContent());
        finalizeConsultation(client, consultationId);
        var auditBefore = get(client, "/api/v1/audit-events?action=ADDENDUM_ADDED");
        var auditCountBefore = JsonPath.<Integer>read(auditBefore.body(), "$.totalElements");
        executeAsMigration(POSTGRESQL, """
                ALTER TABLE audit_event
                ADD CONSTRAINT pp015_force_audit_failure
                CHECK (action <> 'ADDENDUM_ADDED') NOT VALID
                """);

        HttpResponse<String> failed;
        try {
            failed = addAddendum(client, consultationId, """
                    {
                      "content": "Texto que deve sofrer rollback",
                      "justification": "Auditoria indisponível"
                    }
                    """);
        } finally {
            executeAsMigration(POSTGRESQL, "ALTER TABLE audit_event DROP CONSTRAINT pp015_force_audit_failure");
        }

        assertThat(failed.statusCode()).isEqualTo(500);
        var details = get(client, "/api/v1/consultations/" + consultationId);
        assertThat(JsonPath.<List<String>>read(details.body(), "$.addenda[*].id"))
                .isEmpty();
        assertThat(details.body()).doesNotContain("Texto que deve sofrer rollback", "Auditoria indisponível");
        var audit = get(client, "/api/v1/audit-events?action=ADDENDUM_ADDED");
        assertThat(JsonPath.<Integer>read(audit.body(), "$.totalElements")).isEqualTo(auditCountBefore);
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
        assertThat(created.statusCode()).isEqualTo(201);
        return JsonPath.read(created.body(), "$.id");
    }

    private String createConsultation(HttpClient client, String patientId, String body) throws Exception {
        var created = mutation(client, "POST", "/api/v1/patients/" + patientId + "/consultations", body);
        assertThat(created.statusCode()).isEqualTo(201);
        return JsonPath.read(created.body(), "$.id");
    }

    private HttpResponse<String> addAddendum(HttpClient client, String consultationId, String body) throws Exception {
        return mutation(client, "POST", "/api/v1/consultations/" + consultationId + "/addenda", body);
    }

    private void finalizeConsultation(HttpClient client, String consultationId) throws Exception {
        var finalized = mutation(client, "POST", "/api/v1/consultations/" + consultationId + "/finalization", "{}");
        assertThat(finalized.statusCode()).isEqualTo(200);
    }

    private String completeClinicalContent() {
        return """
                {
                  "anamnesis": "História clínica",
                  "chiefComplaint": "Cefaleia",
                  "physicalExamination": "Sem alterações",
                  "diagnosticHypotheses": "Cefaleia tensional",
                  "treatmentPlan": "Orientação e acompanhamento",
                  "observations": "Retorno se necessário"
                }
                """;
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
        TestClock addendumTestClock() {
            return new TestClock(Instant.parse("2030-01-15T12:00:00Z"));
        }
    }

    static class TestClock extends Clock {

        private volatile Instant instant;

        TestClock(Instant initialInstant) {
            instant = initialInstant;
        }

        void set(Instant instant) {
            this.instant = instant;
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
            return instant;
        }
    }
}
