package br.com.primeiroprontuario.appointment;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class AppointmentSpecifications {

    private AppointmentSpecifications() {}

    static Specification<Appointment> matching(TimeInterval period, AppointmentStatus status, UUID patientId) {
        return overlaps(period).and(hasStatus(status)).and(hasPatient(patientId));
    }

    private static Specification<Appointment> overlaps(TimeInterval period) {
        return (root, query, builder) -> builder.and(
                builder.lessThan(root.get("startsAt"), period.endsAt()),
                builder.greaterThan(root.get("endsAt"), period.startsAt()));
    }

    private static Specification<Appointment> hasStatus(AppointmentStatus status) {
        return (root, query, builder) ->
                status == null ? builder.conjunction() : builder.equal(root.get("status"), status);
    }

    private static Specification<Appointment> hasPatient(UUID patientId) {
        return (root, query, builder) ->
                patientId == null ? builder.conjunction() : builder.equal(root.get("patientId"), patientId);
    }
}
