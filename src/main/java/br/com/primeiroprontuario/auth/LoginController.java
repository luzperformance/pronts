package br.com.primeiroprontuario.auth;

import br.com.primeiroprontuario.web.InvalidRequestException;
import br.com.primeiroprontuario.web.InvalidRequestException.InvalidField;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class LoginController {

    private final LoginService loginService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;

    LoginController(
            LoginService loginService,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository) {
        this.loginService = loginService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/login")
    ResponseEntity<DoctorIdentity> login(
            @RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        validate(loginRequest);
        var correlationId = (String) request.getAttribute(br.com.primeiroprontuario.web.CorrelationIdFilter.ATTRIBUTE);
        var authentication = loginService.authenticate(loginRequest.username(), loginRequest.password(), correlationId);
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        var principal = (DoctorPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(new DoctorIdentity(principal.id(), principal.username()));
    }

    private void validate(LoginRequest loginRequest) {
        var errors = new ArrayList<InvalidField>();
        if (loginRequest.username() == null || loginRequest.username().isBlank()) {
            errors.add(new InvalidField("username", "é obrigatório"));
        }
        if (loginRequest.password() == null || loginRequest.password().isBlank()) {
            errors.add(new InvalidField("password", "é obrigatório"));
        }
        if (!errors.isEmpty()) {
            errors.sort(java.util.Comparator.comparing(InvalidField::field));
            throw new InvalidRequestException(errors);
        }
    }

    private record LoginRequest(String username, String password) {

        @Override
        public String toString() {
            return "LoginRequest[REDACTED]";
        }
    }

    private record DoctorIdentity(UUID id, String username) {}
}
