package br.com.primeiroprontuario.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ScheduleBlockAuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    ScheduleBlockAuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public void recordCreated(UUID doctorId, UUID blockId, String correlationId) {
        record(doctorId, blockId, AuditAction.SCHEDULE_BLOCK_CREATED, correlationId);
    }

    public void recordRemoved(UUID doctorId, UUID blockId, String correlationId) {
        record(doctorId, blockId, AuditAction.SCHEDULE_BLOCK_REMOVED, correlationId);
    }

    private void record(UUID doctorId, UUID blockId, AuditAction action, String correlationId) {
        events.save(new AuditEvent(
                UUID.randomUUID(),
                doctorId,
                action,
                "SCHEDULE_BLOCK",
                blockId,
                AuditOutcome.SUCCESS,
                clock.instant(),
                correlationId));
    }
}
