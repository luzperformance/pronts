package br.com.primeiroprontuario.patient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patient")
class Patient {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "mother_name", nullable = false)
    private String motherName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(nullable = false)
    private String phone;

    private String email;

    private String address;

    @Column(name = "emergency_contact")
    private String emergencyContact;

    private String insurance;

    private String allergies;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatientStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Patient() {}

    Patient(
            UUID id,
            String fullName,
            String motherName,
            LocalDate birthDate,
            Cpf cpf,
            String phone,
            String email,
            String address,
            String emergencyContact,
            String insurance,
            String allergies,
            String notes) {
        this.id = id;
        this.fullName = fullName;
        this.motherName = motherName;
        this.birthDate = birthDate;
        this.cpf = cpf.value();
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.emergencyContact = emergencyContact;
        this.insurance = insurance;
        this.allergies = allergies;
        this.notes = notes;
        this.status = PatientStatus.ACTIVE;
    }

    void update(
            String fullName,
            String motherName,
            LocalDate birthDate,
            Cpf cpf,
            String phone,
            String email,
            String address,
            String emergencyContact,
            String insurance,
            String allergies,
            String notes) {
        this.fullName = fullName;
        this.motherName = motherName;
        this.birthDate = birthDate;
        this.cpf = cpf.value();
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.emergencyContact = emergencyContact;
        this.insurance = insurance;
        this.allergies = allergies;
        this.notes = notes;
    }

    boolean changeStatus(PatientStatus requestedStatus) {
        if (status == requestedStatus) {
            return false;
        }
        status = requestedStatus;
        return true;
    }

    UUID getId() {
        return id;
    }

    String getFullName() {
        return fullName;
    }

    String getMotherName() {
        return motherName;
    }

    LocalDate getBirthDate() {
        return birthDate;
    }

    Cpf getCpf() {
        return Cpf.of(cpf);
    }

    String getPhone() {
        return phone;
    }

    String getEmail() {
        return email;
    }

    String getAddress() {
        return address;
    }

    String getEmergencyContact() {
        return emergencyContact;
    }

    String getInsurance() {
        return insurance;
    }

    String getAllergies() {
        return allergies;
    }

    String getNotes() {
        return notes;
    }

    PatientStatus getStatus() {
        return status;
    }

    long getVersion() {
        return version;
    }
}
