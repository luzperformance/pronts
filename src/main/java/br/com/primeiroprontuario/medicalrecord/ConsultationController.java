package br.com.primeiroprontuario.medicalrecord;

import br.com.primeiroprontuario.auth.DoctorPrincipal;
import br.com.primeiroprontuario.web.CorrelationIdFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ConsultationController {

    private final ConsultationService consultations;
    private final AddendumService addenda;
    private final ZoneId zoneId;

    ConsultationController(ConsultationService consultations, AddendumService addenda, ZoneId appointmentZoneId) {
        this.consultations = consultations;
        this.addenda = addenda;
        this.zoneId = appointmentZoneId;
    }

    @PostMapping("/api/v1/patients/{patientId}/consultations")
    ResponseEntity<ConsultationResponse> create(
            @PathVariable UUID patientId, @RequestBody ConsultationDraftRequest consultationRequest) {
        var consultation = consultations.create(
                patientId,
                consultationRequest.appointmentId(),
                consultationRequest.anamnesis(),
                consultationRequest.chiefComplaint(),
                consultationRequest.physicalExamination(),
                consultationRequest.diagnosticHypotheses(),
                consultationRequest.treatmentPlan(),
                consultationRequest.observations(),
                consultationRequest.clinicalDate() == null
                        ? null
                        : consultationRequest.clinicalDate().atZone(zoneId).toInstant());
        return ResponseEntity.created(URI.create("/api/v1/consultations/" + consultation.getId()))
                .body(ConsultationResponse.from(consultation));
    }

    @GetMapping("/api/v1/consultations/{consultationId}")
    ConsultationResponse find(@PathVariable UUID consultationId) {
        return ConsultationResponse.from(consultations.find(consultationId));
    }

    @PutMapping("/api/v1/consultations/{consultationId}")
    ConsultationResponse update(
            @PathVariable UUID consultationId, @Valid @RequestBody UpdateConsultationRequest consultationRequest) {
        return ConsultationResponse.from(consultations.update(
                consultationId,
                consultationRequest.anamnesis(),
                consultationRequest.chiefComplaint(),
                consultationRequest.physicalExamination(),
                consultationRequest.diagnosticHypotheses(),
                consultationRequest.treatmentPlan(),
                consultationRequest.observations(),
                consultationRequest.version()));
    }

    @PostMapping("/api/v1/consultations/{consultationId}/finalization")
    ConsultationResponse finalize(
            @PathVariable UUID consultationId,
            @AuthenticationPrincipal DoctorPrincipal doctor,
            HttpServletRequest request) {
        return ConsultationResponse.from(consultations.finalize(
                consultationId, doctor.id(), (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE)));
    }

    @PostMapping("/api/v1/consultations/{consultationId}/addenda")
    ResponseEntity<AddendumResponse> addAddendum(
            @PathVariable UUID consultationId,
            @AuthenticationPrincipal DoctorPrincipal doctor,
            @Valid @RequestBody AddendumRequest addendumRequest,
            HttpServletRequest request) {
        var addendum = addenda.add(
                consultationId, addendumRequest.content(), addendumRequest.justification(), doctor.id(), (String)
                        request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        return ResponseEntity.created(
                        URI.create("/api/v1/consultations/" + consultationId + "/addenda/" + addendum.getId()))
                .body(AddendumResponse.from(addendum));
    }

    private record ConsultationDraftRequest(
            UUID appointmentId,
            String anamnesis,
            String chiefComplaint,
            String physicalExamination,
            String diagnosticHypotheses,
            String treatmentPlan,
            String observations,
            LocalDateTime clinicalDate) {

        @Override
        public String toString() {
            return "ConsultationDraftRequest[REDACTED]";
        }
    }

    private record UpdateConsultationRequest(
            String anamnesis,
            String chiefComplaint,
            String physicalExamination,
            String diagnosticHypotheses,
            String treatmentPlan,
            String observations,
            @NotNull(message = "é obrigatório") Long version) {

        @Override
        public String toString() {
            return "UpdateConsultationRequest[REDACTED]";
        }
    }

    private record AddendumRequest(
            @NotBlank(message = "é obrigatório") String content,
            @NotBlank(message = "é obrigatório") String justification) {

        @Override
        public String toString() {
            return "AddendumRequest[REDACTED]";
        }
    }

    private record AddendumResponse(
            UUID id, UUID consultationId, String content, String justification, UUID authorId, Instant createdAt) {

        private static AddendumResponse from(Addendum addendum) {
            return new AddendumResponse(
                    addendum.getId(),
                    addendum.getConsultationId(),
                    addendum.getContent(),
                    addendum.getJustification(),
                    addendum.getAuthorId(),
                    addendum.getCreatedAt());
        }

        @Override
        public String toString() {
            return "AddendumResponse[REDACTED]";
        }
    }

    private record ConsultationResponse(
            UUID id,
            UUID patientId,
            UUID appointmentId,
            String anamnesis,
            String chiefComplaint,
            String physicalExamination,
            String diagnosticHypotheses,
            String treatmentPlan,
            String observations,
            ConsultationStatus status,
            UUID finalizedBy,
            Instant finalizedAt,
            long version,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean alreadyFinalized,
            @JsonInclude(JsonInclude.Include.NON_NULL) java.util.List<AddendumResponse> addenda) {

        private static ConsultationResponse from(Consultation consultation) {
            return from(consultation, null);
        }

        private static ConsultationResponse from(ConsultationFinalization finalization) {
            return from(finalization.consultation(), finalization.alreadyFinalized());
        }

        private static ConsultationResponse from(ConsultationDetails details) {
            return from(
                    details.consultation(),
                    null,
                    details.addenda().stream().map(AddendumResponse::from).toList());
        }

        private static ConsultationResponse from(Consultation consultation, Boolean alreadyFinalized) {
            return from(consultation, alreadyFinalized, null);
        }

        private static ConsultationResponse from(
                Consultation consultation, Boolean alreadyFinalized, java.util.List<AddendumResponse> addenda) {
            return new ConsultationResponse(
                    consultation.getId(),
                    consultation.getPatientId(),
                    consultation.getAppointmentId(),
                    consultation.getAnamnesis(),
                    consultation.getChiefComplaint(),
                    consultation.getPhysicalExamination(),
                    consultation.getDiagnosticHypotheses(),
                    consultation.getTreatmentPlan(),
                    consultation.getObservations(),
                    consultation.getStatus(),
                    consultation.getFinalizedBy(),
                    consultation.getFinalizedAt(),
                    consultation.getVersion(),
                    alreadyFinalized,
                    addenda);
        }

        @Override
        public String toString() {
            return "ConsultationResponse[REDACTED]";
        }
    }
}
