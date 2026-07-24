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
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class AppointmentRestartIntegrationTest {

    @Test
    void overlappingAppointmentStillConflictsAfterApplicationRestart() throws Exception {
        try (var postgresql = new DrizzlePostgreSQLContainer()) {
            postgresql.start();
            String patientId;

            try (var firstApplication = startApplication(postgresql)) {
                var firstApi = api(firstApplication);
                var firstClient = authenticatedClient(firstApi);
                patientId = createPatient(firstApi, firstClient);
                var created = mutation(
                        firstApi,
                        firstClient,
                        "POST",
                        "/api/v1/appointments",
                        appointmentJson(patientId, "2030-01-16T10:00:00"));
                assertThat(created.statusCode()).isEqualTo(201);
            }

            try (var restartedApplication = startApplication(postgresql)) {
                var restartedApi = api(restartedApplication);
                var restartedClient = authenticatedClient(restartedApi);

                var conflict = mutation(
                        restartedApi,
                        restartedClient,
                        "POST",
                        "/api/v1/appointments",
                        appointmentJson(patientId, "2030-01-16T10:15:00"));

                assertThat(conflict.statusCode()).isEqualTo(409);
                assertThat(JsonPath.<String>read(conflict.body(), "$.type")).isEqualTo("urn:problem:conflict");
                var persisted = get(
                        restartedApi,
                        restartedClient,
                        "/api/v1/appointments?from=2030-01-16T10:00:00&to=2030-01-16T10:45:00");
                assertThat(JsonPath.<Integer>read(persisted.body(), "$.totalElements"))
                        .isEqualTo(1);
            }
        }
    }

    private ConfigurableApplicationContext startApplication(DrizzlePostgreSQLContainer postgresql) {
        return SpringApplication.run(
                PrimeiroProntuarioApplication.class,
                "--server.port=0",
                "--server.servlet.session.cookie.secure=false",
                "--DB_URL=" + postgresql.getJdbcUrl(),
                "--DB_USERNAME=" + postgresql.getUsername(),
                "--DB_PASSWORD=" + postgresql.getPassword(),
                "--spring.flyway.enabled=false",
                "--app.doctor.username=doctor",
                "--app.doctor.password=valid-test-password");
    }

    private URI api(ConfigurableApplicationContext application) {
        var port = application.getEnvironment().getRequiredProperty("local.server.port");
        return URI.create("http://localhost:" + port);
    }

    private HttpClient authenticatedClient(URI api) throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        var login = send(api, client, "POST", "/api/v1/auth/login", """
                {"username":"doctor","password":"valid-test-password"}
                """, null, null);
        assertThat(login.statusCode()).isEqualTo(200);
        return client;
    }

    private String createPatient(URI api, HttpClient client) throws Exception {
        var created = mutation(api, client, "POST", "/api/v1/patients", """
                {
                  "fullName": "Paciente reinício PP-008",
                  "motherName": "Mãe fictícia",
                  "birthDate": "1990-05-20",
                  "cpf": "280.012.389-38",
                  "phone": "(11) 99999-1234"
                }
                """);
        assertThat(created.statusCode()).isEqualTo(201);
        return JsonPath.read(created.body(), "$.id");
    }

    private String appointmentJson(String patientId, String startsAt) {
        return """
                {
                  "patientId": "%s",
                  "startsAt": "%s",
                  "durationMinutes": 30
                }
                """.formatted(patientId, startsAt);
    }

    private HttpResponse<String> mutation(URI api, HttpClient client, String method, String path, String body)
            throws Exception {
        var csrf = get(api, client, "/api/v1/auth/csrf");
        return send(
                api,
                client,
                method,
                path,
                body,
                JsonPath.read(csrf.body(), "$.headerName"),
                JsonPath.read(csrf.body(), "$.token"));
    }

    private HttpResponse<String> send(
            URI api, HttpClient client, String method, String path, String body, String csrfHeader, String csrfToken)
            throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(api.resolve(path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (csrfHeader != null) {
            request.header(csrfHeader, csrfToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(URI api, HttpClient client, String path) throws Exception {
        var request = HttpRequest.newBuilder().uri(api.resolve(path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
