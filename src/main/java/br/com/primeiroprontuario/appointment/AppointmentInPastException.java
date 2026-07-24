package br.com.primeiroprontuario.appointment;

public class AppointmentInPastException extends RuntimeException {

    AppointmentInPastException() {
        super("Appointment cannot start in the past");
    }
}
