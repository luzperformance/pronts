package br.com.primeiroprontuario.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutAuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    LogoutAuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID doctorId, String correlationId) {
        events.save(new AuditEvent(
                UUID.randomUUID(),
                doctorId,
                AuditAction.AUTH_LOGOUT,
                "DOCTOR_ACCOUNT",
                doctorId,
                AuditOutcome.SUCCESS,
                clock.instant(),
                correlationId));
    }
}
