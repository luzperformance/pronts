package br.com.primeiroprontuario.medicalrecord;

import br.com.primeiroprontuario.auth.DoctorPrincipal;
import br.com.primeiroprontuario.web.ApiPagination;
import br.com.primeiroprontuario.web.CorrelationIdFilter;
import br.com.primeiroprontuario.web.InvalidRequestException;
import br.com.primeiroprontuario.web.InvalidRequestException.InvalidField;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MedicalRecordController {

    private final MedicalRecordService medicalRecord;
    private final ZoneId zoneId;

    MedicalRecordController(MedicalRecordService medicalRecord, ZoneId appointmentZoneId) {
        this.medicalRecord = medicalRecord;
        this.zoneId = appointmentZoneId;
    }

    @GetMapping("/api/v1/patients/{patientId}/medical-record")
    MedicalRecordPage view(
            @PathVariable UUID patientId,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal DoctorPrincipal doctor,
            HttpServletRequest request) {
        validate(from, to, page, size);
        var result = medicalRecord.view(
                patientId,
                from == null ? null : from.atZone(zoneId).toInstant(),
                to == null ? null : to.atZone(zoneId).toInstant(),
                page,
                size,
                doctor.id(),
                (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        return new MedicalRecordPage(
                result.getContent().stream()
                        .map(entry -> MedicalRecordItem.from(entry, zoneId))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private void validate(LocalDateTime from, LocalDateTime to, int page, int size) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw invalidField("to", "deve ser posterior a from");
        }
        ApiPagination.validate(page, size);
    }

    private InvalidRequestException invalidField(String field, String message) {
        return new InvalidRequestException(List.of(new InvalidField(field, message)));
    }

    private record MedicalRecordPage(
            List<MedicalRecordItem> content, int page, int size, long totalElements, int totalPages) {}

    private record MedicalRecordItem(
            UUID id,
            OffsetDateTime clinicalDate,
            OffsetDateTime createdAt,
            String anamnesis,
            String chiefComplaint,
            String physicalExamination,
            String diagnosticHypotheses,
            String treatmentPlan,
            String observations,
            UUID finalizedBy,
            OffsetDateTime finalizedAt,
            List<MedicalRecordAddendum> addenda) {

        private static MedicalRecordItem from(MedicalRecordEntryDetails details, ZoneId zoneId) {
            var entry = details.consultation();
            return new MedicalRecordItem(
                    entry.getId(),
                    OffsetDateTime.ofInstant(entry.getClinicalDate(), zoneId),
                    OffsetDateTime.ofInstant(entry.getCreatedAt(), zoneId),
                    entry.getAnamnesis(),
                    entry.getChiefComplaint(),
                    entry.getPhysicalExamination(),
                    entry.getDiagnosticHypotheses(),
                    entry.getTreatmentPlan(),
                    entry.getObservations(),
                    entry.getFinalizedBy(),
                    OffsetDateTime.ofInstant(entry.getFinalizedAt(), zoneId),
                    details.addenda().stream()
                            .map(addendum -> MedicalRecordAddendum.from(addendum, zoneId))
                            .toList());
        }

        @Override
        public String toString() {
            return "MedicalRecordItem[REDACTED]";
        }
    }

    private record MedicalRecordAddendum(
            UUID id, String content, String justification, UUID authorId, OffsetDateTime createdAt) {

        private static MedicalRecordAddendum from(Addendum addendum, ZoneId zoneId) {
            return new MedicalRecordAddendum(
                    addendum.getId(),
                    addendum.getContent(),
                    addendum.getJustification(),
                    addendum.getAuthorId(),
                    OffsetDateTime.ofInstant(addendum.getCreatedAt(), zoneId));
        }

        @Override
        public String toString() {
            return "MedicalRecordAddendum[REDACTED]";
        }
    }
}
