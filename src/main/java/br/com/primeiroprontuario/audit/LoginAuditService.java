package br.com.primeiroprontuario.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LoginAuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    LoginAuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public void recordSucceeded(UUID doctorId, String correlationId) {
        events.save(new AuditEvent(
                UUID.randomUUID(),
                doctorId,
                AuditAction.AUTH_LOGIN_SUCCEEDED,
                "DOCTOR_ACCOUNT",
                doctorId,
                AuditOutcome.SUCCESS,
                clock.instant(),
                correlationId));
    }
}
