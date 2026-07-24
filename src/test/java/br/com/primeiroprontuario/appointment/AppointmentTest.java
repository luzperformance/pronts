package br.com.primeiroprontuario.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AppointmentTest {

    @Test
    void newAppointmentIsScheduledAndDerivesItsEnd() {
        var patientId = UUID.fromString("ed7961cd-6c09-4b67-83ff-8c176e756047");
        var appointment = new Appointment(
                UUID.fromString("0c0a71eb-d499-42f8-ae9a-4cead5d7bde9"),
                patientId,
                Instant.parse("2030-01-15T13:00:00Z"),
                AppointmentDuration.ofMinutes(45));

        assertThat(appointment.getPatientId()).isEqualTo(patientId);
        assertThat(appointment.getStartsAt()).isEqualTo(Instant.parse("2030-01-15T13:00:00Z"));
        assertThat(appointment.getEndsAt()).isEqualTo(Instant.parse("2030-01-15T13:45:00Z"));
        assertThat(appointment.getDurationMinutes()).isEqualTo(45);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void appliesEveryAllowedStatusTransition(AppointmentStatus current, AppointmentStatus requested) {
        var appointment = appointmentIn(current);

        appointment.transitionTo(requested);

        assertThat(appointment.getStatus()).isEqualTo(requested);
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void rejectsEveryTransitionOutsideTheAllowedTable(AppointmentStatus current, AppointmentStatus requested) {
        var appointment = appointmentIn(current);

        assertThatThrownBy(() -> appointment.transitionTo(requested))
                .isInstanceOf(AppointmentTransitionConflictException.class);
        assertThat(appointment.getStatus()).isEqualTo(current);
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED),
                Arguments.of(AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED),
                Arguments.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CANCELLED),
                Arguments.of(AppointmentStatus.SCHEDULED, AppointmentStatus.NO_SHOW),
                Arguments.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED),
                Arguments.of(AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED),
                Arguments.of(AppointmentStatus.CONFIRMED, AppointmentStatus.NO_SHOW));
    }

    private static Stream<Arguments> invalidTransitions() {
        return Arrays.stream(AppointmentStatus.values()).flatMap(current -> Arrays.stream(AppointmentStatus.values())
                .filter(requested -> !isAllowed(current, requested))
                .map(requested -> Arguments.of(current, requested)));
    }

    private static boolean isAllowed(AppointmentStatus current, AppointmentStatus requested) {
        return current == AppointmentStatus.SCHEDULED
                        && (requested == AppointmentStatus.CONFIRMED
                                || requested == AppointmentStatus.COMPLETED
                                || requested == AppointmentStatus.CANCELLED
                                || requested == AppointmentStatus.NO_SHOW)
                || current == AppointmentStatus.CONFIRMED
                        && (requested == AppointmentStatus.COMPLETED
                                || requested == AppointmentStatus.CANCELLED
                                || requested == AppointmentStatus.NO_SHOW);
    }

    private Appointment appointmentIn(AppointmentStatus status) {
        var appointment = new Appointment(
                UUID.fromString("0c0a71eb-d499-42f8-ae9a-4cead5d7bde9"),
                UUID.fromString("ed7961cd-6c09-4b67-83ff-8c176e756047"),
                Instant.parse("2030-01-15T13:00:00Z"),
                AppointmentDuration.ofMinutes(45));
        if (status != AppointmentStatus.SCHEDULED) {
            appointment.transitionTo(status);
        }
        return appointment;
    }
}
