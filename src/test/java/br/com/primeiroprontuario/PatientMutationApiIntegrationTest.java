package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class PatientMutationApiIntegrationTest extends DrizzleSpringIntegrationTest {

    @Container
    @ServiceConnection
    private static final DrizzlePostgreSQLContainer POSTGRESQL = new DrizzlePostgreSQLContainer();

    @LocalServerPort
    private int port;

    @BeforeEach
    void clearBusinessData() {
        executeAsMigration(POSTGRESQL, """
                TRUNCATE TABLE
                    attachment,
                    addendum,
                    consultation,
                    schedule_block,
                    appointment,
                    patient,
                    audit_event
                """);
    }

    @Test
    void knownVersionUpdatesThePatientAndIncrementsItsVersion() throws Exception {
        var client = authenticatedClient();
        var created = postPatient(client, requiredPatientJson("529.982.247-25"));
        var patientId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");

        var updated = putPatient(client, patientId, """
                {
                  "fullName": "Ana Souza Corrigida",
                  "motherName": "Maria Souza Corrigida",
                  "birthDate": "1991-06-21",
                  "cpf": "935.411.347-80",
                  "phone": "(11) 98888-4321",
                  "email": "ana.corrigida@example.test",
                  "address": "Rua Nova, 20",
                  "emergencyContact": "Clara (11) 97777-6666",
                  "insurance": "Convênio Atualizado",
                  "allergies": "Nenhuma",
                  "notes": "Cadastro corrigido",
                  "version": %d
                }
                """.formatted(knownVersion));

        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(updated.body(), "$.id")).isEqualTo(patientId);
        assertThat(JsonPath.<String>read(updated.body(), "$.fullName")).isEqualTo("Ana Souza Corrigida");
        assertThat(JsonPath.<String>read(updated.body(), "$.cpf")).isEqualTo("93541134780");
        assertThat(JsonPath.<String>read(updated.body(), "$.phone")).isEqualTo("11988884321");
        assertThat(JsonPath.<String>read(updated.body(), "$.status")).isEqualTo("ACTIVE");
        assertThat(JsonPath.<Integer>read(updated.body(), "$.version")).isEqualTo(knownVersion + 1);

        var retrieved = get(client, "/api/v1/patients/" + patientId);
        assertThat(retrieved.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.fullName")).isEqualTo("Ana Souza Corrigida");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion + 1);
    }

    @Test
    void twoUpdatesFromTheSameKnownVersionDoNotOverwriteEachOther() throws Exception {
        var client = authenticatedClient();
        var created = postPatient(client, requiredPatientJson("111.444.777-35"));
        var patientId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");
        var firstRepresentation = updatePatientJson("Primeira Correção Confirmada", "111.444.777-35", knownVersion);
        var secondRepresentation = updatePatientJson("Segunda Correção Obsoleta", "111.444.777-35", knownVersion);

        var firstUpdate = putPatient(client, patientId, firstRepresentation);
        var staleUpdate = putPatient(client, patientId, secondRepresentation);

        assertThat(firstUpdate.statusCode()).isEqualTo(200);
        assertThat(staleUpdate.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(staleUpdate.body(), "$.type")).isEqualTo("urn:problem:conflict");
        assertThat(staleUpdate.body())
                .doesNotContain("Primeira Correção Confirmada")
                .doesNotContain("Segunda Correção Obsoleta")
                .doesNotContain("11144477735");

        var retrieved = get(client, "/api/v1/patients/" + patientId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.fullName")).isEqualTo("Primeira Correção Confirmada");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion + 1);
    }

    @Test
    void simultaneousPatientUpdatesDoNotSilentlyOverwriteEachOther() throws Exception {
        var firstClient = authenticatedClient();
        var secondClient = authenticatedClient();
        var created = postPatient(firstClient, requiredPatientJson("280.012.389-38"));
        var patientId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");
        var firstCsrf = get(firstClient, "/api/v1/auth/csrf");
        var secondCsrf = get(secondClient, "/api/v1/auth/csrf");
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
                        "/api/v1/patients/" + patientId,
                        updatePatientJson("Primeira atualização concorrente", "280.012.389-38", knownVersion),
                        JsonPath.read(firstCsrf.body(), "$.headerName"),
                        JsonPath.read(firstCsrf.body(), "$.token"));
            });
            var secondFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return send(
                        secondClient,
                        "PUT",
                        "/api/v1/patients/" + patientId,
                        updatePatientJson("Segunda atualização concorrente", "280.012.389-38", knownVersion),
                        JsonPath.read(secondCsrf.body(), "$.headerName"),
                        JsonPath.read(secondCsrf.body(), "$.token"));
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
        var retrieved = get(firstClient, "/api/v1/patients/" + patientId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.fullName"))
                .isIn("Primeira atualização concorrente", "Segunda atualização concorrente");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion + 1);
    }

    @Test
    void updatingToAnotherPatientsCanonicalCpfReturnsConflict() throws Exception {
        var client = authenticatedClient();
        var first = postPatient(client, requiredPatientJson("935.411.347-80"));
        var second = postPatient(client, requiredPatientJson("390.533.447-05"));
        var firstPatientId = JsonPath.<String>read(first.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(first.body(), "$.version");

        var duplicateCpfUpdate =
                putPatient(client, firstPatientId, updatePatientJson("CPF em conflito", "39053344705", knownVersion));

        assertThat(duplicateCpfUpdate.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(duplicateCpfUpdate.body(), "$.detail")).isEqualTo("CPF já cadastrado.");
        assertThat(duplicateCpfUpdate.body()).doesNotContain("39053344705");

        var retrieved = get(client, "/api/v1/patients/" + firstPatientId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.cpf")).isEqualTo("93541134780");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion);
        assertThat(JsonPath.<String>read(second.body(), "$.cpf")).isEqualTo("39053344705");
    }

    @Test
    void patientUpdateAuditsOnlyTheNamesOfChangedFields() throws Exception {
        var client = authenticatedClient();
        var created = postPatient(client, requiredPatientJson("987.654.321-00"));
        var patientId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");

        var updated = putPatient(client, patientId, """
                {
                  "fullName": "Nome Sensível Atualizado",
                  "motherName": "Maria Souza",
                  "birthDate": "1990-05-20",
                  "cpf": "123.456.789-09",
                  "phone": "(21) 98888-0000",
                  "version": %d
                }
                """.formatted(knownVersion));
        var audit = get(client, "/api/v1/audit-events?action=PATIENT_UPDATED&size=100");

        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(audit.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<List<String>>>read(
                        audit.body(), "$.content[?(@.targetId == '" + patientId + "')].changedFields"))
                .containsExactly(List.of("fullName", "cpf", "phone"));
        assertThat(audit.body())
                .doesNotContain("Ana Souza")
                .doesNotContain("Nome Sensível Atualizado")
                .doesNotContain("98765432100")
                .doesNotContain("12345678909")
                .doesNotContain("11999991234")
                .doesNotContain("21988880000");
    }

    @Test
    void patientCanBeInactivatedAndReactivatedWithoutDisappearing() throws Exception {
        var client = authenticatedClient();
        var created = postPatient(client, requiredPatientJson("168.995.350-09"));
        var patientId = JsonPath.<String>read(created.body(), "$.id");
        var initialVersion = JsonPath.<Integer>read(created.body(), "$.version");

        var inactivated = patchPatientStatus(client, patientId, "INACTIVE", initialVersion);

        assertThat(inactivated.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(inactivated.body(), "$.id")).isEqualTo(patientId);
        assertThat(JsonPath.<String>read(inactivated.body(), "$.status")).isEqualTo("INACTIVE");
        assertThat(JsonPath.<Integer>read(inactivated.body(), "$.version")).isEqualTo(initialVersion + 1);

        var retrievedWhileInactive = get(client, "/api/v1/patients/" + patientId);
        var searchedWhileInactive = get(client, "/api/v1/patients?fullName=Ana%20Souza");
        assertThat(JsonPath.<String>read(retrievedWhileInactive.body(), "$.status"))
                .isEqualTo("INACTIVE");
        assertThat(JsonPath.<List<String>>read(searchedWhileInactive.body(), "$.content[*].id"))
                .contains(patientId);

        var reactivated = patchPatientStatus(client, patientId, "ACTIVE", initialVersion + 1);
        assertThat(reactivated.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(reactivated.body(), "$.status")).isEqualTo("ACTIVE");
        assertThat(JsonPath.<Integer>read(reactivated.body(), "$.version")).isEqualTo(initialVersion + 2);

        var audit = get(client, "/api/v1/audit-events?action=PATIENT_STATUS_CHANGED&size=100");
        assertThat(JsonPath.<List<String>>read(audit.body(), "$.content[?(@.targetId == '" + patientId + "')].action"))
                .containsExactly("PATIENT_STATUS_CHANGED", "PATIENT_STATUS_CHANGED");
        assertThat(audit.body())
                .doesNotContain("Ana Souza")
                .doesNotContain("16899535009")
                .doesNotContain("11999991234");
    }

    @Test
    void staleStatusChangeDoesNotOverwriteTheConfirmedStatus() throws Exception {
        var client = authenticatedClient();
        var created = postPatient(client, requiredPatientJson("153.509.460-56"));
        var patientId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");

        var inactivated = patchPatientStatus(client, patientId, "INACTIVE", knownVersion);
        var staleReactivation = patchPatientStatus(client, patientId, "ACTIVE", knownVersion);

        assertThat(inactivated.statusCode()).isEqualTo(200);
        assertThat(staleReactivation.statusCode()).isEqualTo(409);
        var retrieved = get(client, "/api/v1/patients/" + patientId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.status")).isEqualTo("INACTIVE");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion + 1);
    }

    @Test
    void patientMutationsRequireTheKnownVersion() throws Exception {
        var client = authenticatedClient();
        var created = postPatient(client, requiredPatientJson("862.883.667-57"));
        var patientId = JsonPath.<String>read(created.body(), "$.id");

        var updateWithoutVersion = putPatient(client, patientId, """
                {
                  "fullName": "Ana Souza",
                  "motherName": "Maria Souza",
                  "birthDate": "1990-05-20",
                  "cpf": "862.883.667-57",
                  "phone": "(11) 99999-1234"
                }
                """);
        var statusWithoutVersion = mutation(client, "PATCH", "/api/v1/patients/" + patientId + "/status", """
                        {"status":"INACTIVE"}
                        """);

        assertMissingVersion(updateWithoutVersion);
        assertMissingVersion(statusWithoutVersion);
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

    private String requiredPatientJson(String cpf) {
        return """
                {
                  "fullName": "Ana Souza",
                  "motherName": "Maria Souza",
                  "birthDate": "1990-05-20",
                  "cpf": "%s",
                  "phone": "(11) 99999-1234"
                }
                """.formatted(cpf);
    }

    private String updatePatientJson(String fullName, String cpf, int version) {
        return """
                {
                  "fullName": "%s",
                  "motherName": "Maria Souza",
                  "birthDate": "1990-05-20",
                  "cpf": "%s",
                  "phone": "(11) 99999-1234",
                  "version": %d
                }
                """.formatted(fullName, cpf, version);
    }

    private HttpResponse<String> postPatient(HttpClient client, String body) throws Exception {
        return mutation(client, "POST", "/api/v1/patients", body);
    }

    private HttpResponse<String> putPatient(HttpClient client, String patientId, String body) throws Exception {
        return mutation(client, "PUT", "/api/v1/patients/" + patientId, body);
    }

    private HttpResponse<String> patchPatientStatus(HttpClient client, String patientId, String status, int version)
            throws Exception {
        return mutation(client, "PATCH", "/api/v1/patients/" + patientId + "/status", """
                {"status":"%s","version":%d}
                """.formatted(status, version));
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

    private void assertMissingVersion(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:invalid-request");
        assertThat(JsonPath.<List<String>>read(response.body(), "$.errors[*].field"))
                .containsExactly("version");
    }

    private URI apiUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
