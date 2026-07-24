package br.com.primeiroprontuario.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginFailureAuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    LoginFailureAuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(String correlationId) {
        events.save(new AuditEvent(
                UUID.randomUUID(),
                null,
                AuditAction.AUTH_LOGIN_FAILED,
                "AUTHENTICATION",
                null,
                AuditOutcome.FAILURE,
                clock.instant(),
                correlationId));
    }
}
