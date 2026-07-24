package br.com.primeiroprontuario.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "audit_event")
class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private AuditAction action;

    @Column(name = "target_type", nullable = false, length = 80)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", nullable = false, length = 36)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_fields", nullable = false, columnDefinition = "jsonb")
    private List<String> changedFields;

    protected AuditEvent() {}

    AuditEvent(
            UUID id,
            UUID actorId,
            AuditAction action,
            String targetType,
            UUID targetId,
            AuditOutcome outcome,
            Instant occurredAt,
            String correlationId) {
        this(id, actorId, action, targetType, targetId, outcome, occurredAt, correlationId, List.of());
    }

    AuditEvent(
            UUID id,
            UUID actorId,
            AuditAction action,
            String targetType,
            UUID targetId,
            AuditOutcome outcome,
            Instant occurredAt,
            String correlationId,
            List<String> changedFields) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.changedFields = List.copyOf(changedFields);
    }

    UUID getId() {
        return id;
    }

    UUID getActorId() {
        return actorId;
    }

    AuditAction getAction() {
        return action;
    }

    String getTargetType() {
        return targetType;
    }

    UUID getTargetId() {
        return targetId;
    }

    AuditOutcome getOutcome() {
        return outcome;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }

    String getCorrelationId() {
        return correlationId;
    }

    List<String> getChangedFields() {
        return changedFields;
    }
}
