package br.com.primeiroprontuario.audit;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PatientAuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    PatientAuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public void recordCreated(UUID doctorId, UUID patientId, String correlationId) {
        events.save(new AuditEvent(
                UUID.randomUUID(),
                doctorId,
                AuditAction.PATIENT_CREATED,
                "PATIENT",
                patientId,
                AuditOutcome.SUCCESS,
                clock.instant(),
                correlationId));
    }

    public void recordUpdated(UUID doctorId, UUID patientId, List<String> changedFields, String correlationId) {
        events.save(new AuditEvent(
                UUID.randomUUID(),
                doctorId,
                AuditAction.PATIENT_UPDATED,
                "PATIENT",
                patientId,
                AuditOutcome.SUCCESS,
                clock.instant(),
                correlationId,
                changedFields));
    }

    public void recordStatusChanged(UUID doctorId, UUID patientId, String correlationId) {
        events.save(new AuditEvent(
                UUID.randomUUID(),
                doctorId,
                AuditAction.PATIENT_STATUS_CHANGED,
                "PATIENT",
                patientId,
                AuditOutcome.SUCCESS,
                clock.instant(),
                correlationId));
    }
}
