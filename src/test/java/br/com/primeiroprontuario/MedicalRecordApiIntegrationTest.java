package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
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
import org.hibernate.SessionFactory;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        classes = PrimeiroProntuarioApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.doctor.username=doctor",
            "app.doctor.password=valid-test-password",
            "server.servlet.session.cookie.secure=false",
            "spring.jpa.properties.hibernate.generate_statistics=true"
        })
@Import(MedicalRecordApiIntegrationTest.TestClockConfiguration.class)
class MedicalRecordApiIntegrationTest extends DrizzleSpringIntegrationTest {

    @Container
    @ServiceConnection
    private static final DrizzlePostgreSQLContainer POSTGRESQL = new DrizzlePostgreSQLContainer();

    @LocalServerPort
    private int port;

    @Autowired
    private TestClock clock;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void resetClock() {
        clock.set(Instant.parse("2030-01-15T12:00:00Z"));
    }

    @Test
    void medicalRecordListsOnlyFinalizedConsultationsInStableClinicalOrderWithOriginalContent() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.015-03");
        clock.set(Instant.parse("2030-01-14T12:00:00Z"));
        var firstTie = createConsultation(
                client, patientId, "2030-01-10T09:00:00", completeClinicalContent("Primeiro empate"));
        var secondTie =
                createConsultation(client, patientId, "2030-01-10T09:00:00", completeClinicalContent("Segundo empate"));
        clock.set(Instant.parse("2030-01-13T12:00:00Z"));
        var later = createConsultation(
                client, patientId, "2030-01-12T09:00:00", completeClinicalContent("Consulta posterior"));
        createConsultation(client, patientId, "2030-01-09T09:00:00", completeClinicalContent("Rascunho oculto"));
        clock.set(Instant.parse("2030-01-16T12:00:00Z"));
        finalizeConsultation(client, firstTie);
        finalizeConsultation(client, secondTie);
        finalizeConsultation(client, later);

        var response = get(client, "/api/v1/patients/" + patientId + "/medical-record");

        assertThat(response.statusCode()).isEqualTo(200);
        var tiedIds = List.of(firstTie, secondTie).stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        assertThat(JsonPath.<List<String>>read(response.body(), "$.content[*].id"))
                .containsExactly(tiedIds.get(0), tiedIds.get(1), later);
        assertThat(JsonPath.<List<String>>read(response.body(), "$.content[*].anamnesis"))
                .containsExactly(
                        tiedIds.get(0).equals(firstTie) ? "Primeiro empate" : "Segundo empate",
                        tiedIds.get(1).equals(firstTie) ? "Primeiro empate" : "Segundo empate",
                        "Consulta posterior");
        assertThat(JsonPath.<String>read(response.body(), "$.content[0].chiefComplaint"))
                .isEqualTo("Cefaleia");
        assertThat(JsonPath.<String>read(response.body(), "$.content[0].physicalExamination"))
                .isEqualTo("Sem alterações");
        assertThat(JsonPath.<String>read(response.body(), "$.content[0].diagnosticHypotheses"))
                .isEqualTo("Cefaleia tensional");
        assertThat(JsonPath.<String>read(response.body(), "$.content[0].treatmentPlan"))
                .isEqualTo("Orientação e acompanhamento");
        assertThat(JsonPath.<String>read(response.body(), "$.content[0].observations"))
                .isEqualTo("Retorno se necessário");
        assertThat(JsonPath.<String>read(response.body(), "$.content[0].clinicalDate"))
                .isEqualTo("2030-01-10T09:00:00-03:00");
        assertThat(JsonPath.<String>read(response.body(), "$.content[0].createdAt"))
                .isEqualTo("2030-01-14T09:00:00-03:00");
        assertThat(response.body()).doesNotContain("Rascunho oculto");
        assertThat(JsonPath.<Map<String, Object>>read(response.body(), "$.content[0]"))
                .containsOnlyKeys(
                        "id",
                        "clinicalDate",
                        "createdAt",
                        "anamnesis",
                        "chiefComplaint",
                        "physicalExamination",
                        "diagnosticHypotheses",
                        "treatmentPlan",
                        "observations",
                        "finalizedBy",
                        "finalizedAt",
                        "addenda");
    }

    @Test
    void medicalRecordUsesAnInclusiveFromExclusiveToIntervalAndStablePages() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.016-86");
        var before = createConsultation(
                client, patientId, "2030-01-10T09:00:00", completeClinicalContent("Antes do intervalo"));
        var atFrom = createConsultation(
                client, patientId, "2030-01-11T09:00:00", completeClinicalContent("No limite inicial"));
        var inside = createConsultation(
                client, patientId, "2030-01-12T09:00:00", completeClinicalContent("Dentro do intervalo"));
        var atTo = createConsultation(
                client, patientId, "2030-01-13T09:00:00", completeClinicalContent("No limite final"));
        for (var consultationId : List.of(before, atFrom, inside, atTo)) {
            finalizeConsultation(client, consultationId);
        }

        var firstPage = get(
                client,
                "/api/v1/patients/" + patientId
                        + "/medical-record?from=2030-01-11T09:00:00&to=2030-01-13T09:00:00&page=0&size=1");
        var secondPage = get(
                client,
                "/api/v1/patients/" + patientId
                        + "/medical-record?from=2030-01-11T09:00:00&to=2030-01-13T09:00:00&page=1&size=1");

        assertThat(firstPage.statusCode()).isEqualTo(200);
        assertThat(secondPage.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(firstPage.body(), "$.content[*].id"))
                .containsExactly(atFrom);
        assertThat(JsonPath.<List<String>>read(secondPage.body(), "$.content[*].id"))
                .containsExactly(inside);
        assertThat(JsonPath.<Integer>read(firstPage.body(), "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(secondPage.body(), "$.page")).isOne();
        assertThat(JsonPath.<Integer>read(firstPage.body(), "$.size")).isOne();
        assertThat(JsonPath.<Integer>read(firstPage.body(), "$.totalElements")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(firstPage.body(), "$.totalPages")).isEqualTo(2);
        assertThat(firstPage.body()).doesNotContain(before, atTo);
        assertThat(secondPage.body()).doesNotContain(before, atTo);
    }

    @Test
    void medicalRecordRejectsInvalidPeriodAndPaginationWithoutRunningAnUnboundedList() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.017-67");

        var invalidPeriod = get(
                client,
                "/api/v1/patients/" + patientId + "/medical-record?from=2030-01-11T09:00:00&to=2030-01-11T09:00:00");
        var invalidPage = get(client, "/api/v1/patients/" + patientId + "/medical-record?page=-1");
        var emptySize = get(client, "/api/v1/patients/" + patientId + "/medical-record?size=0");
        var excessiveSize = get(client, "/api/v1/patients/" + patientId + "/medical-record?size=101");

        assertThat(invalidPeriod.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(invalidPeriod.body(), "$.errors[*].field"))
                .containsExactly("to");
        assertThat(invalidPage.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(invalidPage.body(), "$.errors[*].field"))
                .containsExactly("page");
        assertThat(emptySize.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(emptySize.body(), "$.errors[*].field"))
                .containsExactly("size");
        assertThat(excessiveSize.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(excessiveSize.body(), "$.errors[*].field"))
                .containsExactly("size");
    }

    @Test
    void medicalRecordViewCreatesOnlyMinimalClinicalContentFreeAudit() throws Exception {
        var client = authenticatedClient();
        var doctorId = JsonPath.<String>read(get(client, "/api/v1/auth/me").body(), "$.id");
        var patientId = createPatient(client, "100.000.018-48");
        var consultationId = createConsultation(
                client, patientId, "2030-01-12T09:00:00", completeClinicalContent("Conteúdo clínico ultrassensível"));
        finalizeConsultation(client, consultationId);

        var medicalRecord = get(
                client,
                "/api/v1/patients/" + patientId + "/medical-record?from=2030-01-01T00:00:00&to=2030-02-01T00:00:00");
        var audit = get(client, "/api/v1/audit-events?action=MEDICAL_RECORD_VIEWED");

        assertThat(medicalRecord.statusCode()).isEqualTo(200);
        assertThat(audit.statusCode()).isEqualTo(200);
        var matchingEvents = JsonPath.<List<Map<String, Object>>>read(
                audit.body(), "$.content[?(@.targetId == '" + patientId + "')]");
        assertThat(matchingEvents).singleElement().satisfies(event -> {
            assertThat(event.get("actorId")).isEqualTo(doctorId);
            assertThat(event.get("action")).isEqualTo("MEDICAL_RECORD_VIEWED");
            assertThat(event.get("targetType")).isEqualTo("PATIENT");
            assertThat(event.get("changedFields")).isEqualTo(List.of());
        });
        assertThat(audit.body())
                .doesNotContain("Conteúdo clínico ultrassensível", "Cefaleia", "2030-01-01", "2030-02-01");
    }

    @Test
    void medicalRecordQueryCountDoesNotGrowWithTheNumberOfEntriesInThePage() throws Exception {
        var client = authenticatedClient();
        var oneEntryPatientId = createPatient(client, "100.000.019-29");
        var oneEntry = createConsultation(
                client, oneEntryPatientId, "2030-01-10T09:00:00", completeClinicalContent("Registro único"));
        finalizeConsultation(client, oneEntry);
        var fiveEntryPatientId = createPatient(client, "100.000.020-62");
        for (var day = 10; day <= 14; day++) {
            var consultationId = createConsultation(
                    client,
                    fiveEntryPatientId,
                    "2030-01-%dT09:00:00".formatted(day),
                    completeClinicalContent("Registro " + day));
            finalizeConsultation(client, consultationId);
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        var oneEntryPage = get(client, "/api/v1/patients/" + oneEntryPatientId + "/medical-record?size=10");
        var oneEntryStatementCount = statistics.getPrepareStatementCount();
        statistics.clear();
        var fiveEntryPage = get(client, "/api/v1/patients/" + fiveEntryPatientId + "/medical-record?size=10");
        var fiveEntryStatementCount = statistics.getPrepareStatementCount();

        assertThat(oneEntryPage.statusCode()).isEqualTo(200);
        assertThat(fiveEntryPage.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(oneEntryPage.body(), "$.content[*].id"))
                .hasSize(1);
        assertThat(JsonPath.<List<String>>read(fiveEntryPage.body(), "$.content[*].id"))
                .hasSize(5);
        assertThat(oneEntryStatementCount).isEqualTo(4);
        assertThat(fiveEntryStatementCount).isEqualTo(oneEntryStatementCount);
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

    private String createConsultation(HttpClient client, String patientId, String clinicalDate, String clinicalContent)
            throws Exception {
        var created = mutation(client, "POST", "/api/v1/patients/" + patientId + "/consultations", """
                {
                  "clinicalDate": "%s",
                  %s
                }
                """.formatted(
                        clinicalDate, clinicalContent));
        assertThat(created.statusCode()).isEqualTo(201);
        return JsonPath.read(created.body(), "$.id");
    }

    private void finalizeConsultation(HttpClient client, String consultationId) throws Exception {
        var finalized = mutation(client, "POST", "/api/v1/consultations/" + consultationId + "/finalization", "{}");
        assertThat(finalized.statusCode()).isEqualTo(200);
    }

    private String completeClinicalContent(String anamnesis) {
        return """
                "anamnesis": "%s",
                "chiefComplaint": "Cefaleia",
                "physicalExamination": "Sem alterações",
                "diagnosticHypotheses": "Cefaleia tensional",
                "treatmentPlan": "Orientação e acompanhamento",
                "observations": "Retorno se necessário"
                """.formatted(anamnesis);
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
        TestClock medicalRecordTestClock() {
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
