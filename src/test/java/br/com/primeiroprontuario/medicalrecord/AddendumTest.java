package br.com.primeiroprontuario.medicalrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AddendumTest {

    @Test
    void finalizedConsultationCreatesAddendumWithContentJustificationAuthorAndInstant() {
        var consultation = finalizedConsultation();
        var addendumId = UUID.randomUUID();
        var authorId = UUID.randomUUID();
        var createdAt = Instant.parse("2030-01-02T12:00:00Z");

        var addendum = consultation.addAddendum(
                addendumId, "Complementação clínica", "Informação recebida após a consulta", authorId, createdAt);

        assertThat(addendum.getId()).isEqualTo(addendumId);
        assertThat(addendum.getConsultationId()).isEqualTo(consultation.getId());
        assertThat(addendum.getContent()).isEqualTo("Complementação clínica");
        assertThat(addendum.getJustification()).isEqualTo("Informação recebida após a consulta");
        assertThat(addendum.getAuthorId()).isEqualTo(authorId);
        assertThat(addendum.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void addendumEnumeratesBlankContentAndJustification() {
        var consultation = finalizedConsultation();

        assertThatThrownBy(() -> consultation.addAddendum(
                        UUID.randomUUID(), " \t", "\n", UUID.randomUUID(), Instant.parse("2030-01-02T12:00:00Z")))
                .isInstanceOfSatisfying(
                        InvalidAddendumException.class, exception -> assertThat(exception.getMissingFields())
                                .isEqualTo(List.of("content", "justification")));
    }

    @Test
    void draftConsultationRejectsAddendum() {
        var consultation = draftConsultation();

        assertThatThrownBy(() -> consultation.addAddendum(
                        UUID.randomUUID(),
                        "Complementação clínica",
                        "Informação recebida depois",
                        UUID.randomUUID(),
                        Instant.parse("2030-01-02T12:00:00Z")))
                .isInstanceOf(AddendumConsultationNotFinalizedException.class);
    }

    @Test
    void addingAddendumKeepsEveryOriginalClinicalByteUnchanged() {
        var consultation = finalizedConsultation();

        consultation.addAddendum(
                UUID.randomUUID(),
                "Correção que não substitui o original",
                "Erro material identificado",
                UUID.randomUUID(),
                Instant.parse("2030-01-02T12:00:00Z"));

        assertThat(List.of(
                        consultation.getAnamnesis(),
                        consultation.getChiefComplaint(),
                        consultation.getPhysicalExamination(),
                        consultation.getDiagnosticHypotheses(),
                        consultation.getTreatmentPlan(),
                        consultation.getObservations()))
                .containsExactly(
                        "Anamnese original",
                        "Queixa original",
                        "Exame original",
                        "Hipótese original",
                        "Conduta original",
                        "Observação original");
    }

    private Consultation finalizedConsultation() {
        var consultation = draftConsultation();
        consultation.finalize(UUID.randomUUID(), Instant.parse("2030-01-01T13:00:00Z"));
        return consultation;
    }

    private Consultation draftConsultation() {
        return new Consultation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                "Anamnese original",
                "Queixa original",
                "Exame original",
                "Hipótese original",
                "Conduta original",
                "Observação original",
                Instant.parse("2030-01-01T12:00:00Z"));
    }
}
