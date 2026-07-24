package br.com.primeiroprontuario.appointment;

public class AppointmentNotFoundException extends RuntimeException {

    AppointmentNotFoundException() {
        super("Appointment not found");
    }
}
