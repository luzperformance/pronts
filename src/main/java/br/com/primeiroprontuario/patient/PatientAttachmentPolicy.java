package br.com.primeiroprontuario.patient;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PatientAttachmentPolicy {

    private final PatientRepository patients;

    PatientAttachmentPolicy(PatientRepository patients) {
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public void requireExisting(UUID patientId) {
        if (!patients.existsById(patientId)) {
            throw new PatientNotFoundException();
        }
    }
}
