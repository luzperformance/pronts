package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("prod")
@SpringBootTest(
        classes = PrimeiroProntuarioApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.doctor.username=doctor", "app.doctor.password=valid-test-password"})
class ProductionTransportApiIntegrationTest extends DrizzleSpringIntegrationTest {

    @Container
    @ServiceConnection
    private static final DrizzlePostgreSQLContainer POSTGRESQL = new DrizzlePostgreSQLContainer();

    @LocalServerPort
    private int port;

    @Test
    void productionCookiesRemainSecureBehindTheInternalHttpsProxy() throws Exception {
        var loginRequest = HttpRequest.newBuilder()
                .uri(apiUri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-Proto", "https")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"doctor","password":"valid-test-password"}
                        """))
                .build();
        var csrfRequest = HttpRequest.newBuilder()
                .uri(apiUri("/api/v1/auth/csrf"))
                .header("X-Forwarded-Proto", "https")
                .GET()
                .build();

        var client = HttpClient.newHttpClient();
        var login = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        var csrf = client.send(csrfRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().allValues("Set-Cookie")).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("JSESSIONID=")
                .contains("; Secure", "; HttpOnly", "; SameSite=Lax"));
        assertThat(csrf.statusCode()).isEqualTo(200);
        assertThat(csrf.headers().allValues("Set-Cookie"))
                .anySatisfy(
                        cookie -> assertThat(cookie).startsWith("XSRF-TOKEN=").contains("; Secure", "; SameSite=Lax"));
    }

    @Test
    void productionRejectsCrossOriginRequestsWhenNoOriginIsConfigured() throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(apiUri("/api/v1/patients"))
                .header("Origin", "https://unconfigured.example.test")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    private URI apiUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
