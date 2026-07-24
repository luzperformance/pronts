package br.com.primeiroprontuario.auth;

import br.com.primeiroprontuario.audit.LogoutAuditService;
import br.com.primeiroprontuario.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class SessionController {

    private final LogoutAuditService logoutAuditService;
    private final LogoutHandler logoutHandler;

    SessionController(LogoutAuditService logoutAuditService, LogoutHandler logoutHandler) {
        this.logoutAuditService = logoutAuditService;
        this.logoutHandler = logoutHandler;
    }

    @GetMapping("/me")
    DoctorIdentity me(@AuthenticationPrincipal DoctorPrincipal principal) {
        return new DoctorIdentity(principal.id(), principal.username());
    }

    @GetMapping("/csrf")
    CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        var principal = (DoctorPrincipal) authentication.getPrincipal();
        var correlationId = (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        logoutAuditService.record(principal.id(), correlationId);
        logoutHandler.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    private record DoctorIdentity(UUID id, String username) {}

    private record CsrfTokenResponse(String headerName, String parameterName, String token) {

        @Override
        public String toString() {
            return "CsrfTokenResponse[REDACTED]";
        }
    }
}
