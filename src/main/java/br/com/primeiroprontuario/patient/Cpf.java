package br.com.primeiroprontuario.patient;

public record Cpf(String value) {

    public Cpf {
        if (value == null) {
            throw new IllegalArgumentException("CPF inválido");
        }
        value = value.replaceAll("\\D", "");
        if (value.length() != 11
                || value.chars().distinct().count() == 1
                || checkDigit(value, 9) != value.charAt(9) - '0'
                || checkDigit(value, 10) != value.charAt(10) - '0') {
            throw new IllegalArgumentException("CPF inválido");
        }
    }

    public static Cpf of(String value) {
        return new Cpf(value);
    }

    private static int checkDigit(String value, int digitIndex) {
        var sum = 0;
        for (var index = 0; index < digitIndex; index++) {
            sum += (value.charAt(index) - '0') * (digitIndex + 1 - index);
        }
        var remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
