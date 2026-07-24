package br.com.primeiroprontuario.attachment;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    Page<Attachment> findByPatientIdAndStatus(UUID patientId, AttachmentStatus status, Pageable pageable);

    Optional<Attachment> findByIdAndStatus(UUID id, AttachmentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attachment from Attachment attachment where attachment.id = :id")
    Optional<Attachment> findForMutation(UUID id);
}
