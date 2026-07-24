package br.com.primeiroprontuario.auth;

import br.com.primeiroprontuario.audit.LoginAuditService;
import br.com.primeiroprontuario.audit.LoginFailureAuditService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LoginService {

    private final AuthenticationManager authenticationManager;
    private final LoginAuditService loginAuditService;
    private final LoginFailureAuditService loginFailureAuditService;

    LoginService(
            AuthenticationManager authenticationManager,
            LoginAuditService loginAuditService,
            LoginFailureAuditService loginFailureAuditService) {
        this.authenticationManager = authenticationManager;
        this.loginAuditService = loginAuditService;
        this.loginFailureAuditService = loginFailureAuditService;
    }

    @Transactional
    Authentication authenticate(String username, String password, String correlationId) {
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password));
            var principal = (DoctorPrincipal) authentication.getPrincipal();
            loginAuditService.recordSucceeded(principal.id(), correlationId);
            return authentication;
        } catch (AuthenticationException exception) {
            loginFailureAuditService.recordFailed(correlationId);
            throw exception;
        }
    }
}
