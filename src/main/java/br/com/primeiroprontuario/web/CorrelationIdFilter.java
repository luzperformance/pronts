package br.com.primeiroprontuario.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".correlationId";
    public static final String HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var correlationId = parseCorrelationId(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);

        var startedAt = System.nanoTime();
        try (var ignored = MDC.putCloseable("correlationId", correlationId)) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                LOGGER.atInfo()
                        .addKeyValue("http.method", request.getMethod())
                        .addKeyValue("http.path", request.getRequestURI())
                        .addKeyValue("http.status", response.getStatus())
                        .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
                        .log("HTTP request completed");
            }
        }
    }

    private String parseCorrelationId(String requestedCorrelationId) {
        if (requestedCorrelationId != null) {
            try {
                return UUID.fromString(requestedCorrelationId).toString();
            } catch (IllegalArgumentException ignored) {
                // An untrusted or malformed identifier is replaced instead of logged.
            }
        }
        return UUID.randomUUID().toString();
    }
}
