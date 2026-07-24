package br.com.primeiroprontuario.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AppointmentAuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    AppointmentAuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public void recordCreated(UUID doctorId, UUID appointmentId, String correlationId) {
        record(doctorId, appointmentId, AuditAction.APPOINTMENT_CREATED, correlationId);
    }

    public void recordRescheduled(UUID doctorId, UUID appointmentId, String correlationId) {
        record(doctorId, appointmentId, AuditAction.APPOINTMENT_RESCHEDULED, correlationId);
    }

    public void recordStatusChanged(UUID doctorId, UUID appointmentId, String correlationId) {
        record(doctorId, appointmentId, AuditAction.APPOINTMENT_STATUS_CHANGED, correlationId);
    }

    private void record(UUID doctorId, UUID appointmentId, AuditAction action, String correlationId) {
        events.save(new AuditEvent(
                UUID.randomUUID(),
                doctorId,
                action,
                "APPOINTMENT",
                appointmentId,
                AuditOutcome.SUCCESS,
                clock.instant(),
                correlationId));
    }
}
