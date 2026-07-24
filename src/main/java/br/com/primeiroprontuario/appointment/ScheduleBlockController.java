package br.com.primeiroprontuario.appointment;

import br.com.primeiroprontuario.auth.DoctorPrincipal;
import br.com.primeiroprontuario.web.ApiPagination;
import br.com.primeiroprontuario.web.CorrelationIdFilter;
import br.com.primeiroprontuario.web.InvalidRequestException;
import br.com.primeiroprontuario.web.InvalidRequestException.InvalidField;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule-blocks")
class ScheduleBlockController {

    private final ScheduleBlockService blocks;
    private final ZoneId zoneId;

    ScheduleBlockController(ScheduleBlockService blocks, ZoneId appointmentZoneId) {
        this.blocks = blocks;
        this.zoneId = appointmentZoneId;
    }

    @PostMapping
    ResponseEntity<ScheduleBlockResponse> create(
            @Valid @RequestBody CreateScheduleBlockRequest blockRequest,
            @AuthenticationPrincipal DoctorPrincipal doctor,
            HttpServletRequest request) {
        var interval = parseInterval(blockRequest.startsAt(), blockRequest.endsAt());
        var block = blocks.create(interval, blockRequest.reason(), doctor.id(), (String)
                request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        return ResponseEntity.created(URI.create("/api/v1/schedule-blocks/" + block.getId()))
                .body(ScheduleBlockResponse.from(block, zoneId));
    }

    @GetMapping
    List<ScheduleBlockResponse> search(
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ApiPagination.validate(page, size);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("startsAt"), Sort.Order.asc("id")));
        return blocks.search(parsePeriod(from, to), pageable).getContent().stream()
                .map(block -> ScheduleBlockResponse.from(block, zoneId))
                .toList();
    }

    @DeleteMapping("/{blockId}")
    ResponseEntity<Void> remove(
            @PathVariable UUID blockId, @AuthenticationPrincipal DoctorPrincipal doctor, HttpServletRequest request) {
        blocks.remove(blockId, doctor.id(), (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        return ResponseEntity.noContent().build();
    }

    private TimeInterval parseInterval(LocalDateTime startsAt, LocalDateTime endsAt) {
        try {
            return TimeInterval.of(
                    startsAt.atZone(zoneId).toInstant(), endsAt.atZone(zoneId).toInstant());
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(List.of(new InvalidField("endsAt", "deve ser posterior a startsAt")));
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

    private record CreateScheduleBlockRequest(
            @NotNull(message = "é obrigatório") LocalDateTime startsAt,
            @NotNull(message = "é obrigatório") LocalDateTime endsAt,
            @NotBlank(message = "é obrigatória") String reason) {

        @Override
        public String toString() {
            return "CreateScheduleBlockRequest[REDACTED]";
        }
    }

    private record ScheduleBlockResponse(
            UUID id, OffsetDateTime startsAt, OffsetDateTime endsAt, String reason, OffsetDateTime createdAt) {

        private static ScheduleBlockResponse from(ScheduleBlock block, ZoneId zoneId) {
            return new ScheduleBlockResponse(
                    block.getId(),
                    OffsetDateTime.ofInstant(block.getStartsAt(), zoneId),
                    OffsetDateTime.ofInstant(block.getEndsAt(), zoneId),
                    block.getReason(),
                    OffsetDateTime.ofInstant(block.getCreatedAt(), zoneId));
        }

        @Override
        public String toString() {
            return "ScheduleBlockResponse[REDACTED]";
        }
    }
}
