package br.com.primeiroprontuario.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface AuditEventRepository extends Repository<AuditEvent, UUID> {

    <S extends AuditEvent> S save(S event);

    <S extends AuditEvent> S saveAndFlush(S event);

    @Query("""
            SELECT event
            FROM AuditEvent event
            WHERE (:filterFrom = FALSE OR event.occurredAt >= :fromInstant)
              AND (:filterTo = FALSE OR event.occurredAt < :toInstant)
              AND (:filterAction = FALSE OR event.action = :action)
              AND (:filterOutcome = FALSE OR event.outcome = :outcome)
              AND (:filterTargetType = FALSE OR event.targetType = :targetType)
              AND (:filterTargetId = FALSE OR event.targetId = :targetId)
            """)
    Page<AuditEvent> search(
            @Param("filterFrom") boolean filterFrom,
            @Param("fromInstant") Instant from,
            @Param("filterTo") boolean filterTo,
            @Param("toInstant") Instant to,
            @Param("filterAction") boolean filterAction,
            @Param("action") AuditAction action,
            @Param("filterOutcome") boolean filterOutcome,
            @Param("outcome") AuditOutcome outcome,
            @Param("filterTargetType") boolean filterTargetType,
            @Param("targetType") String targetType,
            @Param("filterTargetId") boolean filterTargetId,
            @Param("targetId") UUID targetId,
            Pageable pageable);
}
