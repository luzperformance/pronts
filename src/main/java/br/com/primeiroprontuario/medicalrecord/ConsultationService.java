package br.com.primeiroprontuario.medicalrecord;

import br.com.primeiroprontuario.appointment.AppointmentConsultationPolicy;
import br.com.primeiroprontuario.audit.ConsultationAuditService;
import br.com.primeiroprontuario.patient.PatientSchedulingPolicy;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ConsultationService {

    private final ConsultationRepository consultations;
    private final AddendumRepository addenda;
    private final PatientSchedulingPolicy patientPolicy;
    private final AppointmentConsultationPolicy appointmentPolicy;
    private final ConsultationAuditService audit;
    private final Clock clock;

    ConsultationService(
            ConsultationRepository consultations,
            AddendumRepository addenda,
            PatientSchedulingPolicy patientPolicy,
            AppointmentConsultationPolicy appointmentPolicy,
            ConsultationAuditService audit,
            Clock clock) {
        this.consultations = consultations;
        this.addenda = addenda;
        this.patientPolicy = patientPolicy;
        this.appointmentPolicy = appointmentPolicy;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    Consultation create(
            UUID patientId,
            UUID appointmentId,
            String anamnesis,
            String chiefComplaint,
            String physicalExamination,
            String diagnosticHypotheses,
            String treatmentPlan,
            String observations,
            java.time.Instant clinicalDate) {
        patientPolicy.requireActive(patientId);
        var appointmentPatientId = appointmentId == null ? null : appointmentPolicy.patientIdOf(appointmentId);
        if (appointmentId != null && consultations.existsByAppointmentId(appointmentId)) {
            throw new ConsultationAppointmentConflictException();
        }
        var consultation = new Consultation(
                UUID.randomUUID(),
                patientId,
                appointmentId,
                appointmentPatientId,
                anamnesis,
                chiefComplaint,
                physicalExamination,
                diagnosticHypotheses,
                treatmentPlan,
                observations,
                clinicalDate,
                clock.instant());
        try {
            return consultations.saveAndFlush(consultation);
        } catch (DataIntegrityViolationException exception) {
            throw new ConsultationAppointmentConflictException();
        }
    }

    @Transactional(readOnly = true)
    ConsultationDetails find(UUID consultationId) {
        var consultation = consultations.findById(consultationId).orElseThrow(ConsultationNotFoundException::new);
        return new ConsultationDetails(
                consultation, addenda.findByConsultationIdOrderByCreatedAtAscIdAsc(consultationId));
    }

    @Transactional
    Consultation update(
            UUID consultationId,
            String anamnesis,
            String chiefComplaint,
            String physicalExamination,
            String diagnosticHypotheses,
            String treatmentPlan,
            String observations,
            long knownVersion) {
        var consultation = consultations.findById(consultationId).orElseThrow(ConsultationNotFoundException::new);
        if (consultation.getVersion() != knownVersion) {
            throw new ConsultationVersionConflictException();
        }
        consultation.update(
                anamnesis, chiefComplaint, physicalExamination, diagnosticHypotheses, treatmentPlan, observations);
        flushDetectingVersionConflict();
        return consultation;
    }

    @Transactional
    ConsultationFinalization finalize(UUID consultationId, UUID doctorId, String correlationId) {
        var consultation = consultations.findById(consultationId).orElseThrow(ConsultationNotFoundException::new);
        var transitioned = consultation.finalize(doctorId, clock.instant().truncatedTo(ChronoUnit.MICROS));
        flushDetectingVersionConflict();
        if (transitioned) {
            if (consultation.getAppointmentId() != null) {
                appointmentPolicy.completeIfActive(consultation.getAppointmentId());
            }
            audit.recordFinalized(doctorId, consultationId, correlationId);
        }
        return new ConsultationFinalization(consultation, !transitioned);
    }

    private void flushDetectingVersionConflict() {
        try {
            consultations.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw new ConsultationVersionConflictException();
        }
    }
}

record ConsultationFinalization(Consultation consultation, boolean alreadyFinalized) {}

record ConsultationDetails(Consultation consultation, java.util.List<Addendum> addenda) {}
