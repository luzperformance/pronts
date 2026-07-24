package br.com.primeiroprontuario.patient;

public class PatientVersionConflictException extends RuntimeException {

    PatientVersionConflictException() {
        super("Patient version conflict");
    }
}
