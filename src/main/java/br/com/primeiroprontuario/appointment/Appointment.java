package br.com.primeiroprontuario.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment")
class Appointment {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Appointment() {}

    Appointment(UUID id, UUID patientId, Instant startsAt, AppointmentDuration duration) {
        var interval = TimeInterval.startingAt(startsAt, duration);
        this.id = id;
        this.patientId = patientId;
        this.startsAt = interval.startsAt();
        this.endsAt = interval.endsAt();
        this.durationMinutes = duration.minutes();
        this.status = AppointmentStatus.SCHEDULED;
    }

    UUID getId() {
        return id;
    }

    UUID getPatientId() {
        return patientId;
    }

    Instant getStartsAt() {
        return startsAt;
    }

    Instant getEndsAt() {
        return endsAt;
    }

    int getDurationMinutes() {
        return durationMinutes;
    }

    AppointmentStatus getStatus() {
        return status;
    }

    long getVersion() {
        return version;
    }

    void requireReschedulable() {
        if (status != AppointmentStatus.SCHEDULED && status != AppointmentStatus.CONFIRMED) {
            throw new AppointmentRescheduleConflictException();
        }
    }

    void reschedule(Instant requestedStartsAt, AppointmentDuration requestedDuration) {
        requireReschedulable();
        var interval = TimeInterval.startingAt(requestedStartsAt, requestedDuration);
        startsAt = interval.startsAt();
        endsAt = interval.endsAt();
        durationMinutes = requestedDuration.minutes();
    }

    void transitionTo(AppointmentStatus requestedStatus) {
        var allowed =
                switch (status) {
                    case SCHEDULED ->
                        requestedStatus == AppointmentStatus.CONFIRMED
                                || requestedStatus == AppointmentStatus.COMPLETED
                                || requestedStatus == AppointmentStatus.CANCELLED
                                || requestedStatus == AppointmentStatus.NO_SHOW;
                    case CONFIRMED ->
                        requestedStatus == AppointmentStatus.COMPLETED
                                || requestedStatus == AppointmentStatus.CANCELLED
                                || requestedStatus == AppointmentStatus.NO_SHOW;
                    case COMPLETED, CANCELLED, NO_SHOW -> false;
                };
        if (!allowed) {
            throw new AppointmentTransitionConflictException();
        }
        status = requestedStatus;
    }

    boolean completeIfActive() {
        if (status != AppointmentStatus.SCHEDULED && status != AppointmentStatus.CONFIRMED) {
            return false;
        }
        status = AppointmentStatus.COMPLETED;
        return true;
    }
}
