package br.com.primeiroprontuario.attachment;

import br.com.primeiroprontuario.audit.AttachmentAuditService;
import br.com.primeiroprontuario.medicalrecord.ConsultationAttachmentPolicy;
import br.com.primeiroprontuario.patient.PatientAttachmentPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
class AttachmentService {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(AttachmentService.class);
    private static final long MAXIMUM_SIZE = 10L * 1024 * 1024;

    private final AttachmentRepository attachments;
    private final AttachmentStorage storage;
    private final AttachmentContentDetector contentDetector;
    private final PatientAttachmentPolicy patientPolicy;
    private final ConsultationAttachmentPolicy consultationPolicy;
    private final AttachmentAuditService audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    AttachmentService(
            AttachmentRepository attachments,
            AttachmentStorage storage,
            AttachmentContentDetector contentDetector,
            PatientAttachmentPolicy patientPolicy,
            ConsultationAttachmentPolicy consultationPolicy,
            AttachmentAuditService audit,
            TransactionTemplate transactions,
            Clock clock) {
        this.attachments = attachments;
        this.storage = storage;
        this.contentDetector = contentDetector;
        this.patientPolicy = patientPolicy;
        this.consultationPolicy = consultationPolicy;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    Attachment upload(UUID patientId, UUID consultationId, MultipartFile file, UUID doctorId, String correlationId) {
        patientPolicy.requireExisting(patientId);
        validateConsultationPatient(patientId, consultationId);
        if (file.getSize() > MAXIMUM_SIZE) {
            throw new AttachmentTooLargeException();
        }

        AttachmentStorage.StagedAttachment staged = null;
        var storageKey = UUID.randomUUID().toString();
        try {
            staged = storage.stage(file.getInputStream(), MAXIMUM_SIZE);
            var detected = contentDetector.detect(storage, staged, file.getOriginalFilename(), file.getContentType());
            if (staged.size() == 0) {
                throw new UnsupportedAttachmentTypeException();
            }
            var prepared = new Attachment(
                    UUID.randomUUID(),
                    patientId,
                    consultationId,
                    file.getOriginalFilename(),
                    detected.mediaType(),
                    staged.size(),
                    staged.sha256(),
                    storageKey,
                    doctorId,
                    clock.instant().truncatedTo(ChronoUnit.MICROS));
            var stagedForTransaction = staged;
            return transactions.execute(status -> {
                storage.promote(stagedForTransaction, storageKey);
                var saved = attachments.save(prepared);
                audit.recordUploaded(doctorId, saved.getId(), correlationId);
                attachments.flush();
                return saved;
            });
        } catch (IOException exception) {
            compensate(storageKey);
            throw new AttachmentStorageException(exception);
        } catch (RuntimeException exception) {
            compensate(storageKey);
            throw exception;
        } finally {
            discard(staged);
        }
    }

    Page<Attachment> list(UUID patientId, Pageable pageable) {
        patientPolicy.requireExisting(patientId);
        return attachments.findByPatientIdAndStatus(patientId, AttachmentStatus.ACTIVE, pageable);
    }

    Attachment find(UUID attachmentId) {
        return attachments.findById(attachmentId).orElseThrow(AttachmentNotFoundException::new);
    }

    AttachmentDownload download(UUID attachmentId, UUID doctorId, String correlationId) {
        var openedContent = new AtomicReference<InputStream>();
        try {
            return transactions.execute(status -> {
                var attachment = attachments.findById(attachmentId).orElseThrow(AttachmentNotFoundException::new);
                if (attachment.getStatus() == AttachmentStatus.REMOVED) {
                    throw new AttachmentGoneException();
                }
                var content = storage.open(attachment.getStorageKey());
                openedContent.set(content);
                audit.recordDownloaded(doctorId, attachmentId, correlationId);
                return new AttachmentDownload(
                        content, attachment.getOriginalFilename(), attachment.getMediaType(), attachment.getSize());
            });
        } catch (RuntimeException exception) {
            close(openedContent.get());
            throw exception;
        }
    }

    void remove(UUID attachmentId, String justification, UUID doctorId, String correlationId) {
        var cleanup = transactions.execute(status -> {
            var attachment = attachments.findForMutation(attachmentId).orElseThrow(AttachmentNotFoundException::new);
            if (attachment.remove(justification, doctorId, clock.instant().truncatedTo(ChronoUnit.MICROS))) {
                audit.recordRemoved(doctorId, attachmentId, correlationId);
                attachments.flush();
            }
            return new AttachmentCleanup(attachment.getStorageKey(), attachment.isBinaryCleanupPending());
        });
        if (!cleanup.pending()) {
            return;
        }

        storage.delete(cleanup.storageKey());
        transactions.executeWithoutResult(status -> {
            var attachment = attachments.findForMutation(attachmentId).orElseThrow(AttachmentNotFoundException::new);
            attachment.completeBinaryCleanup();
            attachments.flush();
        });
    }

    private void validateConsultationPatient(UUID patientId, UUID consultationId) {
        if (consultationId != null && !patientId.equals(consultationPolicy.patientIdOf(consultationId))) {
            throw new AttachmentPatientConflictException();
        }
    }

    private void compensate(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Attachment storage compensation failed exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void discard(AttachmentStorage.StagedAttachment stagedAttachment) {
        if (stagedAttachment == null) {
            return;
        }
        try {
            storage.discard(stagedAttachment);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Attachment staging cleanup failed exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void close(InputStream content) {
        if (content == null) {
            return;
        }
        try {
            content.close();
        } catch (IOException exception) {
            LOGGER.error(
                    "Attachment content cleanup failed exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}

record AttachmentDownload(InputStream content, String originalFilename, String mediaType, long size) {}

record AttachmentCleanup(String storageKey, boolean pending) {}
