package br.com.primeiroprontuario.appointment;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "schedule_calendar")
class ScheduleCalendar {

    @Id
    private short id;

    protected ScheduleCalendar() {}
}
