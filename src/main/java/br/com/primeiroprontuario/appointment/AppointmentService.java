package br.com.primeiroprontuario.appointment;

import br.com.primeiroprontuario.audit.AppointmentAuditService;
import br.com.primeiroprontuario.patient.PatientSchedulingPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AppointmentService {

    private static final Duration REMINDER_WINDOW = Duration.ofHours(24);
    private static final List<AppointmentStatus> REMINDER_STATUSES =
            List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED);

    private final AppointmentRepository appointments;
    private final ScheduleBlockRepository blocks;
    private final ScheduleCalendarRepository calendar;
    private final PatientSchedulingPolicy patientPolicy;
    private final AppointmentAuditService audit;
    private final Clock clock;

    AppointmentService(
            AppointmentRepository appointments,
            ScheduleBlockRepository blocks,
            ScheduleCalendarRepository calendar,
            PatientSchedulingPolicy patientPolicy,
            AppointmentAuditService audit,
            Clock clock) {
        this.appointments = appointments;
        this.blocks = blocks;
        this.calendar = calendar;
        this.patientPolicy = patientPolicy;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    Appointment create(
            UUID patientId, Instant startsAt, AppointmentDuration duration, UUID doctorId, String correlationId) {
        patientPolicy.requireActive(patientId);
        if (startsAt.isBefore(clock.instant())) {
            throw new AppointmentInPastException();
        }
        var appointment = new Appointment(UUID.randomUUID(), patientId, startsAt, duration);
        var interval = TimeInterval.of(appointment.getStartsAt(), appointment.getEndsAt());
        calendar.findForMutation().orElseThrow(IllegalStateException::new);
        if (appointments.existsByStatusNotAndStartsAtLessThanAndEndsAtGreaterThan(
                        AppointmentStatus.CANCELLED, interval.endsAt(), interval.startsAt())
                || blocks.existsByStartsAtLessThanAndEndsAtGreaterThan(interval.endsAt(), interval.startsAt())) {
            throw new AppointmentConflictException();
        }
        appointments.saveAndFlush(appointment);
        audit.recordCreated(doctorId, appointment.getId(), correlationId);
        return appointment;
    }

    @Transactional(readOnly = true)
    Appointment find(UUID appointmentId) {
        return appointments.findById(appointmentId).orElseThrow(AppointmentNotFoundException::new);
    }

    @Transactional
    Appointment reschedule(
            UUID appointmentId,
            Instant startsAt,
            AppointmentDuration duration,
            long knownVersion,
            UUID doctorId,
            String correlationId) {
        var appointment = appointments.findById(appointmentId).orElseThrow(AppointmentNotFoundException::new);
        requireKnownVersion(appointment, knownVersion);
        appointment.requireReschedulable();
        patientPolicy.requireActive(appointment.getPatientId());
        if (startsAt.isBefore(clock.instant())) {
            throw new AppointmentInPastException();
        }
        var requestedInterval = TimeInterval.startingAt(startsAt, duration);
        calendar.findForMutation().orElseThrow(IllegalStateException::new);
        if (appointments.existsByIdNotAndStatusNotAndStartsAtLessThanAndEndsAtGreaterThan(
                        appointment.getId(),
                        AppointmentStatus.CANCELLED,
                        requestedInterval.endsAt(),
                        requestedInterval.startsAt())
                || blocks.existsByStartsAtLessThanAndEndsAtGreaterThan(
                        requestedInterval.endsAt(), requestedInterval.startsAt())) {
            throw new AppointmentConflictException();
        }
        appointment.reschedule(startsAt, duration);
        flushDetectingVersionConflict();
        audit.recordRescheduled(doctorId, appointment.getId(), correlationId);
        return appointment;
    }

    @Transactional
    Appointment changeStatus(
            UUID appointmentId,
            AppointmentStatus requestedStatus,
            long knownVersion,
            UUID doctorId,
            String correlationId) {
        var appointment = appointments.findById(appointmentId).orElseThrow(AppointmentNotFoundException::new);
        requireKnownVersion(appointment, knownVersion);
        calendar.findForMutation().orElseThrow(IllegalStateException::new);
        appointment.transitionTo(requestedStatus);
        flushDetectingVersionConflict();
        audit.recordStatusChanged(doctorId, appointment.getId(), correlationId);
        return appointment;
    }

    @Transactional(readOnly = true)
    Page<Appointment> search(TimeInterval period, AppointmentStatus status, UUID patientId, Pageable pageable) {
        return appointments.findAll(AppointmentSpecifications.matching(period, status, patientId), pageable);
    }

    @Transactional(readOnly = true)
    Page<Appointment> reminders(Pageable pageable) {
        var startsAt = clock.instant();
        return appointments.findByStatusInAndStartsAtGreaterThanEqualAndStartsAtLessThanEqual(
                REMINDER_STATUSES, startsAt, startsAt.plus(REMINDER_WINDOW), pageable);
    }

    private void requireKnownVersion(Appointment appointment, long knownVersion) {
        if (appointment.getVersion() != knownVersion) {
            throw new AppointmentVersionConflictException();
        }
    }

    private void flushDetectingVersionConflict() {
        try {
            appointments.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw new AppointmentVersionConflictException();
        }
    }
}
