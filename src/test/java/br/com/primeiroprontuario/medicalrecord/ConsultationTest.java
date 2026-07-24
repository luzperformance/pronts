package br.com.primeiroprontuario.medicalrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsultationTest {

    @Test
    void newConsultationIsADraftAndKeepsItsPatientAppointmentLink() {
        var patientId = UUID.randomUUID();
        var appointmentId = UUID.randomUUID();

        var consultation = new Consultation(
                UUID.randomUUID(),
                patientId,
                appointmentId,
                patientId,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2030-01-01T12:00:00Z"));

        assertThat(consultation.getStatus()).isEqualTo(ConsultationStatus.DRAFT);
        assertThat(consultation.getPatientId()).isEqualTo(patientId);
        assertThat(consultation.getAppointmentId()).isEqualTo(appointmentId);
    }

    @Test
    void appointmentFromAnotherPatientViolatesTheLocalLinkInvariant() {
        assertThatThrownBy(() -> new Consultation(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2030-01-01T12:00:00Z")))
                .isInstanceOf(ConsultationAppointmentConflictException.class);
    }

    @Test
    void finalizationEnumeratesEveryMissingClinicalFieldIncludingWhitespaceOnlyValues() {
        var consultation = consultationWithClinicalContent(null, "", " \t", "\n", "   ", null);

        assertThatThrownBy(() -> consultation.finalize(UUID.randomUUID(), Instant.parse("2030-01-01T13:00:00Z")))
                .isInstanceOfSatisfying(
                        IncompleteConsultationException.class, exception -> assertThat(exception.getMissingFields())
                                .isEqualTo(List.of(
                                        "anamnesis",
                                        "chiefComplaint",
                                        "physicalExamination",
                                        "diagnosticHypotheses",
                                        "treatmentPlan",
                                        "observations")));
    }

    @Test
    void completeDraftFinalizationFixesItsStatusAuthorAndServerInstant() {
        var consultation = completeConsultation();
        var authorId = UUID.randomUUID();
        var finalizedAt = Instant.parse("2030-01-01T13:00:00Z");

        consultation.finalize(authorId, finalizedAt);

        assertThat(consultation.getStatus()).isEqualTo(ConsultationStatus.FINALIZED);
        assertThat(consultation.getFinalizedBy()).isEqualTo(authorId);
        assertThat(consultation.getFinalizedAt()).isEqualTo(finalizedAt);
    }

    @Test
    void finalizedConsultationRejectsClinicalContentMutation() {
        var consultation = completeConsultation();
        consultation.finalize(UUID.randomUUID(), Instant.parse("2030-01-01T13:00:00Z"));

        assertThatThrownBy(() -> consultation.update(
                        "Outra anamnese",
                        "Outra queixa",
                        "Outro exame",
                        "Outras hipóteses",
                        "Outra conduta",
                        "Outras observações"))
                .isInstanceOf(ConsultationFinalizedConflictException.class);
        assertThat(consultation.getAnamnesis()).isEqualTo("Anamnese");
    }

    @Test
    void repeatedFinalizationReportsNoTransitionAndPreservesOriginalMetadata() {
        var consultation = completeConsultation();
        var originalAuthor = UUID.randomUUID();
        var originalInstant = Instant.parse("2030-01-01T13:00:00Z");
        consultation.finalize(originalAuthor, originalInstant);

        var transitioned = consultation.finalize(UUID.randomUUID(), Instant.parse("2030-01-02T13:00:00Z"));

        assertThat(transitioned).isFalse();
        assertThat(consultation.getFinalizedBy()).isEqualTo(originalAuthor);
        assertThat(consultation.getFinalizedAt()).isEqualTo(originalInstant);
    }

    private Consultation completeConsultation() {
        return consultationWithClinicalContent(
                "Anamnese", "Queixa", "Exame físico", "Hipóteses diagnósticas", "Conduta", "Observações");
    }

    private Consultation consultationWithClinicalContent(
            String anamnesis,
            String chiefComplaint,
            String physicalExamination,
            String diagnosticHypotheses,
            String treatmentPlan,
            String observations) {
        return new Consultation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                anamnesis,
                chiefComplaint,
                physicalExamination,
                diagnosticHypotheses,
                treatmentPlan,
                observations,
                Instant.parse("2030-01-01T12:00:00Z"));
    }
}
