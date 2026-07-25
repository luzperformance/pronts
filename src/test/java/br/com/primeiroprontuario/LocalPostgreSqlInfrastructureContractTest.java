package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalPostgreSqlInfrastructureContractTest {

    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void localDeploymentUsesPersistentInternalPostgreSqlWithSeparateRoles() throws IOException {
        var configMap = read("deploy/kubernetes/local/10-configmap.yaml");
        var roleInitialization = read("deploy/kubernetes/local/25-postgresql-init.yaml");
        var postgresql = read("deploy/kubernetes/local/30-postgresql.yaml");
        var deployment = read("deploy/kubernetes/40-api.yaml");
        var credentials = read("deploy/kubernetes/credentials.example.env");
        var productionConfiguration = read("src/main/resources/application-prod.yml");
        var bootstrap = read("scripts/create-local-kubernetes.sh");
        var smokeTest = read("scripts/smoke-kubernetes.sh");

        assertThat(productionConfiguration)
                .contains("minimum-idle: 1")
                .contains("maximum-pool-size: 5")
                .contains("liveness:\n          include: livenessState")
                .contains("readiness:\n          include: readinessState,db");
        assertThat(configMap).contains("POSTGRES_DB: primeiro_prontuario").doesNotContain("neon", "sslmode=require");
        assertThat(roleInitialization)
                .contains("MIGRATION_USERNAME")
                .contains("RUNTIME_USERNAME")
                .contains("ALTER DEFAULT PRIVILEGES")
                .contains("GRANT USAGE ON SCHEMA public")
                .contains("REVOKE CONNECT, TEMPORARY");
        assertThat(postgresql)
                .contains("kind: StatefulSet")
                .contains("image: postgres:18.4")
                .contains("type: ClusterIP")
                .contains("volumeClaimTemplates:")
                .doesNotContain("NodePort", "LoadBalancer");
        assertThat(credentials)
                .contains("bootstrap-username=")
                .contains("migration-username=primeiro_prontuario_migration")
                .contains("database-url=jdbc:postgresql://postgresql:5432/primeiro_prontuario")
                .contains("database-username=primeiro_prontuario_runtime")
                .doesNotContain("neon.tech", "sslmode=require");
        assertThat(credentials.lines())
                .containsExactly(
                        "bootstrap-username=primeiro_prontuario_admin",
                        "bootstrap-password=REPLACE_WITH_LOCAL_BOOTSTRAP_PASSWORD",
                        "migration-username=primeiro_prontuario_migration",
                        "migration-password=REPLACE_WITH_LOCAL_MIGRATION_PASSWORD",
                        "database-url=jdbc:postgresql://postgresql:5432/primeiro_prontuario",
                        "database-username=primeiro_prontuario_runtime",
                        "database-password=REPLACE_WITH_LOCAL_RUNTIME_PASSWORD",
                        "doctor-username=REPLACE_WITH_LOCAL_DOCTOR_USERNAME",
                        "doctor-password=REPLACE_WITH_LOCAL_DOCTOR_PASSWORD");

        assertThat(deployment)
                .contains("replicas: 1")
                .contains("name: DB_URL")
                .contains("key: database-url")
                .contains("name: DB_USERNAME")
                .contains("key: database-username")
                .contains("name: DB_PASSWORD")
                .contains("key: database-password")
                .contains("name: MIGRATION_DB_URL")
                .contains("name: MIGRATION_DB_USERNAME")
                .contains("key: migration-username")
                .contains("name: MIGRATION_DB_PASSWORD")
                .contains("key: migration-password")
                .contains("name: DOCTOR_USERNAME")
                .contains("name: DOCTOR_PASSWORD")
                .contains("path: /actuator/health/liveness")
                .contains("path: /actuator/health/readiness")
                .contains("claimName: primeiro-prontuario-attachments");

        assertThat(bootstrap)
                .contains("deploy/kubernetes/local/25-postgresql-init.yaml")
                .contains("deploy/kubernetes/local/30-postgresql.yaml")
                .contains("kubectl rollout status statefulset/postgresql")
                .doesNotContain("node", "npm", "drizzle", "DATABASE_URL", "neon");
        assertThat(smokeTest)
                .contains("kubectl rollout status statefulset/postgresql")
                .contains("verify_persisted_data");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath));
    }
}
