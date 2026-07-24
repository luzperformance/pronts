package br.com.primeiroprontuario.patient;

public class PatientNotFoundException extends RuntimeException {

    PatientNotFoundException() {
        super("Patient not found");
    }
}
