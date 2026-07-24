package br.com.primeiroprontuario.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doctor_account")
class DoctorAccount {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DoctorAccount() {}

    DoctorAccount(UUID id, String username, String passwordHash, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.active = true;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    String getUsername() {
        return username;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    boolean isActive() {
        return active;
    }

    void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
