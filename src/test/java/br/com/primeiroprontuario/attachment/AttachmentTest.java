package br.com.primeiroprontuario.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttachmentTest {

    @Test
    void activeAttachmentBecomesAnAuditableTombstone() {
        var attachment = activeAttachment();
        var removedBy = UUID.randomUUID();
        var removedAt = Instant.parse("2030-01-02T12:00:00Z");

        var transitioned = attachment.remove("Envio anexado ao paciente incorreto", removedBy, removedAt);

        assertThat(transitioned).isTrue();
        assertThat(attachment.getStatus()).isEqualTo(AttachmentStatus.REMOVED);
        assertThat(attachment.getRemovedBy()).isEqualTo(removedBy);
        assertThat(attachment.getRemovalJustification()).isEqualTo("Envio anexado ao paciente incorreto");
        assertThat(attachment.getRemovedAt()).isEqualTo(removedAt);
        assertThat(attachment.isBinaryCleanupPending()).isTrue();
    }

    @Test
    void removingATombstoneAgainPreservesItsOriginalRemovalData() {
        var attachment = activeAttachment();
        var originalAuthor = UUID.randomUUID();
        var originalInstant = Instant.parse("2030-01-02T12:00:00Z");
        attachment.remove("Justificativa original", originalAuthor, originalInstant);

        var transitioned =
                attachment.remove("Justificativa posterior", UUID.randomUUID(), Instant.parse("2030-01-03T12:00:00Z"));

        assertThat(transitioned).isFalse();
        assertThat(attachment.getRemovedBy()).isEqualTo(originalAuthor);
        assertThat(attachment.getRemovalJustification()).isEqualTo("Justificativa original");
        assertThat(attachment.getRemovedAt()).isEqualTo(originalInstant);
        assertThat(attachment.isBinaryCleanupPending()).isTrue();
    }

    @Test
    void activeAttachmentRejectsBlankRemovalJustification() {
        var attachment = activeAttachment();

        assertThatThrownBy(() -> attachment.remove(" \t", UUID.randomUUID(), Instant.parse("2030-01-02T12:00:00Z")))
                .isInstanceOf(InvalidAttachmentRemovalException.class);

        assertThat(attachment.getStatus()).isEqualTo(AttachmentStatus.ACTIVE);
    }

    private Attachment activeAttachment() {
        return new Attachment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "laudo.pdf",
                "application/pdf",
                1234,
                "4f51245a7c1e9b81a73a514a18150ef2f8d3e7d18e20b325dcc2ca3f7756c743",
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                Instant.parse("2030-01-01T12:00:00Z"));
    }
}
