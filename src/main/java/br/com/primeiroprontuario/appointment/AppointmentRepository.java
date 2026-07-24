package br.com.primeiroprontuario.appointment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    boolean existsByStatusNotAndStartsAtLessThanAndEndsAtGreaterThan(
            AppointmentStatus ignoredStatus, Instant endsAt, Instant startsAt);

    boolean existsByIdNotAndStatusNotAndStartsAtLessThanAndEndsAtGreaterThan(
            UUID appointmentId, AppointmentStatus ignoredStatus, Instant endsAt, Instant startsAt);

    Page<Appointment> findByStatusInAndStartsAtGreaterThanEqualAndStartsAtLessThanEqual(
            List<AppointmentStatus> statuses, Instant startsAt, Instant endsAt, Pageable pageable);
}
