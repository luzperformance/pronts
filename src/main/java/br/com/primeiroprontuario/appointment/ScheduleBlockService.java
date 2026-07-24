package br.com.primeiroprontuario.appointment;

import br.com.primeiroprontuario.audit.ScheduleBlockAuditService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ScheduleBlockService {

    private final ScheduleBlockRepository blocks;
    private final AppointmentRepository appointments;
    private final ScheduleCalendarRepository calendar;
    private final ScheduleBlockAuditService audit;
    private final Clock clock;

    ScheduleBlockService(
            ScheduleBlockRepository blocks,
            AppointmentRepository appointments,
            ScheduleCalendarRepository calendar,
            ScheduleBlockAuditService audit,
            Clock clock) {
        this.blocks = blocks;
        this.appointments = appointments;
        this.calendar = calendar;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    ScheduleBlock create(TimeInterval interval, String reason, UUID doctorId, String correlationId) {
        var now = clock.instant();
        if (!interval.startsAt().isAfter(now)) {
            throw new ScheduleBlockNotFutureException();
        }
        calendar.findForMutation().orElseThrow(IllegalStateException::new);
        if (appointments.existsByStatusNotAndStartsAtLessThanAndEndsAtGreaterThan(
                        AppointmentStatus.CANCELLED, interval.endsAt(), interval.startsAt())
                || blocks.existsByStartsAtLessThanAndEndsAtGreaterThan(interval.endsAt(), interval.startsAt())) {
            throw new ScheduleBlockConflictException();
        }
        var block = new ScheduleBlock(UUID.randomUUID(), interval, reason.trim(), now);
        blocks.saveAndFlush(block);
        audit.recordCreated(doctorId, block.getId(), correlationId);
        return block;
    }

    @Transactional(readOnly = true)
    Page<ScheduleBlock> search(TimeInterval period, Pageable pageable) {
        return blocks.findByStartsAtLessThanAndEndsAtGreaterThan(period.endsAt(), period.startsAt(), pageable);
    }

    @Transactional
    void remove(UUID blockId, UUID doctorId, String correlationId) {
        calendar.findForMutation().orElseThrow(IllegalStateException::new);
        var block = blocks.findById(blockId).orElseThrow(ScheduleBlockNotFoundException::new);
        if (!block.getStartsAt().isAfter(clock.instant())) {
            throw new ScheduleBlockNotFutureException();
        }
        blocks.delete(block);
        audit.recordRemoved(doctorId, blockId, correlationId);
    }
}
