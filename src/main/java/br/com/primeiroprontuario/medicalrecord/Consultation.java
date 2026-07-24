package br.com.primeiroprontuario.medicalrecord;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

@Entity
@Table(name = "consultation")
class Consultation {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "appointment_id", unique = true)
    private UUID appointmentId;

    private String anamnesis;

    @Column(name = "chief_complaint")
    private String chiefComplaint;

    @Column(name = "physical_examination")
    private String physicalExamination;

    @Column(name = "diagnostic_hypotheses")
    private String diagnosticHypotheses;

    @Column(name = "treatment_plan")
    private String treatmentPlan;

    private String observations;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsultationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "clinical_date", nullable = false, updatable = false)
    private Instant clinicalDate;

    @Column(name = "finalized_by")
    private UUID finalizedBy;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Consultation() {}

    Consultation(
            UUID id,
            UUID patientId,
            UUID appointmentId,
            UUID appointmentPatientId,
            String anamnesis,
            String chiefComplaint,
            String physicalExamination,
            String diagnosticHypotheses,
            String treatmentPlan,
            String observations,
            Instant createdAt) {
        this(
                id,
                patientId,
                appointmentId,
                appointmentPatientId,
                anamnesis,
                chiefComplaint,
                physicalExamination,
                diagnosticHypotheses,
                treatmentPlan,
                observations,
                null,
                createdAt);
    }

    Consultation(
            UUID id,
            UUID patientId,
            UUID appointmentId,
            UUID appointmentPatientId,
            String anamnesis,
            String chiefComplaint,
            String physicalExamination,
            String diagnosticHypotheses,
            String treatmentPlan,
            String observations,
            Instant clinicalDate,
            Instant createdAt) {
        if (appointmentId != null && !patientId.equals(appointmentPatientId)) {
            throw new ConsultationAppointmentConflictException();
        }
        this.id = id;
        this.patientId = patientId;
        this.appointmentId = appointmentId;
        this.anamnesis = anamnesis;
        this.chiefComplaint = chiefComplaint;
        this.physicalExamination = physicalExamination;
        this.diagnosticHypotheses = diagnosticHypotheses;
        this.treatmentPlan = treatmentPlan;
        this.observations = observations;
        this.status = ConsultationStatus.DRAFT;
        this.createdAt = createdAt;
        this.clinicalDate = clinicalDate == null ? createdAt : clinicalDate;
    }

    void update(
            String anamnesis,
            String chiefComplaint,
            String physicalExamination,
            String diagnosticHypotheses,
            String treatmentPlan,
            String observations) {
        if (status == ConsultationStatus.FINALIZED) {
            throw new ConsultationFinalizedConflictException();
        }
        this.anamnesis = anamnesis;
        this.chiefComplaint = chiefComplaint;
        this.physicalExamination = physicalExamination;
        this.diagnosticHypotheses = diagnosticHypotheses;
        this.treatmentPlan = treatmentPlan;
        this.observations = observations;
    }

    boolean finalize(UUID authorId, Instant finalizedAt) {
        if (status == ConsultationStatus.FINALIZED) {
            return false;
        }
        var missingFields = new ArrayList<String>();
        addIfBlank(missingFields, "anamnesis", anamnesis);
        addIfBlank(missingFields, "chiefComplaint", chiefComplaint);
        addIfBlank(missingFields, "physicalExamination", physicalExamination);
        addIfBlank(missingFields, "diagnosticHypotheses", diagnosticHypotheses);
        addIfBlank(missingFields, "treatmentPlan", treatmentPlan);
        addIfBlank(missingFields, "observations", observations);
        if (!missingFields.isEmpty()) {
            throw new IncompleteConsultationException(missingFields);
        }
        status = ConsultationStatus.FINALIZED;
        finalizedBy = authorId;
        this.finalizedAt = finalizedAt;
        return true;
    }

    Addendum addAddendum(UUID addendumId, String content, String justification, UUID authorId, Instant createdAt) {
        if (status != ConsultationStatus.FINALIZED) {
            throw new AddendumConsultationNotFinalizedException();
        }
        var missingFields = new ArrayList<String>();
        addIfBlank(missingFields, "content", content);
        addIfBlank(missingFields, "justification", justification);
        if (!missingFields.isEmpty()) {
            throw new InvalidAddendumException(missingFields);
        }
        return new Addendum(addendumId, id, content, justification, authorId, createdAt);
    }

    private void addIfBlank(ArrayList<String> missingFields, String field, String value) {
        if (value == null || value.isBlank()) {
            missingFields.add(field);
        }
    }

    UUID getId() {
        return id;
    }

    UUID getPatientId() {
        return patientId;
    }

    UUID getAppointmentId() {
        return appointmentId;
    }

    String getAnamnesis() {
        return anamnesis;
    }

    String getChiefComplaint() {
        return chiefComplaint;
    }

    String getPhysicalExamination() {
        return physicalExamination;
    }

    String getDiagnosticHypotheses() {
        return diagnosticHypotheses;
    }

    String getTreatmentPlan() {
        return treatmentPlan;
    }

    String getObservations() {
        return observations;
    }

    ConsultationStatus getStatus() {
        return status;
    }

    UUID getFinalizedBy() {
        return finalizedBy;
    }

    Instant getFinalizedAt() {
        return finalizedAt;
    }

    long getVersion() {
        return version;
    }
}
