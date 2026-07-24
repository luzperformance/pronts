package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
class PatientApiIntegrationTest extends DrizzleSpringIntegrationTest {

    @Container
    @ServiceConnection
    private static final DrizzlePostgreSQLContainer POSTGRESQL = new DrizzlePostgreSQLContainer();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void authenticatedDoctorCanCreateAndRetrieveAPatient() throws Exception {
        var client = authenticatedClient();

        var created = postPatient(client, """
                {
                  "fullName": "Ana Souza",
                  "motherName": "Maria Souza",
                  "birthDate": "1990-05-20",
                  "cpf": "529.982.247-25",
                  "phone": "(11) 99999-1234",
                  "email": "ana@example.test",
                  "address": "Rua das Flores, 10",
                  "emergencyContact": "Bruno (11) 98888-7777",
                  "insurance": "Saúde Teste",
                  "allergies": "Dipirona",
                  "notes": "Cadastro fictício"
                }
                """);

        assertThat(created.statusCode()).isEqualTo(201);
        var patientId = JsonPath.<String>read(created.body(), "$.id");
        assertThat(created.headers().firstValue("Location").orElseThrow()).endsWith("/api/v1/patients/" + patientId);

        var retrieved = get(client, "/api/v1/patients/" + patientId);

        assertThat(retrieved.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.id")).isEqualTo(patientId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.fullName")).isEqualTo("Ana Souza");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.motherName")).isEqualTo("Maria Souza");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.birthDate")).isEqualTo("1990-05-20");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.cpf")).isEqualTo("52998224725");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.phone")).isEqualTo("11999991234");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.email")).isEqualTo("ana@example.test");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.address")).isEqualTo("Rua das Flores, 10");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.emergencyContact"))
                .isEqualTo("Bruno (11) 98888-7777");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.insurance")).isEqualTo("Saúde Teste");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.allergies")).isEqualTo("Dipirona");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.notes")).isEqualTo("Cadastro fictício");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.status")).isEqualTo("ACTIVE");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isZero();
    }

    @Test
    void duplicateCanonicalCpfReturnsConflict() throws Exception {
        var client = authenticatedClient();
        var first = postPatient(client, requiredPatientJson("111.444.777-35"));

        var duplicate = postPatient(client, requiredPatientJson("11144477735"));

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(duplicate.body(), "$.type")).isEqualTo("urn:problem:conflict");
        assertThat(JsonPath.<String>read(duplicate.body(), "$.detail")).isEqualTo("CPF já cadastrado.");
        assertThat(duplicate.body()).doesNotContain("11144477735");
    }

    @Test
    void invalidCpfReturnsAFieldValidationProblem() throws Exception {
        var client = authenticatedClient();

        var response = postPatient(client, requiredPatientJson("529.982.247-24"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:invalid-request");
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].field")).isEqualTo("cpf");
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].message"))
                .isEqualTo("CPF inválido");
        assertThat(response.body()).doesNotContain("52998224724").doesNotContain("529.982.247-24");
    }

    @Test
    void missingOrBlankRequiredFieldsReturnOneErrorPerField() throws Exception {
        var client = authenticatedClient();

        var response = postPatient(client, """
                {
                  "fullName": " ",
                  "motherName": "",
                  "cpf": " ",
                  "phone": ""
                }
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:invalid-request");
        assertThat(JsonPath.<java.util.List<String>>read(response.body(), "$.errors[*].field"))
                .containsExactly("birthDate", "cpf", "fullName", "motherName", "phone");
        assertThat(JsonPath.<java.util.List<String>>read(response.body(), "$.errors[*].message"))
                .containsOnly("é obrigatório");
    }

    @Test
    void optionalFieldsRemainOptional() throws Exception {
        var client = authenticatedClient();
        var created = postPatient(client, requiredPatientJson("935.411.347-80"));
        var patientId = JsonPath.<String>read(created.body(), "$.id");

        var retrieved = get(client, "/api/v1/patients/" + patientId);

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(retrieved.statusCode()).isEqualTo(200);
        for (var optionalField :
                new String[] {"email", "address", "emergencyContact", "insurance", "allergies", "notes"}) {
            assertThat(JsonPath.<Object>read(retrieved.body(), "$." + optionalField))
                    .isNull();
        }
    }

    @Test
    void patientEndpointsRequireAnAuthenticatedSession() throws Exception {
        var anonymousCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var anonymousClient =
                HttpClient.newBuilder().cookieHandler(anonymousCookies).build();

        var creation = postPatient(anonymousClient, requiredPatientJson("987.654.321-00"));
        var retrieval = get(anonymousClient, "/api/v1/patients/" + java.util.UUID.randomUUID());

        assertAuthenticationRequired(creation);
        assertAuthenticationRequired(retrieval);
    }

    @Test
    void patientCreationProducesASafeAuditEvent() throws Exception {
        var client = authenticatedClient();
        var created = postPatient(client, requiredPatientJson("123.456.789-09"));
        var patientId = JsonPath.<String>read(created.body(), "$.id");

        var audit = get(client, "/api/v1/audit-events?action=PATIENT_CREATED");

        assertThat(audit.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(audit.body(), "$.totalElements")).isPositive();
        assertThat(JsonPath.<java.util.List<String>>read(audit.body(), "$.content[*].actorId"))
                .allMatch(actorId -> actorId.matches("[0-9a-f-]{36}"));
        assertThat(JsonPath.<java.util.List<String>>read(audit.body(), "$.content[*].action"))
                .containsOnly("PATIENT_CREATED");
        assertThat(JsonPath.<java.util.List<String>>read(audit.body(), "$.content[*].targetType"))
                .containsOnly("PATIENT");
        assertThat(JsonPath.<java.util.List<String>>read(audit.body(), "$.content[*].targetId"))
                .contains(patientId);
        assertThat(JsonPath.<java.util.List<String>>read(audit.body(), "$.content[*].outcome"))
                .containsOnly("SUCCESS");
        assertThat(audit.body())
                .doesNotContain("12345678909")
                .doesNotContain("Paciente Teste")
                .doesNotContain("21988880000")
                .doesNotContain("payload")
                .doesNotContain("metadata");
    }

    @Test
    void patientAndAuditCreationRollBackTogether() throws Exception {
        var client = authenticatedClient();
        var patient = requiredPatientJson("168.995.350-09");
        executeAsMigration(POSTGRESQL, """
                ALTER TABLE audit_event
                ADD CONSTRAINT audit_event_reject_patient_created
                CHECK (action <> 'PATIENT_CREATED')
                NOT VALID
                """);

        HttpResponse<String> failedCreation;
        try {
            failedCreation = postPatient(client, patient);
        } finally {
            executeAsMigration(
                    POSTGRESQL, "ALTER TABLE audit_event DROP CONSTRAINT audit_event_reject_patient_created");
        }

        var retriedCreation = postPatient(client, patient);

        assertThat(failedCreation.statusCode()).isEqualTo(500);
        assertThat(retriedCreation.statusCode()).isEqualTo(201);
    }

    @Test
    void unknownPatientIdentifierReturnsNotFound() throws Exception {
        var client = authenticatedClient();

        var response = get(client, "/api/v1/patients/" + java.util.UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:resource-not-found");
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo("O recurso solicitado não existe.");
    }

    private HttpClient authenticatedClient() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        var login = post(client, "/api/v1/auth/login", """
                {"username":"doctor","password":"valid-test-password"}
                """, null, null);
        assertThat(login.statusCode()).isEqualTo(200);
        return client;
    }

    private String requiredPatientJson(String cpf) {
        return """
                {
                  "fullName": "Paciente Teste",
                  "motherName": "Mãe Teste",
                  "birthDate": "1985-03-10",
                  "cpf": "%s",
                  "phone": "(21) 98888-0000"
                }
                """.formatted(cpf);
    }

    private HttpResponse<String> postPatient(HttpClient client, String body) throws Exception {
        var csrf = get(client, "/api/v1/auth/csrf");
        return post(
                client,
                "/api/v1/patients",
                body,
                JsonPath.read(csrf.body(), "$.headerName"),
                JsonPath.read(csrf.body(), "$.token"));
    }

    private HttpResponse<String> post(HttpClient client, String path, String body, String csrfHeader, String csrfToken)
            throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(apiUri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (csrfHeader != null) {
            request.header(csrfHeader, csrfToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        var request = HttpRequest.newBuilder().uri(apiUri(path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertAuthenticationRequired(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:authentication-required");
        assertThat(JsonPath.<String>read(response.body(), "$.title")).isEqualTo("Não autenticado");
    }

    private URI apiUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
