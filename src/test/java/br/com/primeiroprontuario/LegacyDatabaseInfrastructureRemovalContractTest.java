package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LegacyDatabaseInfrastructureRemovalContractTest {

    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void springUsesDrizzleWithoutFlywayAndKeepsPostgreSQLTestcontainers() throws IOException {
        var pom = read("pom.xml");
        var applicationConfiguration = read("src/main/resources/application.yml");
        var productionConfiguration = read("src/main/resources/application-prod.yml");
        var databasePackage = read("database/package.json");

        assertThat(pom)
                .contains("<artifactId>postgresql</artifactId>")
                .contains("<artifactId>testcontainers-postgresql</artifactId>")
                .doesNotContain("flyway");
        assertThat(applicationConfiguration).doesNotContain("flyway:");
        assertThat(productionConfiguration).doesNotContain("flyway:");
        assertThat(databasePackage)
                .contains("\"migrate\": \"drizzle-kit migrate")
                .doesNotContain("verify:equivalence", "schema-equivalence");
        assertThat(PROJECT.resolve("src/main/resources/db/migration")).doesNotExist();
    }

    @Test
    void clusterUsesNeonAndKeepsOnlyTheAttachmentPersistentVolume() throws IOException {
        var configMap = read("deploy/kubernetes/10-configmap.yaml");
        var deployment = read("deploy/kubernetes/40-api.yaml");
        var attachmentVolume = read("deploy/kubernetes/20-attachment-pvc.yaml");
        var credentials = read("deploy/kubernetes/credentials.example.env");
        var smokeTest = read("scripts/smoke-kubernetes.sh");

        assertThat(PROJECT.resolve("deploy/kubernetes/30-postgresql.yaml")).doesNotExist();
        assertThat(configMap).doesNotContain("POSTGRES_", "jdbc:postgresql://postgresql");
        assertThat(deployment)
                .contains("claimName: primeiro-prontuario-attachments")
                .doesNotContain("postgresql", "migration");
        assertThat(attachmentVolume).contains("name: primeiro-prontuario-attachments");
        assertThat(credentials.lines())
                .containsExactly(
                        "database-url=jdbc:postgresql://ep-REPLACE_WITH_NEON_HOST.neon.tech"
                                + "/REPLACE_WITH_DATABASE?sslmode=require",
                        "database-username=REPLACE_WITH_NEON_RUNTIME_USERNAME",
                        "database-password=REPLACE_WITH_NEON_RUNTIME_PASSWORD",
                        "doctor-username=REPLACE_WITH_DOCTOR_USERNAME",
                        "doctor-password=REPLACE_WITH_DOCTOR_PASSWORD");
        assertThat(smokeTest).doesNotContain("postgresql-0", "statefulset/postgresql");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath));
    }
}
