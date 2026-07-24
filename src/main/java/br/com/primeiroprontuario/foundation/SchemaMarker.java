package br.com.primeiroprontuario.foundation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "schema_marker")
class SchemaMarker {

    @Id
    private Short id;

    @Column(name = "installed_at", nullable = false)
    private Instant installedAt;

    protected SchemaMarker() {}
}
