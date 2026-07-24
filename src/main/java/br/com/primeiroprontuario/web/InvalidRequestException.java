package br.com.primeiroprontuario.web;

import java.util.List;

public class InvalidRequestException extends RuntimeException {

    private final List<InvalidField> errors;

    public InvalidRequestException(List<InvalidField> errors) {
        super("Invalid request");
        this.errors = List.copyOf(errors);
    }

    List<InvalidField> getErrors() {
        return errors;
    }

    public record InvalidField(String field, String message) {}
}
