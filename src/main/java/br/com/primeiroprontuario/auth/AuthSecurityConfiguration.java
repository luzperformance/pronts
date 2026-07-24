package br.com.primeiroprontuario.auth;

import br.com.primeiroprontuario.web.SecurityProblemHandler;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Component
public class AuthSecurityConfiguration {

    private final SecurityContextRepository securityContextRepository;
    private final SecurityProblemHandler securityProblemHandler;
    private final String allowedOrigin;

    AuthSecurityConfiguration(
            SecurityContextRepository securityContextRepository,
            SecurityProblemHandler securityProblemHandler,
            @Value("${app.cors.allowed-origin:}") String allowedOrigin) {
        this.securityContextRepository = securityContextRepository;
        this.securityProblemHandler = securityProblemHandler;
        this.allowedOrigin = allowedOrigin;
    }

    public HttpSecurity configure(HttpSecurity http) throws Exception {
        http.securityContext(context -> context.securityContextRepository(securityContextRepository))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityProblemHandler)
                        .accessDeniedHandler(securityProblemHandler))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .csrf(csrf ->
                        csrf.csrfTokenRepository(csrfTokenRepository()).ignoringRequestMatchers("/api/v1/auth/login"))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorization -> authorization
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/audit-events")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/patients")
                        .authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/patients/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/patients/*/status")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients/*/medical-record")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/patients/*/attachments")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients/*/attachments")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/attachments/*/content")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/attachments/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/attachments/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/patients/*/consultations")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/consultations/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/consultations/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/consultations/*/finalization")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/consultations/*/addenda")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/appointments")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/appointments")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/appointments/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/appointments/*/schedule")
                        .authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/appointments/*/status")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/schedule-blocks")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/schedule-blocks")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/schedule-blocks/*")
                        .authenticated()
                        .requestMatchers("/api/v1/**")
                        .denyAll()
                        .anyRequest()
                        .denyAll());
        return http;
    }

    private CookieCsrfTokenRepository csrfTokenRepository() {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    private UrlBasedCorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        if (!allowedOrigin.isBlank()) {
            configuration.setAllowedOrigins(List.of(allowedOrigin));
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Correlation-ID"));
        configuration.setExposedHeaders(List.of("X-Correlation-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
