package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KubernetesNeonRuntimeContractTest {

    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void deploymentUsesOnlyTheDirectNeonRuntimeConnection() throws IOException {
        var configMap = read("deploy/kubernetes/10-configmap.yaml");
        var deployment = read("deploy/kubernetes/40-api.yaml");
        var credentials = read("deploy/kubernetes/credentials.example.env");
        var deploymentGuide = read("docs/deploy-kubernetes-local.md");
        var productionConfiguration = read("src/main/resources/application-prod.yml");
        var smokeTest = read("scripts/smoke-kubernetes.sh");

        assertThat(productionConfiguration)
                .contains("minimum-idle: 1")
                .contains("maximum-pool-size: 5")
                .doesNotContain("flyway:")
                .contains("liveness:\n          include: livenessState")
                .contains("readiness:\n          include: readinessState,db");
        assertThat(configMap)
                .contains("SPRING_DATASOURCE_HIKARI_DATA_SOURCE_PROPERTIES_SSLMODE: require")
                .doesNotContain("DB_URL", "POSTGRES_", "postgresql");
        assertThat(credentials)
                .contains("database-url=jdbc:postgresql://ep-")
                .contains(".neon.tech/")
                .contains("sslmode=require")
                .doesNotContain("-pooler", "migration");
        assertThat(credentials.lines())
                .containsExactly(
                        "database-url=jdbc:postgresql://ep-REPLACE_WITH_NEON_HOST.neon.tech"
                                + "/REPLACE_WITH_DATABASE?sslmode=require",
                        "database-username=REPLACE_WITH_NEON_RUNTIME_USERNAME",
                        "database-password=REPLACE_WITH_NEON_RUNTIME_PASSWORD",
                        "doctor-username=REPLACE_WITH_DOCTOR_USERNAME",
                        "doctor-password=REPLACE_WITH_DOCTOR_PASSWORD");

        assertThat(deployment)
                .contains("replicas: 1")
                .contains("name: DB_URL")
                .contains("key: database-url")
                .contains("name: DB_USERNAME")
                .contains("key: database-username")
                .contains("name: DB_PASSWORD")
                .contains("key: database-password")
                .contains("name: DOCTOR_USERNAME")
                .contains("name: DOCTOR_PASSWORD")
                .contains("path: /actuator/health/liveness")
                .contains("path: /actuator/health/readiness")
                .contains("claimName: primeiro-prontuario-attachments")
                .doesNotContain("MIGRATION", "migration");

        assertThat(deploymentGuide)
                .doesNotContain(
                        "kubectl apply -f deploy/kubernetes/30-postgresql.yaml",
                        "kubectl rollout status statefulset/postgresql");
        assertThat(smokeTest)
                .doesNotContain("kubectl delete pod postgresql-0", "kubectl rollout status statefulset/postgresql");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath));
    }
}
