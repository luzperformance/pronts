package br.com.primeiroprontuario.patient;

public class InactivePatientException extends RuntimeException {

    InactivePatientException() {
        super("Inactive patient");
    }
}
