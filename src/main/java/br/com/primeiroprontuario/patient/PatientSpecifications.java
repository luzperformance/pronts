package br.com.primeiroprontuario.patient;

import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class PatientSpecifications {

    private PatientSpecifications() {}

    static Specification<Patient> matching(
            String fullName, String motherName, String cpf, String phone, String email, PatientStatus status) {
        return Specification.allOf(
                containsIgnoringCase("fullName", fullName),
                containsIgnoringCase("motherName", motherName),
                equalTo("cpf", digitsOnly(cpf)),
                equalTo("phone", digitsOnly(phone)),
                containsIgnoringCase("email", email),
                equalTo("status", status));
    }

    private static Specification<Patient> containsIgnoringCase(String field, String value) {
        if (value == null) {
            return Specification.unrestricted();
        }
        var pattern = "%" + escapeLike(value.strip().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), pattern, '\\');
    }

    private static Specification<Patient> equalTo(String field, Object value) {
        if (value == null) {
            return Specification.unrestricted();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static String digitsOnly(String value) {
        return value == null ? null : value.replaceAll("\\D", "");
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
