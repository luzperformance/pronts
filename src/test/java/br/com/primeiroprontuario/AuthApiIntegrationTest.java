package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.primeiroprontuario.web.CorrelationIdFilter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(
        classes = PrimeiroProntuarioApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.doctor.username=doctor",
            "app.doctor.password=valid-test-password",
            "app.cors.allowed-origin=https://prontuario.example.test",
            "server.servlet.session.cookie.secure=false"
        })
class AuthApiIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18.4");

    @LocalServerPort
    private int port;

    @Test
    void configuredOriginCanUseTheAuthenticatedApiWithCredentials() throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(apiUri("/api/v1/patients"))
                .header("Origin", "https://prontuario.example.test")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("https://prontuario.example.test");
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials"))
                .contains("true");
    }

    @Test
    void originOutsideTheConfiguredAllowlistIsRejected() throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(apiUri("/api/v1/patients"))
                .header("Origin", "https://untrusted.example.test")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void validCredentialsCreateAuthenticatedSession() throws Exception {
        var response = postLogin("""
                        {"username":"doctor","password":"valid-test-password"}
                        """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        assertThat(response.headers().allValues("Set-Cookie")).anyMatch(cookie -> cookie.startsWith("JSESSIONID="));
        assertThat(response.body())
                .contains("\"id\":")
                .contains("\"username\":\"doctor\"")
                .doesNotContain("password")
                .doesNotContain("hash");
    }

    @Test
    void currentSessionReturnsTheAuthenticatedDoctorIdentity() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        var login = postLogin(client, """
                {"username":"doctor","password":"valid-test-password"}
                """);
        var doctorId = JsonPath.<String>read(login.body(), "$.id");

        var response = get(client, "/api/v1/auth/me");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.id")).isEqualTo(doctorId);
        assertThat(JsonPath.<String>read(response.body(), "$.username")).isEqualTo("doctor");
        assertThat(response.body()).doesNotContain("password").doesNotContain("hash");
    }

    @Test
    void csrfTokenIsAvailableToAnAnonymousCookieClient() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();

        var response = get(client, "/api/v1/auth/csrf");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.headerName")).isEqualTo("X-XSRF-TOKEN");
        assertThat(JsonPath.<String>read(response.body(), "$.parameterName")).isEqualTo("_csrf");
        assertThat(JsonPath.<String>read(response.body(), "$.token")).isNotBlank();
        assertThat(cookies.getCookieStore().getCookies())
                .anyMatch(cookie -> cookie.getName().equals("XSRF-TOKEN")
                        && !cookie.getValue().isBlank());
        assertThat(cookies.getCookieStore().getCookies())
                .noneMatch(cookie -> cookie.getName().equals("JSESSIONID"));
    }

    @Test
    void authenticatedMutationWithoutCsrfIsForbiddenAndDoesNotLogout() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        assertThat(postLogin(client, """
                                {"username":"doctor","password":"valid-test-password"}
                                """).statusCode()).isEqualTo(200);

        var response = post(client, "/api/v1/auth/logout");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(403);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:access-denied");
        assertThat(get(client, "/api/v1/auth/me").statusCode()).isEqualTo(200);
    }

    @Test
    void everyAuthenticatedMutationMethodRequiresCsrf() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        assertThat(postLogin(client, """
                                {"username":"doctor","password":"valid-test-password"}
                                """).statusCode()).isEqualTo(200);
        var unknownId = "00000000-0000-0000-0000-000000000001";

        var responses = java.util.List.of(
                sendWithoutCsrf(client, "POST", "/api/v1/patients", "{}"),
                sendWithoutCsrf(client, "PUT", "/api/v1/patients/" + unknownId, "{}"),
                sendWithoutCsrf(client, "PATCH", "/api/v1/patients/" + unknownId + "/status", "{}"),
                sendWithoutCsrf(client, "DELETE", "/api/v1/schedule-blocks/" + unknownId, ""));

        assertThat(responses).allSatisfy(response -> {
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:access-denied");
            var correlationId = JsonPath.<String>read(response.body(), "$.correlationId");
            assertThat(correlationId).matches("[0-9a-f-]{36}");
            assertThat(response.headers().firstValue("X-Correlation-ID")).contains(correlationId);
        });
    }

    @Test
    void requestLogsAreStructuredAndDoNotContainSensitivePayloads() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            var client = HttpClient.newBuilder().cookieHandler(cookies).build();
            assertThat(postLogin(client, """
                                    {"username":"doctor","password":"valid-test-password"}
                                    """).statusCode()).isEqualTo(200);
            var csrf = get(client, "/api/v1/auth/csrf");
            var csrfToken = JsonPath.<String>read(csrf.body(), "$.token");
            var response = send(
                    client, "POST", "/api/v1/patients", """
                    {
                      "fullName": "PACIENTE-SENTINELA",
                      "motherName": "MAE-SENTINELA",
                      "birthDate": "1990-05-20",
                      "cpf": "153.509.460-56",
                      "phone": "(11) 99999-1234",
                      "allergies": "CONTEUDO-CLINICO-SENTINELA",
                      "notes": "ANEXO-SENTINELA"
                    }
                    """, JsonPath.read(csrf.body(), "$.headerName"), csrfToken);

            assertThat(response.statusCode()).isEqualTo(201);
            var correlationId =
                    response.headers().firstValue("X-Correlation-ID").orElseThrow();
            assertThat(appender.list)
                    .filteredOn(event -> event.getLoggerName().equals(CorrelationIdFilter.class.getName())
                            && event.getMDCPropertyMap().containsValue(correlationId))
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getFormattedMessage()).isEqualTo("HTTP request completed");
                        assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", correlationId);
                        assertThat(event.getKeyValuePairs())
                                .extracting(pair -> pair.key + "=" + pair.value)
                                .contains("http.method=POST", "http.path=/api/v1/patients", "http.status=201");
                    });
            assertThat(appender.list).extracting(ILoggingEvent::toString).allSatisfy(log -> assertThat(log)
                    .doesNotContain(
                            "PACIENTE-SENTINELA",
                            "MAE-SENTINELA",
                            "153.509.460-56",
                            "CONTEUDO-CLINICO-SENTINELA",
                            "ANEXO-SENTINELA",
                            csrfToken.substring(0, 16)));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void validCsrfCompletesLogoutAndTheEndedSessionCannotBeReused() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        var login = postLogin(client, """
                {"username":"doctor","password":"valid-test-password"}
                """);
        var doctorId = JsonPath.<String>read(login.body(), "$.id");
        var csrf = get(client, "/api/v1/auth/csrf");
        var csrfHeader = JsonPath.<String>read(csrf.body(), "$.headerName");
        var csrfToken = JsonPath.<String>read(csrf.body(), "$.token");

        var logout = post(client, "/api/v1/auth/logout", csrfHeader, csrfToken);

        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.body()).isEmpty();
        assertAuthenticationRequired(get(client, "/api/v1/auth/me"));

        var repeatedLogout = post(client, "/api/v1/auth/logout", csrfHeader, csrfToken);
        assertAuthenticationRequired(repeatedLogout);
        assertThat(repeatedLogout.headers().allValues("Set-Cookie"))
                .noneMatch(cookie -> cookie.startsWith("JSESSIONID=") && !cookie.contains("Max-Age=0"));

        assertThat(postLogin(client, """
                                {"username":"doctor","password":"valid-test-password"}
                                """).statusCode()).isEqualTo(200);
        var audit = get(client, "/api/v1/audit-events?action=AUTH_LOGOUT");

        assertThat(audit.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(audit.body(), "$.totalElements")).isEqualTo(1);
        assertThat(JsonPath.<String>read(audit.body(), "$.content[0].actorId")).isEqualTo(doctorId);
        assertThat(JsonPath.<String>read(audit.body(), "$.content[0].action")).isEqualTo("AUTH_LOGOUT");
        assertThat(JsonPath.<String>read(audit.body(), "$.content[0].targetType"))
                .isEqualTo("DOCTOR_ACCOUNT");
        assertThat(JsonPath.<String>read(audit.body(), "$.content[0].targetId")).isEqualTo(doctorId);
        assertThat(JsonPath.<String>read(audit.body(), "$.content[0].outcome")).isEqualTo("SUCCESS");
        assertThat(audit.body())
                .doesNotContain("password")
                .doesNotContain("hash")
                .doesNotContain("payload")
                .doesNotContain("metadata");
    }

    @Test
    void invalidCredentialsReturnTheSameSafeProblem() throws Exception {
        var wrongPassword = postLogin("""
                {"username":"doctor","password":"wrong-password"}
                """);
        var unknownUsername = postLogin("""
                {"username":"unknown","password":"wrong-password"}
                """);

        assertInvalidCredentialsProblem(wrongPassword);
        assertInvalidCredentialsProblem(unknownUsername);
        assertThat(JsonPath.<String>read(wrongPassword.body(), "$.detail"))
                .isEqualTo(JsonPath.<String>read(unknownUsername.body(), "$.detail"));
    }

    @Test
    void missingPasswordReturnsFieldValidationProblem() throws Exception {
        var response = postLogin("""
                {"username":"doctor"}
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:invalid-request");
        assertThat(JsonPath.<String>read(response.body(), "$.title")).isEqualTo("Requisição inválida");
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo("Um ou mais campos são inválidos.");
        assertThat(JsonPath.<String>read(response.body(), "$.correlationId")).matches("[0-9a-f-]{36}");
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].field")).isEqualTo("password");
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].message"))
                .isEqualTo("é obrigatório");
        assertThat(response.body()).doesNotContain("hash");
    }

    @Test
    void malformedJsonReturnsSafeBodyValidationProblem() throws Exception {
        var response = postLogin("{");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo("O corpo da requisição é inválido.");
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].field")).isEqualTo("body");
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].message"))
                .isEqualTo("JSON inválido");
        assertThat(response.body())
                .doesNotContain("Json")
                .doesNotContain("exception")
                .doesNotContain("stack");
    }

    @Test
    void successfulLoginRotatesAnExistingSessionIdentifier() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        assertThat(postLogin(client, """
                                {"username":"doctor","password":"valid-test-password"}
                                """).statusCode()).isEqualTo(200);
        var sessionBeforeLogin = sessionId(cookies);

        var response = postLogin(client, """
                {"username":"doctor","password":"valid-test-password"}
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(sessionId(cookies)).isNotEqualTo(sessionBeforeLogin);
    }

    @Test
    void loginResultsAreObservableOnlyThroughTheAuthenticatedAuditApi() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var authenticatedClient = HttpClient.newBuilder().cookieHandler(cookies).build();
        var successfulLogin = postLogin(authenticatedClient, """
                {"username":"doctor","password":"valid-test-password"}
                """);
        assertThat(successfulLogin.statusCode()).isEqualTo(200);

        var failedLogin = postLogin("""
                {"username":"doctor","password":"wrong-password"}
                """);
        assertThat(failedLogin.statusCode()).isEqualTo(401);

        var succeededEvents = get(authenticatedClient, "/api/v1/audit-events?action=AUTH_LOGIN_SUCCEEDED");
        var failedEvents = get(authenticatedClient, "/api/v1/audit-events?action=AUTH_LOGIN_FAILED");

        assertAuditPageContains(succeededEvents, "AUTH_LOGIN_SUCCEEDED", "SUCCESS");
        assertAuditPageContains(failedEvents, "AUTH_LOGIN_FAILED", "FAILURE");
    }

    @Test
    void auditEventsRequireAnAuthenticatedSessionUsingProblemDetails() throws Exception {
        var response = get(HttpClient.newHttpClient(), "/api/v1/audit-events");

        assertAuthenticationRequired(response);
    }

    @Test
    void apiDefaultsToUnauthorizedOrForbiddenAccordingToTheSession() throws Exception {
        assertAuthenticationRequired(get(HttpClient.newHttpClient(), "/api/v1/patients"));

        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        assertThat(postLogin(client, """
                                {"username":"doctor","password":"valid-test-password"}
                                """).statusCode()).isEqualTo(200);

        var response = get(client, "/api/v1/not-allowed");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(403);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:access-denied");
        assertThat(JsonPath.<String>read(response.body(), "$.title")).isEqualTo("Acesso negado");
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo("A operação não é permitida.");
        assertThat(JsonPath.<String>read(response.body(), "$.correlationId")).matches("[0-9a-f-]{36}");
    }

    @Test
    void auditPaginationRejectsAnOversizedPageUsingFieldErrors() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        assertThat(postLogin(client, """
                                {"username":"doctor","password":"valid-test-password"}
                                """).statusCode()).isEqualTo(200);

        var response = get(client, "/api/v1/audit-events?size=101");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].field")).isEqualTo("size");
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].message"))
                .isEqualTo("deve estar entre 1 e 100");
    }

    @Test
    void authenticatedCookieClientsReceiveACsrfToken() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        assertThat(postLogin(client, """
                                {"username":"doctor","password":"valid-test-password"}
                                """).statusCode()).isEqualTo(200);

        var response = get(client, "/api/v1/audit-events");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(cookies.getCookieStore().getCookies())
                .anyMatch(cookie -> cookie.getName().equals("XSRF-TOKEN")
                        && !cookie.getValue().isBlank());
    }

    private void assertAuditPageContains(HttpResponse<String> response, String expectedAction, String expectedOutcome) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(response.body(), "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(response.body(), "$.size")).isEqualTo(20);
        assertThat(JsonPath.<String>read(response.body(), "$.content[0].action"))
                .isEqualTo(expectedAction);
        assertThat(JsonPath.<String>read(response.body(), "$.content[0].outcome"))
                .isEqualTo(expectedOutcome);
        assertThat(JsonPath.<String>read(response.body(), "$.content[0].correlationId"))
                .matches("[0-9a-f-]{36}");
        assertThat(response.body())
                .doesNotContain("password")
                .doesNotContain("hash")
                .doesNotContain("payload")
                .doesNotContain("metadata");
    }

    private void assertInvalidCredentialsProblem(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(401);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:invalid-credentials");
        assertThat(JsonPath.<String>read(response.body(), "$.title")).isEqualTo("Falha de autenticação");
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo("Credenciais inválidas.");
        assertThat(JsonPath.<String>read(response.body(), "$.correlationId")).matches("[0-9a-f-]{36}");
        assertThat(response.body())
                .doesNotContain("doctor")
                .doesNotContain("unknown")
                .doesNotContain("password")
                .doesNotContain("hash");
    }

    private void assertAuthenticationRequired(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(401);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:authentication-required");
        assertThat(JsonPath.<String>read(response.body(), "$.title")).isEqualTo("Não autenticado");
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo("Autenticação necessária.");
        assertThat(JsonPath.<String>read(response.body(), "$.correlationId")).matches("[0-9a-f-]{36}");
    }

    private HttpResponse<String> postLogin(String body) throws Exception {
        return postLogin(HttpClient.newHttpClient(), body);
    }

    private HttpResponse<String> postLogin(HttpClient client, String body) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(apiUri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        var request = HttpRequest.newBuilder().uri(apiUri(path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path) throws Exception {
        return post(client, path, null, null);
    }

    private HttpResponse<String> post(HttpClient client, String path, String headerName, String headerValue)
            throws Exception {
        var request = HttpRequest.newBuilder().uri(apiUri(path)).POST(HttpRequest.BodyPublishers.noBody());
        if (headerName != null) {
            request.header(headerName, headerValue);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendWithoutCsrf(HttpClient client, String method, String path, String body)
            throws Exception {
        return send(client, method, path, body, null, null);
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

    private String sessionId(CookieManager cookies) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("JSESSIONID"))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow();
    }

    private URI apiUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
