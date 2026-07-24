package br.com.primeiroprontuario.attachment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachment")
class Attachment {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "consultation_id")
    private UUID consultationId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "media_type", nullable = false, length = 80)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private long size;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, unique = true, length = 36)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttachmentStatus status;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "removed_by")
    private UUID removedBy;

    @Column(name = "removal_justification")
    private String removalJustification;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "binary_cleanup_pending", nullable = false)
    private boolean binaryCleanupPending;

    protected Attachment() {}

    Attachment(
            UUID id,
            UUID patientId,
            UUID consultationId,
            String originalFilename,
            String mediaType,
            long size,
            String sha256,
            String storageKey,
            UUID uploadedBy,
            Instant createdAt) {
        this.id = id;
        this.patientId = patientId;
        this.consultationId = consultationId;
        this.originalFilename = originalFilename;
        this.mediaType = mediaType;
        this.size = size;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.status = AttachmentStatus.ACTIVE;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getPatientId() {
        return patientId;
    }

    UUID getConsultationId() {
        return consultationId;
    }

    String getOriginalFilename() {
        return originalFilename;
    }

    String getMediaType() {
        return mediaType;
    }

    long getSize() {
        return size;
    }

    String getSha256() {
        return sha256;
    }

    String getStorageKey() {
        return storageKey;
    }

    AttachmentStatus getStatus() {
        return status;
    }

    UUID getUploadedBy() {
        return uploadedBy;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    boolean remove(String justification, UUID authorId, Instant instant) {
        if (status == AttachmentStatus.REMOVED) {
            return false;
        }
        if (justification == null || justification.isBlank()) {
            throw new InvalidAttachmentRemovalException();
        }
        status = AttachmentStatus.REMOVED;
        removedBy = authorId;
        removalJustification = justification.trim();
        removedAt = instant;
        binaryCleanupPending = true;
        return true;
    }

    UUID getRemovedBy() {
        return removedBy;
    }

    String getRemovalJustification() {
        return removalJustification;
    }

    Instant getRemovedAt() {
        return removedAt;
    }

    boolean isBinaryCleanupPending() {
        return binaryCleanupPending;
    }

    void completeBinaryCleanup() {
        if (status == AttachmentStatus.REMOVED) {
            binaryCleanupPending = false;
        }
    }
}
