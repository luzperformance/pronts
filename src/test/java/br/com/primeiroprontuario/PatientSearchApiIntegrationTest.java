package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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
class PatientSearchApiIntegrationTest extends DrizzleSpringIntegrationTest {

    @Container
    @ServiceConnection
    private static final DrizzlePostgreSQLContainer POSTGRESQL = new DrizzlePostgreSQLContainer();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void combinedTextCpfPhoneAndStatusFiltersFindOnlyTheMatchingPatient() throws Exception {
        var client = authenticatedClient();
        var expected = createPatient(
                client,
                "Ana Pesquisa Alfa",
                "Maria Pesquisa Unica",
                "529.982.247-25",
                "(11) 98765-4321",
                "Ana.Mixed@Example.test");
        createPatient(
                client,
                "Bruno Pesquisa Beta",
                "Celina Pesquisa",
                "111.444.777-35",
                "(21) 97654-3210",
                "bruno@example.test");

        var response = get(
                client,
                "/api/v1/patients?fullName=ANA%20PESQUISA"
                        + "&motherName=MARIA%20PESQUISA"
                        + "&cpf=529.982.247-25"
                        + "&phone=11987654321"
                        + "&email=ana.mixed"
                        + "&status=ACTIVE");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<String>>read(response.body(), "$.content[*].id"))
                .containsExactly(expected);
        assertThat(JsonPath.<Integer>read(response.body(), "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(response.body(), "$.size")).isEqualTo(20);
        assertThat(JsonPath.<Integer>read(response.body(), "$.totalElements")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(response.body(), "$.totalPages")).isEqualTo(1);
    }

    @Test
    void everySupportedFilterWorksInIsolation() throws Exception {
        var client = authenticatedClient();
        var expected = createPatient(
                client,
                "Carla Filtro Individual",
                "Joana Marcador Materno",
                "935.411.347-80",
                "(31) 99876-1234",
                "carla.individual@example.test");

        assertOnlyPatient(client, "/api/v1/patients?fullName=FILTRO%20INDIVIDUAL", expected);
        assertOnlyPatient(client, "/api/v1/patients?motherName=marcador%20materno", expected);
        assertOnlyPatient(client, "/api/v1/patients?cpf=935.411.347-80", expected);
        assertOnlyPatient(client, "/api/v1/patients?phone=%2831%29%2099876-1234", expected);
        assertOnlyPatient(client, "/api/v1/patients?email=INDIVIDUAL%40EXAMPLE.TEST", expected);

        var byStatus = get(client, "/api/v1/patients?status=active&size=100");
        assertThat(byStatus.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<String>>read(byStatus.body(), "$.content[*].id"))
                .contains(expected);
    }

    @Test
    void searchWithoutMatchesReturnsAnEmptyPage() throws Exception {
        var client = authenticatedClient();

        var response = get(client, "/api/v1/patients?fullName=NOME%20QUE%20NAO%20EXISTE");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<String>>read(response.body(), "$.content"))
                .isEmpty();
        assertThat(JsonPath.<Integer>read(response.body(), "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(response.body(), "$.size")).isEqualTo(20);
        assertThat(JsonPath.<Integer>read(response.body(), "$.totalElements")).isZero();
        assertThat(JsonPath.<Integer>read(response.body(), "$.totalPages")).isZero();
    }

    @Test
    void inactivePatientsRemainSearchableAndCanBeFilteredByStatus() throws Exception {
        var client = authenticatedClient();
        var expected = createPatient(
                client,
                "Debora Paciente Inativa",
                "Marta Pesquisa Inativa",
                "390.533.447-05",
                "41987651234",
                "debora.inativa@example.test");
        jdbc.update("UPDATE patient SET status = 'INACTIVE' WHERE id = ?", UUID.fromString(expected));

        assertOnlyPatient(client, "/api/v1/patients?fullName=PACIENTE%20INATIVA", expected);
        assertOnlyPatient(client, "/api/v1/patients?status=INACTIVE", expected);
    }

    @Test
    void paginationUsesTheIdentifierAsADeterministicTieBreaker() throws Exception {
        var client = authenticatedClient();
        var expectedIds = new ArrayList<>(List.of(
                createPatient(
                        client,
                        "Paciente Empate Deterministico",
                        "Mae Empate Um",
                        "168.995.350-09",
                        "11911110001",
                        "empate1@example.test"),
                createPatient(
                        client,
                        "Paciente Empate Deterministico",
                        "Mae Empate Dois",
                        "123.456.789-09",
                        "11911110002",
                        "empate2@example.test"),
                createPatient(
                        client,
                        "Paciente Empate Deterministico",
                        "Mae Empate Tres",
                        "987.654.321-00",
                        "11911110003",
                        "empate3@example.test")));
        expectedIds.sort(Comparator.naturalOrder());

        var firstPage =
                get(client, "/api/v1/patients?fullName=Empate%20Deterministico" + "&page=0&size=2&sort=fullName,asc");
        var repeatedFirstPage =
                get(client, "/api/v1/patients?fullName=Empate%20Deterministico" + "&page=0&size=2&sort=fullName,asc");
        var secondPage =
                get(client, "/api/v1/patients?fullName=Empate%20Deterministico" + "&page=1&size=2&sort=fullName,asc");

        assertThat(firstPage.statusCode()).isEqualTo(200);
        assertThat(repeatedFirstPage.statusCode()).isEqualTo(200);
        assertThat(secondPage.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(firstPage.body(), "$.content[*].id"))
                .containsExactlyElementsOf(expectedIds.subList(0, 2));
        assertThat(JsonPath.<List<String>>read(repeatedFirstPage.body(), "$.content[*].id"))
                .containsExactlyElementsOf(expectedIds.subList(0, 2));
        assertThat(JsonPath.<List<String>>read(secondPage.body(), "$.content[*].id"))
                .containsExactly(expectedIds.get(2));
        assertThat(JsonPath.<Integer>read(firstPage.body(), "$.totalElements")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(firstPage.body(), "$.totalPages")).isEqualTo(2);
    }

    @Test
    void allowedSortFieldAndDirectionOrderThePage() throws Exception {
        var client = authenticatedClient();
        var first = createPatient(
                client,
                "Alberto Ordem Permitida",
                "Mae Marcador Ordenacao",
                "862.883.667-57",
                "11922220001",
                "alberto.ordem@example.test");
        var second = createPatient(
                client,
                "Zoe Ordem Permitida",
                "Mae Marcador Ordenacao",
                "153.509.460-56",
                "11922220002",
                "zoe.ordem@example.test");

        var response = get(client, "/api/v1/patients?motherName=Marcador%20Ordenacao&sort=fullName,desc");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(response.body(), "$.content[*].id"))
                .containsExactly(second, first);
    }

    @Test
    void invalidPaginationAndSortingReturnFieldValidationProblems() throws Exception {
        var client = authenticatedClient();

        assertInvalidQuery(client, "page=-1", "page", "deve ser maior ou igual a 0");
        assertInvalidQuery(client, "size=0", "size", "deve estar entre 1 e 100");
        assertInvalidQuery(client, "size=101", "size", "deve estar entre 1 e 100");
        assertInvalidQuery(client, "sort=notes,asc", "sort", "ordenação inválida");
        assertInvalidQuery(client, "sort=fullName,sideways", "sort", "ordenação inválida");
    }

    private void assertInvalidQuery(HttpClient client, String query, String field, String message) throws Exception {
        var response = get(client, "/api/v1/patients?" + query);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:invalid-request");
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].field")).isEqualTo(field);
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].message"))
                .isEqualTo(message);
    }

    private void assertOnlyPatient(HttpClient client, String path, String expectedId) throws Exception {
        var response = get(client, path);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<String>>read(response.body(), "$.content[*].id"))
                .containsExactly(expectedId);
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

    private String createPatient(
            HttpClient client, String fullName, String motherName, String cpf, String phone, String email)
            throws Exception {
        var response = postPatient(client, """
                {
                  "fullName": "%s",
                  "motherName": "%s",
                  "birthDate": "1985-03-10",
                  "cpf": "%s",
                  "phone": "%s",
                  "email": "%s"
                }
                """.formatted(fullName, motherName, cpf, phone, email));
        assertThat(response.statusCode()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.id");
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

    private URI apiUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
