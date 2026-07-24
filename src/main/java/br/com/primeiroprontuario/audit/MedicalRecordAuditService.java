package br.com.primeiroprontuario.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MedicalRecordAuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    MedicalRecordAuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public void recordViewed(UUID doctorId, UUID patientId, String correlationId) {
        events.save(new AuditEvent(
                UUID.randomUUID(),
                doctorId,
                AuditAction.MEDICAL_RECORD_VIEWED,
                "PATIENT",
                patientId,
                AuditOutcome.SUCCESS,
                clock.instant(),
                correlationId));
    }
}
