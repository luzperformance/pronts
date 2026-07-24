package br.com.primeiroprontuario.medicalrecord;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    boolean existsByAppointmentId(UUID appointmentId);

    @Query(value = """
                    select
                        consultation.id as id,
                        consultation.clinicalDate as clinicalDate,
                        consultation.createdAt as createdAt,
                        consultation.anamnesis as anamnesis,
                        consultation.chiefComplaint as chiefComplaint,
                        consultation.physicalExamination as physicalExamination,
                        consultation.diagnosticHypotheses as diagnosticHypotheses,
                        consultation.treatmentPlan as treatmentPlan,
                        consultation.observations as observations,
                        consultation.finalizedBy as finalizedBy,
                        consultation.finalizedAt as finalizedAt
                    from Consultation consultation
                    where consultation.patientId = :patientId
                      and consultation.status = :status
                      and consultation.clinicalDate >= :from
                      and consultation.clinicalDate < :to
                    order by consultation.clinicalDate, consultation.createdAt, consultation.id
                    """, countQuery = """
                    select count(consultation)
                    from Consultation consultation
                    where consultation.patientId = :patientId
                      and consultation.status = :status
                      and consultation.clinicalDate >= :from
                      and consultation.clinicalDate < :to
                    """)
    Page<MedicalRecordEntry> findMedicalRecord(
            @Param("patientId") UUID patientId,
            @Param("status") ConsultationStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}

interface MedicalRecordEntry {

    UUID getId();

    Instant getClinicalDate();

    Instant getCreatedAt();

    String getAnamnesis();

    String getChiefComplaint();

    String getPhysicalExamination();

    String getDiagnosticHypotheses();

    String getTreatmentPlan();

    String getObservations();

    UUID getFinalizedBy();

    Instant getFinalizedAt();
}
