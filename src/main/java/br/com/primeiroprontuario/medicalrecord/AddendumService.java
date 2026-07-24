package br.com.primeiroprontuario.medicalrecord;

import br.com.primeiroprontuario.audit.ConsultationAuditService;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AddendumService {

    private final ConsultationRepository consultations;
    private final AddendumRepository addenda;
    private final ConsultationAuditService audit;
    private final Clock clock;

    AddendumService(
            ConsultationRepository consultations,
            AddendumRepository addenda,
            ConsultationAuditService audit,
            Clock clock) {
        this.consultations = consultations;
        this.addenda = addenda;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    Addendum add(UUID consultationId, String content, String justification, UUID doctorId, String correlationId) {
        var consultation = consultations.findById(consultationId).orElseThrow(ConsultationNotFoundException::new);
        var addendum = consultation.addAddendum(
                UUID.randomUUID(),
                content,
                justification,
                doctorId,
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        var saved = addenda.save(addendum);
        audit.recordAddendumAdded(doctorId, saved.getId(), correlationId);
        return saved;
    }
}
