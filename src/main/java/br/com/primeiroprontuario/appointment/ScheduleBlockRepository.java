package br.com.primeiroprontuario.appointment;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ScheduleBlockRepository extends JpaRepository<ScheduleBlock, UUID> {

    boolean existsByStartsAtLessThanAndEndsAtGreaterThan(Instant endsAt, Instant startsAt);

    Page<ScheduleBlock> findByStartsAtLessThanAndEndsAtGreaterThan(Instant endsAt, Instant startsAt, Pageable pageable);
}
