package br.com.primeiroprontuario.patient;

public class DuplicateCpfException extends RuntimeException {

    DuplicateCpfException() {
        super("CPF already registered");
    }
}
