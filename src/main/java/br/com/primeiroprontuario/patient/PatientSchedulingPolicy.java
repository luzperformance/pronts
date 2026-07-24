package br.com.primeiroprontuario.patient;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PatientSchedulingPolicy {

    private final PatientRepository patients;

    PatientSchedulingPolicy(PatientRepository patients) {
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public void requireActive(UUID patientId) {
        var patient = patients.findById(patientId).orElseThrow(PatientNotFoundException::new);
        if (patient.getStatus() != PatientStatus.ACTIVE) {
            throw new InactivePatientException();
        }
    }
}
