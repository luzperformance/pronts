package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class ConsultationApiIntegrationTest extends DrizzleSpringIntegrationTest {

    @Container
    @ServiceConnection
    private static final DrizzlePostgreSQLContainer POSTGRESQL = new DrizzlePostgreSQLContainer();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void activePatientCanCreateAndRetrieveAnIncompleteUnscheduledDraft() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "529.982.247-25");

        var created = createConsultation(client, patientId, """
                {
                  "anamnesis": "História ainda parcial",
                  "chiefComplaint": null,
                  "physicalExamination": null,
                  "diagnosticHypotheses": null,
                  "treatmentPlan": null,
                  "observations": null
                }
                """);

        assertThat(created.statusCode()).isEqualTo(201);
        var consultationId = JsonPath.<String>read(created.body(), "$.id");
        assertThat(created.headers().firstValue("Location")).contains("/api/v1/consultations/" + consultationId);
        assertThat(JsonPath.<String>read(created.body(), "$.patientId")).isEqualTo(patientId);
        assertThat(JsonPath.<String>read(created.body(), "$.anamnesis")).isEqualTo("História ainda parcial");
        assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("DRAFT");
        assertThat(JsonPath.<Integer>read(created.body(), "$.version")).isZero();
        assertThat(JsonPath.<Map<String, Object>>read(created.body(), "$"))
                .containsOnlyKeys(
                        "id",
                        "patientId",
                        "appointmentId",
                        "anamnesis",
                        "chiefComplaint",
                        "physicalExamination",
                        "diagnosticHypotheses",
                        "treatmentPlan",
                        "observations",
                        "status",
                        "finalizedBy",
                        "finalizedAt",
                        "version");

        var retrieved = get(client, "/api/v1/consultations/" + consultationId);
        assertThat(retrieved.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.anamnesis")).isEqualTo("História ainda parcial");
        assertThat(JsonPath.<List<String>>read(retrieved.body(), "$.addenda[*].id"))
                .isEmpty();
    }

    @Test
    void knownVersionEditsAllClinicalFieldsAndKeepsTheConsultationAsDraft() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "862.883.667-57");
        var created = createConsultation(client, patientId, "{}");
        var consultationId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");

        var updated = updateConsultation(client, consultationId, """
                {
                  "anamnesis": "História clínica completa",
                  "chiefComplaint": "Dor de cabeça",
                  "physicalExamination": "Exame sem alterações",
                  "diagnosticHypotheses": "Cefaleia tensional",
                  "treatmentPlan": "Orientação e acompanhamento",
                  "observations": "Retorno se necessário",
                  "version": %d
                }
                """.formatted(knownVersion));

        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(updated.body(), "$.anamnesis")).isEqualTo("História clínica completa");
        assertThat(JsonPath.<String>read(updated.body(), "$.chiefComplaint")).isEqualTo("Dor de cabeça");
        assertThat(JsonPath.<String>read(updated.body(), "$.physicalExamination"))
                .isEqualTo("Exame sem alterações");
        assertThat(JsonPath.<String>read(updated.body(), "$.diagnosticHypotheses"))
                .isEqualTo("Cefaleia tensional");
        assertThat(JsonPath.<String>read(updated.body(), "$.treatmentPlan")).isEqualTo("Orientação e acompanhamento");
        assertThat(JsonPath.<String>read(updated.body(), "$.observations")).isEqualTo("Retorno se necessário");
        assertThat(JsonPath.<String>read(updated.body(), "$.status")).isEqualTo("DRAFT");
        assertThat(JsonPath.<Integer>read(updated.body(), "$.version")).isEqualTo(knownVersion + 1);
        var retrieved = get(client, "/api/v1/consultations/" + consultationId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.anamnesis")).isEqualTo("História clínica completa");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion + 1);
        assertThat(JsonPath.<List<String>>read(retrieved.body(), "$.addenda[*].id"))
                .isEmpty();
    }

    @Test
    void appointmentFromAnotherPatientCannotBeLinked() throws Exception {
        var client = authenticatedClient();
        var consultationPatientId = createPatient(client, "100.000.001-08");
        var appointmentPatientId = createPatient(client, "100.000.002-80");
        var appointmentId = createAppointment(client, appointmentPatientId, "2030-03-01T10:00:00");

        var rejected = createConsultation(client, consultationPatientId, """
                {"appointmentId":"%s"}
                """.formatted(appointmentId));

        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(rejected.body(), "$.type")).isEqualTo("urn:problem:conflict");
        assertThat(rejected.body()).doesNotContain(appointmentId);
    }

    @Test
    void appointmentAlreadyLinkedToAConsultationCannotBeReused() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.003-61");
        var appointmentId = createAppointment(client, patientId, "2030-03-02T10:00:00");
        var linked = createConsultation(client, patientId, """
                {"appointmentId":"%s"}
                """.formatted(appointmentId));

        var rejected = createConsultation(client, patientId, """
                {"appointmentId":"%s"}
                """.formatted(appointmentId));

        assertThat(linked.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(linked.body(), "$.appointmentId")).isEqualTo(appointmentId);
        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(rejected.body(), "$.type")).isEqualTo("urn:problem:conflict");
        assertThat(rejected.body()).doesNotContain(appointmentId);
    }

    @Test
    void inactivePatientCannotReceiveAConsultation() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.004-42");
        inactivatePatient(client, patientId);

        var rejected = createConsultation(client, patientId, "{}");

        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(rejected.body(), "$.detail")).isEqualTo("O paciente não está ativo.");
    }

    @Test
    void consultationUpdateRequiresTheKnownVersion() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.005-23");
        var created = createConsultation(client, patientId, "{}");
        var consultationId = JsonPath.<String>read(created.body(), "$.id");

        var rejected = updateConsultation(client, consultationId, """
                {"anamnesis":"Tentativa sem versão"}
                """);

        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(rejected.body(), "$.errors[*].field"))
                .containsExactly("version");
        assertThat(rejected.body()).doesNotContain("Tentativa sem versão");
    }

    @Test
    void staleVersionDoesNotOverwriteTheConfirmedDraft() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.006-04");
        var created = createConsultation(client, patientId, "{}");
        var consultationId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");

        var confirmed = updateConsultation(client, consultationId, updateJson("Conteúdo confirmado", knownVersion));
        var stale = updateConsultation(client, consultationId, updateJson("Conteúdo obsoleto", knownVersion));

        assertThat(confirmed.statusCode()).isEqualTo(200);
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(stale.body()).doesNotContain("Conteúdo confirmado").doesNotContain("Conteúdo obsoleto");
        var retrieved = get(client, "/api/v1/consultations/" + consultationId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.anamnesis")).isEqualTo("Conteúdo confirmado");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion + 1);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.status")).isEqualTo("DRAFT");
    }

    @Test
    void simultaneousUpdatesFromTheSameVersionDoNotSilentlyOverwriteEachOther() throws Exception {
        var firstClient = authenticatedClient();
        var secondClient = authenticatedClient();
        var patientId = createPatient(firstClient, "100.000.007-95");
        var created = createConsultation(firstClient, patientId, "{}");
        var consultationId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");
        var firstCsrf = csrf(firstClient);
        var secondCsrf = csrf(secondClient);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        HttpResponse<String> firstResponse;
        HttpResponse<String> secondResponse;
        try {
            var firstFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return send(
                        firstClient,
                        "PUT",
                        "/api/v1/consultations/" + consultationId,
                        updateJson("Primeira edição concorrente", knownVersion),
                        firstCsrf.headerName(),
                        firstCsrf.token());
            });
            var secondFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return send(
                        secondClient,
                        "PUT",
                        "/api/v1/consultations/" + consultationId,
                        updateJson("Segunda edição concorrente", knownVersion),
                        secondCsrf.headerName(),
                        secondCsrf.token());
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstResponse = firstFuture.get(10, TimeUnit.SECONDS);
            secondResponse = secondFuture.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(List.of(firstResponse.statusCode(), secondResponse.statusCode()).stream()
                        .sorted()
                        .toList())
                .containsExactly(200, 409);
        var retrieved = get(firstClient, "/api/v1/consultations/" + consultationId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.anamnesis"))
                .isIn("Primeira edição concorrente", "Segunda edição concorrente");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion + 1);
    }

    @Test
    void incompleteFinalizationEnumeratesAllMissingClinicalFieldsIncludingWhitespaceOnlyValues() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.008-76");
        var created = createConsultation(client, patientId, """
                {
                  "anamnesis": null,
                  "chiefComplaint": "",
                  "physicalExamination": "   ",
                  "diagnosticHypotheses": null,
                  "treatmentPlan": " ",
                  "observations": null
                }
                """);
        var consultationId = JsonPath.<String>read(created.body(), "$.id");

        var rejected = finalizeConsultation(client, consultationId);

        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(rejected.body(), "$.errors[*].field"))
                .containsExactly(
                        "anamnesis",
                        "chiefComplaint",
                        "physicalExamination",
                        "diagnosticHypotheses",
                        "treatmentPlan",
                        "observations");
    }

    @Test
    void validFinalizationPersistsFinalStateAuthenticatedAuthorAndServerInstant() throws Exception {
        var client = authenticatedClient();
        var doctor = get(client, "/api/v1/auth/me");
        var doctorId = JsonPath.<String>read(doctor.body(), "$.id");
        var patientId = createPatient(client, "100.000.009-57");
        var created = createConsultation(client, patientId, completeClinicalContent());
        var consultationId = JsonPath.<String>read(created.body(), "$.id");

        var finalized = finalizeConsultation(client, consultationId);

        assertThat(finalized.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(finalized.body(), "$.status")).isEqualTo("FINALIZED");
        assertThat(JsonPath.<String>read(finalized.body(), "$.finalizedBy")).isEqualTo(doctorId);
        assertThat(Instant.parse(JsonPath.read(finalized.body(), "$.finalizedAt")))
                .isNotNull();
        var retrieved = get(client, "/api/v1/consultations/" + consultationId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.status")).isEqualTo("FINALIZED");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.finalizedBy")).isEqualTo(doctorId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.finalizedAt"))
                .isEqualTo(JsonPath.read(finalized.body(), "$.finalizedAt"));
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version"))
                .isEqualTo(JsonPath.read(finalized.body(), "$.version"));
    }

    @Test
    void finalizationCreatesClinicalContentFreeAuditInTheSameBusinessFlow() throws Exception {
        var client = authenticatedClient();
        var doctorId = JsonPath.<String>read(get(client, "/api/v1/auth/me").body(), "$.id");
        var patientId = createPatient(client, "100.000.010-90");
        var created = createConsultation(client, patientId, completeClinicalContent());
        var consultationId = JsonPath.<String>read(created.body(), "$.id");

        var finalized = finalizeConsultation(client, consultationId);
        var audit = get(client, "/api/v1/audit-events?action=CONSULTATION_FINALIZED");

        assertThat(finalized.statusCode()).isEqualTo(200);
        var matchingEvents = JsonPath.<List<Map<String, Object>>>read(
                audit.body(), "$.content[?(@.targetId == '" + consultationId + "')]");
        assertThat(matchingEvents).singleElement().satisfies(event -> {
            assertThat(event.get("actorId")).isEqualTo(doctorId);
            assertThat(event.get("action")).isEqualTo("CONSULTATION_FINALIZED");
            assertThat(event.get("targetType")).isEqualTo("CONSULTATION");
            assertThat(event.get("changedFields")).isEqualTo(List.of());
        });
        assertThat(audit.body())
                .doesNotContain(
                        "História clínica",
                        "Cefaleia",
                        "Sem alterações",
                        "Cefaleia tensional",
                        "Orientação e acompanhamento",
                        "Retorno se necessário");
    }

    @Test
    void repeatedFinalizationIsReportedAndDoesNotDuplicateMetadataVersionOrAudit() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.011-71");
        var created = createConsultation(client, patientId, completeClinicalContent());
        var consultationId = JsonPath.<String>read(created.body(), "$.id");

        var first = finalizeConsultation(client, consultationId);
        var repeated = finalizeConsultation(client, consultationId);
        var audit = get(client, "/api/v1/audit-events?action=CONSULTATION_FINALIZED");

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(first.body(), "$.alreadyFinalized")).isFalse();
        assertThat(JsonPath.<Boolean>read(repeated.body(), "$.alreadyFinalized"))
                .isTrue();
        assertThat(JsonPath.<String>read(repeated.body(), "$.finalizedBy"))
                .isEqualTo(JsonPath.read(first.body(), "$.finalizedBy"));
        assertThat(JsonPath.<String>read(repeated.body(), "$.finalizedAt"))
                .isEqualTo(JsonPath.read(first.body(), "$.finalizedAt"));
        assertThat(JsonPath.<Integer>read(repeated.body(), "$.version"))
                .isEqualTo(JsonPath.read(first.body(), "$.version"));
        assertThat(JsonPath.<List<Map<String, Object>>>read(
                        audit.body(), "$.content[?(@.targetId == '" + consultationId + "')]"))
                .hasSize(1);
    }

    @Test
    void finalizedConsultationUpdateReturnsConflictAndKeepsFrozenContent() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.012-52");
        var created = createConsultation(client, patientId, completeClinicalContent());
        var consultationId = JsonPath.<String>read(created.body(), "$.id");
        var finalized = finalizeConsultation(client, consultationId);
        var finalizedVersion = JsonPath.<Integer>read(finalized.body(), "$.version");

        var rejected = updateConsultation(
                client, consultationId, updateJson("Conteúdo que não pode substituir o original", finalizedVersion));

        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(rejected.body(), "$.type")).isEqualTo("urn:problem:conflict");
        assertThat(rejected.body())
                .doesNotContain("Conteúdo que não pode substituir o original")
                .doesNotContain("História clínica");
        assertThat(JsonPath.<String>read(
                        get(client, "/api/v1/consultations/" + consultationId).body(), "$.anamnesis"))
                .isEqualTo("História clínica");
    }

    @Test
    void finalizationCompletesItsLinkedActiveAppointment() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.013-33");
        var appointmentId = createAppointment(client, patientId, "2030-03-03T10:00:00");
        var created = createConsultation(client, patientId, completeClinicalContent(appointmentId));
        var consultationId = JsonPath.<String>read(created.body(), "$.id");

        var finalized = finalizeConsultation(client, consultationId);
        var appointment = get(client, "/api/v1/appointments/" + appointmentId);

        assertThat(finalized.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(appointment.body(), "$.status")).isEqualTo("COMPLETED");
    }

    @Test
    void auditFailureRollsBackConsultationAppointmentAndAuditTogether() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.014-14");
        var appointmentId = createAppointment(client, patientId, "2030-03-04T10:00:00");
        var created = createConsultation(client, patientId, completeClinicalContent(appointmentId));
        var consultationId = JsonPath.<String>read(created.body(), "$.id");
        executeAsMigration(POSTGRESQL, """
                ALTER TABLE audit_event
                ADD CONSTRAINT pp013_force_audit_failure
                CHECK (target_id <> '%s'::uuid)
                """.formatted(consultationId));

        HttpResponse<String> failed;
        try {
            failed = finalizeConsultation(client, consultationId);
        } finally {
            executeAsMigration(POSTGRESQL, "ALTER TABLE audit_event DROP CONSTRAINT pp013_force_audit_failure");
        }

        assertThat(failed.statusCode()).isEqualTo(500);
        assertThat(JsonPath.<String>read(
                        get(client, "/api/v1/consultations/" + consultationId).body(), "$.status"))
                .isEqualTo("DRAFT");
        assertThat(JsonPath.<String>read(
                        get(client, "/api/v1/appointments/" + appointmentId).body(), "$.status"))
                .isEqualTo("SCHEDULED");
        var audit = get(client, "/api/v1/audit-events?action=CONSULTATION_FINALIZED");
        assertThat(JsonPath.<List<Map<String, Object>>>read(
                        audit.body(), "$.content[?(@.targetId == '" + consultationId + "')]"))
                .isEmpty();
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

    private HttpResponse<String> createConsultation(HttpClient client, String patientId, String body) throws Exception {
        return mutation(client, "POST", "/api/v1/patients/" + patientId + "/consultations", body);
    }

    private String createAppointment(HttpClient client, String patientId, String startsAt) throws Exception {
        var created = mutation(client, "POST", "/api/v1/appointments", """
                {
                  "patientId": "%s",
                  "startsAt": "%s",
                  "durationMinutes": 30
                }
                """.formatted(patientId, startsAt));
        assertThat(created.statusCode()).isEqualTo(201);
        return JsonPath.read(created.body(), "$.id");
    }

    private HttpResponse<String> updateConsultation(HttpClient client, String consultationId, String body)
            throws Exception {
        return mutation(client, "PUT", "/api/v1/consultations/" + consultationId, body);
    }

    private HttpResponse<String> finalizeConsultation(HttpClient client, String consultationId) throws Exception {
        return mutation(client, "POST", "/api/v1/consultations/" + consultationId + "/finalization", "{}");
    }

    private String updateJson(String anamnesis, int version) {
        return """
                {"anamnesis":"%s","version":%d}
                """.formatted(anamnesis, version);
    }

    private String completeClinicalContent() {
        return completeClinicalContent(null);
    }

    private String completeClinicalContent(String appointmentId) {
        return """
                {
                  %s
                  "anamnesis": "História clínica",
                  "chiefComplaint": "Cefaleia",
                  "physicalExamination": "Sem alterações",
                  "diagnosticHypotheses": "Cefaleia tensional",
                  "treatmentPlan": "Orientação e acompanhamento",
                  "observations": "Retorno se necessário"
                }
                """.formatted(appointmentId == null ? "" : "\"appointmentId\": \"" + appointmentId + "\",");
    }

    private void inactivatePatient(HttpClient client, String patientId) throws Exception {
        var patient = get(client, "/api/v1/patients/" + patientId);
        var version = JsonPath.<Integer>read(patient.body(), "$.version");
        var response = mutation(client, "PATCH", "/api/v1/patients/" + patientId + "/status", """
                {"status":"INACTIVE","version":%d}
                """.formatted(version));
        assertThat(response.statusCode()).isEqualTo(200);
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
}
