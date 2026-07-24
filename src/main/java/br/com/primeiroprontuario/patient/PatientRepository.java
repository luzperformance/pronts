package br.com.primeiroprontuario.patient;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface PatientRepository extends JpaRepository<Patient, UUID>, JpaSpecificationExecutor<Patient> {}
