package br.com.primeiroprontuario.patient;

import br.com.primeiroprontuario.audit.PatientAuditService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PatientService {

    private final PatientRepository patients;
    private final PatientAuditService audit;

    PatientService(PatientRepository patients, PatientAuditService audit) {
        this.patients = patients;
        this.audit = audit;
    }

    @Transactional
    Patient create(
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
            String notes,
            UUID doctorId,
            String correlationId) {
        var patient = new Patient(
                UUID.randomUUID(),
                fullName,
                motherName,
                birthDate,
                cpf,
                normalizePhone(phone),
                email,
                address,
                emergencyContact,
                insurance,
                allergies,
                notes);
        try {
            patients.saveAndFlush(patient);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateCpfException();
        }
        audit.recordCreated(doctorId, patient.getId(), correlationId);
        return patient;
    }

    @Transactional(readOnly = true)
    Patient find(UUID patientId) {
        return patients.findById(patientId).orElseThrow(PatientNotFoundException::new);
    }

    @Transactional
    Patient update(
            UUID patientId,
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
            String notes,
            long knownVersion,
            UUID doctorId,
            String correlationId) {
        var patient = patients.findById(patientId).orElseThrow(PatientNotFoundException::new);
        requireKnownVersion(patient, knownVersion);
        var normalizedPhone = normalizePhone(phone);
        var changedFields = changedFields(
                patient,
                fullName,
                motherName,
                birthDate,
                cpf,
                normalizedPhone,
                email,
                address,
                emergencyContact,
                insurance,
                allergies,
                notes);
        patient.update(
                fullName,
                motherName,
                birthDate,
                cpf,
                normalizedPhone,
                email,
                address,
                emergencyContact,
                insurance,
                allergies,
                notes);
        try {
            patients.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateCpfException();
        } catch (OptimisticLockingFailureException exception) {
            throw new PatientVersionConflictException();
        }
        audit.recordUpdated(doctorId, patient.getId(), changedFields, correlationId);
        return patient;
    }

    @Transactional
    Patient changeStatus(
            UUID patientId, PatientStatus requestedStatus, long knownVersion, UUID doctorId, String correlationId) {
        var patient = patients.findById(patientId).orElseThrow(PatientNotFoundException::new);
        requireKnownVersion(patient, knownVersion);
        if (!patient.changeStatus(requestedStatus)) {
            return patient;
        }
        try {
            patients.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw new PatientVersionConflictException();
        }
        audit.recordStatusChanged(doctorId, patient.getId(), correlationId);
        return patient;
    }

    @Transactional(readOnly = true)
    Page<Patient> search(
            String fullName,
            String motherName,
            String cpf,
            String phone,
            String email,
            PatientStatus status,
            Pageable pageable) {
        return patients.findAll(
                PatientSpecifications.matching(fullName, motherName, cpf, phone, email, status), pageable);
    }

    private String normalizePhone(String phone) {
        return phone.replaceAll("\\D", "");
    }

    private void requireKnownVersion(Patient patient, long knownVersion) {
        if (patient.getVersion() != knownVersion) {
            throw new PatientVersionConflictException();
        }
    }

    private List<String> changedFields(
            Patient patient,
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
        var fields = new ArrayList<String>();
        addIfChanged(fields, "fullName", patient.getFullName(), fullName);
        addIfChanged(fields, "motherName", patient.getMotherName(), motherName);
        addIfChanged(fields, "birthDate", patient.getBirthDate(), birthDate);
        addIfChanged(fields, "cpf", patient.getCpf(), cpf);
        addIfChanged(fields, "phone", patient.getPhone(), phone);
        addIfChanged(fields, "email", patient.getEmail(), email);
        addIfChanged(fields, "address", patient.getAddress(), address);
        addIfChanged(fields, "emergencyContact", patient.getEmergencyContact(), emergencyContact);
        addIfChanged(fields, "insurance", patient.getInsurance(), insurance);
        addIfChanged(fields, "allergies", patient.getAllergies(), allergies);
        addIfChanged(fields, "notes", patient.getNotes(), notes);
        return List.copyOf(fields);
    }

    private void addIfChanged(List<String> fields, String field, Object currentValue, Object requestedValue) {
        if (!Objects.equals(currentValue, requestedValue)) {
            fields.add(field);
        }
    }
}
