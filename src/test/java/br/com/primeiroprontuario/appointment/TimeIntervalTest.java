package br.com.primeiroprontuario.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeIntervalTest {

    private static final Instant TEN_OCLOCK = Instant.parse("2030-01-15T13:00:00Z");
    private static final Instant ELEVEN_OCLOCK = Instant.parse("2030-01-15T14:00:00Z");

    @Test
    void requiresTheEndToBeAfterTheStart() {
        assertThatIllegalArgumentException().isThrownBy(() -> TimeInterval.of(TEN_OCLOCK, TEN_OCLOCK));
    }

    @ParameterizedTest
    @CsvSource({
        "2030-01-15T12:30:00Z, 2030-01-15T13:30:00Z",
        "2030-01-15T13:30:00Z, 2030-01-15T14:30:00Z",
        "2030-01-15T13:15:00Z, 2030-01-15T13:45:00Z",
        "2030-01-15T12:30:00Z, 2030-01-15T14:30:00Z"
    })
    void overlappingIntervalsIntersect(Instant otherStart, Instant otherEnd) {
        var appointment = TimeInterval.of(TEN_OCLOCK, ELEVEN_OCLOCK);

        assertThat(appointment.overlaps(TimeInterval.of(otherStart, otherEnd))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"2030-01-15T12:00:00Z, 2030-01-15T13:00:00Z", "2030-01-15T14:00:00Z, 2030-01-15T15:00:00Z"})
    void adjacentIntervalsDoNotIntersect(Instant otherStart, Instant otherEnd) {
        var appointment = TimeInterval.of(TEN_OCLOCK, ELEVEN_OCLOCK);

        assertThat(appointment.overlaps(TimeInterval.of(otherStart, otherEnd))).isFalse();
    }
}
