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
        properties = {
            "app.doctor.username=doctor",
            "app.doctor.password=valid-test-password",
            "server.tomcat.remoteip.internal-proxies=10.0.0.0/8"
        })
class UntrustedForwardedHeadersApiIntegrationTest extends FlywaySpringIntegrationTest {

    @Container
    @ServiceConnection(type = org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails.class)
    private static final FlywayPostgreSQLContainer POSTGRESQL = new FlywayPostgreSQLContainer();

    @org.springframework.test.context.DynamicPropertySource
    static void flywayProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        POSTGRESQL.registerFlywayProperties(registry);
    }

    @LocalServerPort
    private int port;

    @Test
    void forwardedHttpsIsIgnoredOutsideTheConfiguredInternalProxyNetwork() throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth/csrf"))
                .header("X-Forwarded-Proto", "https")
                .GET()
                .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Set-Cookie")).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("XSRF-TOKEN=")
                .contains("; SameSite=Lax")
                .doesNotContain("; Secure"));
    }
}
