package br.com.primeiroprontuario.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AppointmentDurationTest {

    @ParameterizedTest
    @ValueSource(ints = {15, 30, 45, 60})
    void acceptsOnlySupportedDurations(int minutes) {
        assertThat(AppointmentDuration.ofMinutes(minutes).minutes()).isEqualTo(minutes);
    }

    @ParameterizedTest
    @ValueSource(ints = {-15, 0, 14, 16, 90})
    void rejectsUnsupportedDurations(int minutes) {
        assertThatIllegalArgumentException().isThrownBy(() -> AppointmentDuration.ofMinutes(minutes));
    }

    @ParameterizedTest
    @CsvSource({
        "15, 2030-01-15T13:15:00Z",
        "30, 2030-01-15T13:30:00Z",
        "45, 2030-01-15T13:45:00Z",
        "60, 2030-01-15T14:00:00Z"
    })
    void derivesTheEndFromTheStart(int minutes, Instant expectedEnd) {
        var startsAt = Instant.parse("2030-01-15T13:00:00Z");

        var endsAt = AppointmentDuration.ofMinutes(minutes).addTo(startsAt);

        assertThat(endsAt).isEqualTo(expectedEnd);
    }
}
