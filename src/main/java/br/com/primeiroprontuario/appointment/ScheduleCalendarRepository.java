package br.com.primeiroprontuario.appointment;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ScheduleCalendarRepository extends JpaRepository<ScheduleCalendar, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select calendar from ScheduleCalendar calendar where calendar.id = 1")
    Optional<ScheduleCalendar> findForMutation();
}
