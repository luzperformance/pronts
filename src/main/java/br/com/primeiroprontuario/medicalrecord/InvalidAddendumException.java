package br.com.primeiroprontuario.medicalrecord;

import java.util.List;

public class InvalidAddendumException extends RuntimeException {

    private final List<String> missingFields;

    InvalidAddendumException(List<String> missingFields) {
        this.missingFields = List.copyOf(missingFields);
    }

    public List<String> getMissingFields() {
        return missingFields;
    }
}
