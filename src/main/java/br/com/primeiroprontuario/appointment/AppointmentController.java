package br.com.primeiroprontuario.appointment;

import br.com.primeiroprontuario.auth.DoctorPrincipal;
import br.com.primeiroprontuario.web.ApiPagination;
import br.com.primeiroprontuario.web.CorrelationIdFilter;
import br.com.primeiroprontuario.web.InvalidRequestException;
import br.com.primeiroprontuario.web.InvalidRequestException.InvalidField;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
class AppointmentController {

    private final AppointmentService appointments;
    private final ZoneId zoneId;

    AppointmentController(AppointmentService appointments, ZoneId appointmentZoneId) {
        this.appointments = appointments;
        this.zoneId = appointmentZoneId;
    }

    @PostMapping
    ResponseEntity<AppointmentResponse> create(
            @Valid @RequestBody CreateAppointmentRequest appointmentRequest,
            @AuthenticationPrincipal DoctorPrincipal doctor,
            HttpServletRequest request) {
        var appointment = appointments.create(
                appointmentRequest.patientId(),
                appointmentRequest.startsAt().atZone(zoneId).toInstant(),
                parseDuration(appointmentRequest.durationMinutes()),
                doctor.id(),
                (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + appointment.getId()))
                .body(AppointmentResponse.from(appointment, zoneId));
    }

    @GetMapping("/reminders")
    AppointmentPage reminders(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        ApiPagination.validate(page, size);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("startsAt"), Sort.Order.asc("id")));
        var result = appointments.reminders(pageable);
        return new AppointmentPage(
                result.getContent().stream()
                        .map(appointment -> AppointmentResponse.from(appointment, zoneId))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @GetMapping("/{appointmentId}")
    AppointmentResponse find(@PathVariable UUID appointmentId) {
        return AppointmentResponse.from(appointments.find(appointmentId), zoneId);
    }

    @PutMapping("/{appointmentId}/schedule")
    AppointmentResponse reschedule(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody RescheduleAppointmentRequest appointmentRequest,
            @AuthenticationPrincipal DoctorPrincipal doctor,
            HttpServletRequest request) {
        return AppointmentResponse.from(
                appointments.reschedule(
                        appointmentId,
                        appointmentRequest.startsAt().atZone(zoneId).toInstant(),
                        parseDuration(appointmentRequest.durationMinutes()),
                        appointmentRequest.version(),
                        doctor.id(),
                        (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE)),
                zoneId);
    }

    @PatchMapping("/{appointmentId}/status")
    AppointmentResponse changeStatus(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody ChangeAppointmentStatusRequest statusRequest,
            @AuthenticationPrincipal DoctorPrincipal doctor,
            HttpServletRequest request) {
        return AppointmentResponse.from(
                appointments.changeStatus(
                        appointmentId,
                        parseRequiredStatus(statusRequest.status()),
                        statusRequest.version(),
                        doctor.id(),
                        (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE)),
                zoneId);
    }

    @GetMapping
    AppointmentPage search(
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ApiPagination.validate(page, size);
        var period = parsePeriod(from, to);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("startsAt"), Sort.Order.asc("id")));
        var result = appointments.search(period, parseStatus(status), patientId, pageable);
        return new AppointmentPage(
                result.getContent().stream()
                        .map(appointment -> AppointmentResponse.from(appointment, zoneId))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private AppointmentDuration parseDuration(int minutes) {
        try {
            return AppointmentDuration.ofMinutes(minutes);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(
                    List.of(new InvalidField("durationMinutes", "deve ser 15, 30, 45 ou 60")));
        }
    }

    private TimeInterval parsePeriod(LocalDateTime from, LocalDateTime to) {
        var errors = new ArrayList<InvalidField>();
        if (from == null) {
            errors.add(new InvalidField("from", "é obrigatório"));
        }
        if (to == null) {
            errors.add(new InvalidField("to", "é obrigatório"));
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        try {
            return TimeInterval.of(
                    from.atZone(zoneId).toInstant(), to.atZone(zoneId).toInstant());
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(List.of(new InvalidField("to", "deve ser posterior a from")));
        }
    }

    private AppointmentStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return AppointmentStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(List.of(new InvalidField("status", "status inválido")));
        }
    }

    private AppointmentStatus parseRequiredStatus(String status) {
        return parseStatus(status);
    }

    private record CreateAppointmentRequest(
            @NotNull(message = "é obrigatório") UUID patientId,
            @NotNull(message = "é obrigatório") LocalDateTime startsAt,
            @NotNull(message = "é obrigatório") Integer durationMinutes) {

        @Override
        public String toString() {
            return "CreateAppointmentRequest[REDACTED]";
        }
    }

    private record RescheduleAppointmentRequest(
            @NotNull(message = "é obrigatório") LocalDateTime startsAt,
            @NotNull(message = "é obrigatório") Integer durationMinutes,
            @NotNull(message = "é obrigatório") Long version) {

        @Override
        public String toString() {
            return "RescheduleAppointmentRequest[REDACTED]";
        }
    }

    private record ChangeAppointmentStatusRequest(
            @NotNull(message = "é obrigatório") String status,
            @NotNull(message = "é obrigatório") Long version) {

        @Override
        public String toString() {
            return "ChangeAppointmentStatusRequest[REDACTED]";
        }
    }

    private record AppointmentResponse(
            UUID id,
            UUID patientId,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            int durationMinutes,
            AppointmentStatus status,
            long version) {

        private static AppointmentResponse from(Appointment appointment, ZoneId zoneId) {
            return new AppointmentResponse(
                    appointment.getId(),
                    appointment.getPatientId(),
                    OffsetDateTime.ofInstant(appointment.getStartsAt(), zoneId),
                    OffsetDateTime.ofInstant(appointment.getEndsAt(), zoneId),
                    appointment.getDurationMinutes(),
                    appointment.getStatus(),
                    appointment.getVersion());
        }

        @Override
        public String toString() {
            return "AppointmentResponse[REDACTED]";
        }
    }

    private record AppointmentPage(
            List<AppointmentResponse> content, int page, int size, long totalElements, int totalPages) {

        @Override
        public String toString() {
            return "AppointmentPage[REDACTED]";
        }
    }
}
