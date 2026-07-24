package br.com.primeiroprontuario.medicalrecord;

import br.com.primeiroprontuario.audit.MedicalRecordAuditService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MedicalRecordService {

    private static final Instant FIRST_SUPPORTED_CLINICAL_DATE = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant LAST_SUPPORTED_CLINICAL_DATE = Instant.parse("9999-12-31T23:59:59.999999Z");

    private final ConsultationRepository consultations;
    private final AddendumRepository addenda;
    private final MedicalRecordAuditService audit;

    MedicalRecordService(
            ConsultationRepository consultations, AddendumRepository addenda, MedicalRecordAuditService audit) {
        this.consultations = consultations;
        this.addenda = addenda;
        this.audit = audit;
    }

    @Transactional
    Page<MedicalRecordEntryDetails> view(
            UUID patientId, Instant from, Instant to, int page, int size, UUID doctorId, String correlationId) {
        var result = consultations.findMedicalRecord(
                patientId,
                ConsultationStatus.FINALIZED,
                from == null ? FIRST_SUPPORTED_CLINICAL_DATE : from,
                to == null ? LAST_SUPPORTED_CLINICAL_DATE : to,
                PageRequest.of(page, size));
        var consultationIds =
                result.getContent().stream().map(MedicalRecordEntry::getId).toList();
        var addendaByConsultation = consultationIds.isEmpty()
                ? java.util.Map.<UUID, List<Addendum>>of()
                : addenda.findByConsultationIdInOrderByCreatedAtAscIdAsc(consultationIds).stream()
                        .collect(Collectors.groupingBy(Addendum::getConsultationId));
        audit.recordViewed(doctorId, patientId, correlationId);
        return result.map(entry ->
                new MedicalRecordEntryDetails(entry, addendaByConsultation.getOrDefault(entry.getId(), List.of())));
    }
}

record MedicalRecordEntryDetails(MedicalRecordEntry consultation, List<Addendum> addenda) {}
