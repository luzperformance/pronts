package br.com.primeiroprontuario.audit;

import br.com.primeiroprontuario.web.ApiPagination;
import br.com.primeiroprontuario.web.InvalidRequestException;
import br.com.primeiroprontuario.web.InvalidRequestException.InvalidField;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
class AuditEventController {

    private final AuditEventRepository events;

    AuditEventController(AuditEventRepository events) {
        this.events = events;
    }

    @GetMapping
    AuditEventPage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId) {
        ApiPagination.validate(page, size);
        var parsedFrom = parseInstant(from, "from");
        var parsedTo = parseInstant(to, "to");
        validatePeriod(parsedFrom, parsedTo);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        var result = events.search(
                parsedFrom != null,
                parsedFrom,
                parsedTo != null,
                parsedTo,
                action != null,
                parseEnum(action, "action", AuditAction.class),
                outcome != null,
                parseEnum(outcome, "outcome", AuditOutcome.class),
                targetType != null,
                targetType,
                targetId != null,
                parseUuid(targetId),
                pageable);
        var content = result.getContent().stream().map(AuditEventItem::from).toList();
        return new AuditEventPage(
                content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private Instant parseInstant(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException exception) {
            throw invalid(field, "instante inválido");
        }
    }

    private void validatePeriod(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw invalid("to", "deve ser posterior a from");
        }
    }

    private <T extends Enum<T>> T parseEnum(String value, String field, Class<T> enumType) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw invalid(field, "valor inválido");
        }
    }

    private UUID parseUuid(String targetId) {
        if (targetId == null) {
            return null;
        }
        try {
            return UUID.fromString(targetId);
        } catch (IllegalArgumentException exception) {
            throw invalid("targetId", "identificador inválido");
        }
    }

    private InvalidRequestException invalid(String field, String message) {
        return new InvalidRequestException(List.of(new InvalidField(field, message)));
    }

    private record AuditEventPage(
            List<AuditEventItem> content, int page, int size, long totalElements, int totalPages) {}

    private record AuditEventItem(
            UUID id,
            UUID actorId,
            AuditAction action,
            String targetType,
            UUID targetId,
            AuditOutcome outcome,
            Instant occurredAt,
            String correlationId,
            List<String> changedFields) {

        private static AuditEventItem from(AuditEvent event) {
            return new AuditEventItem(
                    event.getId(),
                    event.getActorId(),
                    event.getAction(),
                    event.getTargetType(),
                    event.getTargetId(),
                    event.getOutcome(),
                    event.getOccurredAt(),
                    event.getCorrelationId(),
                    event.getChangedFields());
        }
    }
}
