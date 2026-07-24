package br.com.primeiroprontuario.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schedule_block")
class ScheduleBlock {

    @Id
    private UUID id;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ScheduleBlock() {}

    ScheduleBlock(UUID id, TimeInterval interval, String reason, Instant createdAt) {
        this.id = id;
        this.startsAt = interval.startsAt();
        this.endsAt = interval.endsAt();
        this.reason = reason;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    Instant getStartsAt() {
        return startsAt;
    }

    Instant getEndsAt() {
        return endsAt;
    }

    String getReason() {
        return reason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
