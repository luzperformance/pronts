package br.com.primeiroprontuario.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConsultationAuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    ConsultationAuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public void recordFinalized(UUID doctorId, UUID consultationId, String correlationId) {
        events.save(new AuditEvent(
                UUID.randomUUID(),
                doctorId,
                AuditAction.CONSULTATION_FINALIZED,
                "CONSULTATION",
                consultationId,
                AuditOutcome.SUCCESS,
                clock.instant(),
                correlationId));
    }

    public void recordAddendumAdded(UUID doctorId, UUID addendumId, String correlationId) {
        events.save(new AuditEvent(
                UUID.randomUUID(),
                doctorId,
                AuditAction.ADDENDUM_ADDED,
                "ADDENDUM",
                addendumId,
                AuditOutcome.SUCCESS,
                clock.instant(),
                correlationId));
    }
}
