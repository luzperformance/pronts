package br.com.primeiroprontuario.appointment;

import java.time.Instant;
import java.util.Objects;

record TimeInterval(Instant startsAt, Instant endsAt) {

    TimeInterval {
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("The interval end must be after its start");
        }
    }

    static TimeInterval of(Instant startsAt, Instant endsAt) {
        return new TimeInterval(startsAt, endsAt);
    }

    static TimeInterval startingAt(Instant startsAt, AppointmentDuration duration) {
        return new TimeInterval(startsAt, duration.addTo(startsAt));
    }

    boolean overlaps(TimeInterval other) {
        return startsAt.isBefore(other.endsAt) && endsAt.isAfter(other.startsAt);
    }
}
