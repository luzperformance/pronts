package br.com.primeiroprontuario.medicalrecord;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ConsultationAttachmentPolicy {

    private final ConsultationRepository consultations;

    ConsultationAttachmentPolicy(ConsultationRepository consultations) {
        this.consultations = consultations;
    }

    @Transactional(readOnly = true)
    public UUID patientIdOf(UUID consultationId) {
        return consultations
                .findById(consultationId)
                .orElseThrow(ConsultationNotFoundException::new)
                .getPatientId();
    }
}
