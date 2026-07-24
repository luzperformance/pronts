package br.com.primeiroprontuario.appointment;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AppointmentConsultationPolicy {

    private final AppointmentRepository appointments;
    private final ScheduleCalendarRepository calendar;

    AppointmentConsultationPolicy(AppointmentRepository appointments, ScheduleCalendarRepository calendar) {
        this.appointments = appointments;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public UUID patientIdOf(UUID appointmentId) {
        return appointments
                .findById(appointmentId)
                .orElseThrow(AppointmentNotFoundException::new)
                .getPatientId();
    }

    @Transactional
    public void completeIfActive(UUID appointmentId) {
        calendar.findForMutation().orElseThrow(IllegalStateException::new);
        var appointment = appointments.findById(appointmentId).orElseThrow(AppointmentNotFoundException::new);
        if (appointment.completeIfActive()) {
            appointments.flush();
        }
    }
}
