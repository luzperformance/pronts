package br.com.primeiroprontuario.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AttachmentAuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    AttachmentAuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public void recordUploaded(UUID doctorId, UUID attachmentId, String correlationId) {
        record(doctorId, AuditAction.ATTACHMENT_UPLOADED, attachmentId, correlationId);
    }

    public void recordDownloaded(UUID doctorId, UUID attachmentId, String correlationId) {
        record(doctorId, AuditAction.ATTACHMENT_DOWNLOADED, attachmentId, correlationId);
    }

    public void recordRemoved(UUID doctorId, UUID attachmentId, String correlationId) {
        record(doctorId, AuditAction.ATTACHMENT_REMOVED, attachmentId, correlationId);
    }

    private void record(UUID doctorId, AuditAction action, UUID attachmentId, String correlationId) {
        events.saveAndFlush(new AuditEvent(
                UUID.randomUUID(),
                doctorId,
                action,
                "ATTACHMENT",
                attachmentId,
                AuditOutcome.SUCCESS,
                clock.instant(),
                correlationId));
    }
}
