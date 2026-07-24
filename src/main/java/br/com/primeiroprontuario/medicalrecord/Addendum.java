package br.com.primeiroprontuario.medicalrecord;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "addendum")
class Addendum {

    @Id
    private UUID id;

    @Column(name = "consultation_id", nullable = false, updatable = false)
    private UUID consultationId;

    @Column(nullable = false, updatable = false)
    private String content;

    @Column(nullable = false, updatable = false)
    private String justification;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Addendum() {}

    Addendum(UUID id, UUID consultationId, String content, String justification, UUID authorId, Instant createdAt) {
        this.id = id;
        this.consultationId = consultationId;
        this.content = content;
        this.justification = justification;
        this.authorId = authorId;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getConsultationId() {
        return consultationId;
    }

    String getContent() {
        return content;
    }

    String getJustification() {
        return justification;
    }

    UUID getAuthorId() {
        return authorId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
