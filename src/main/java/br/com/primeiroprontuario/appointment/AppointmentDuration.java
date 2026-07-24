package br.com.primeiroprontuario.appointment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

record AppointmentDuration(int minutes) {

    private static final Set<Integer> SUPPORTED_MINUTES = Set.of(15, 30, 45, 60);

    AppointmentDuration {
        if (!SUPPORTED_MINUTES.contains(minutes)) {
            throw new IllegalArgumentException("Unsupported appointment duration");
        }
    }

    static AppointmentDuration ofMinutes(int minutes) {
        return new AppointmentDuration(minutes);
    }

    Instant addTo(Instant startsAt) {
        return startsAt.plus(minutes, ChronoUnit.MINUTES);
    }
}
