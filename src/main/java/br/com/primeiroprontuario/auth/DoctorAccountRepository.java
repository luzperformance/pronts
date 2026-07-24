package br.com.primeiroprontuario.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DoctorAccountRepository extends JpaRepository<DoctorAccount, UUID> {

    Optional<DoctorAccount> findByUsername(String username);
}
