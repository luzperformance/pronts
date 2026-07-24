package br.com.primeiroprontuario.medicalrecord;

import java.util.List;

public class IncompleteConsultationException extends RuntimeException {

    private final List<String> missingFields;

    IncompleteConsultationException(List<String> missingFields) {
        this.missingFields = List.copyOf(missingFields);
    }

    public List<String> getMissingFields() {
        return missingFields;
    }
}
