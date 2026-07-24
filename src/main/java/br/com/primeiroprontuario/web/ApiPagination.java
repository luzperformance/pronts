package br.com.primeiroprontuario.web;

import br.com.primeiroprontuario.web.InvalidRequestException.InvalidField;
import java.util.List;

public final class ApiPagination {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAXIMUM_SIZE = 100;

    private ApiPagination() {}

    public static void validate(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException(List.of(new InvalidField("page", "deve ser maior ou igual a 0")));
        }
        if (size < 1 || size > MAXIMUM_SIZE) {
            throw new InvalidRequestException(
                    List.of(new InvalidField("size", "deve estar entre 1 e " + MAXIMUM_SIZE)));
        }
    }
}
