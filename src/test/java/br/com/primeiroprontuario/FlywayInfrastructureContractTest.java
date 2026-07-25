package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FlywayInfrastructureContractTest {

    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void springAndIntegrationTestsUseFlywayWithPostgreSql18WithoutNodeOrDrizzle() throws IOException {
        var pom = read("pom.xml");
        var applicationConfiguration = read("src/main/resources/application.yml");
        var postgresqlTestContainer = read("src/test/java/br/com/primeiroprontuario/FlywayPostgreSQLContainer.java");

        assertThat(pom)
                .contains("<artifactId>spring-boot-starter-flyway</artifactId>")
                .contains("<artifactId>flyway-database-postgresql</artifactId>")
                .contains("<artifactId>postgresql</artifactId>")
                .contains("<artifactId>testcontainers-postgresql</artifactId>");
        assertThat(applicationConfiguration)
                .contains("flyway:")
                .contains("enabled: true")
                .contains("${MIGRATION_DB_URL:")
                .contains("${MIGRATION_DB_USERNAME:")
                .contains("${MIGRATION_DB_PASSWORD:");
        assertThat(postgresqlTestContainer)
                .contains("postgres:18.4")
                .contains("primeiro_prontuario_migration")
                .contains("primeiro_prontuario_runtime")
                .contains("registerFlywayProperties")
                .doesNotContain("ProcessBuilder", "npm", "drizzle");
        assertThat(PROJECT.resolve("src/main/resources/db/migration/V1__create_schema_marker.sql"))
                .exists();
        assertThat(PROJECT.resolve("src/main/resources/db/migration/V16__protect_audit_event_append_only.sql"))
                .exists();
    }

    @Test
    void activeJavaAndLocalDeploymentPathsDoNotReferenceManagedDatabaseServices() throws IOException {
        var activeFiles = Files.walk(PROJECT)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    var relative = PROJECT.relativize(path).toString();
                    return (relative.startsWith("src/")
                                    || relative.startsWith("deploy/")
                                    || relative.startsWith("scripts/"))
                            && !relative.contains("/target/")
                            && !relative.contains("/node_modules/")
                            && !relative.endsWith("FlywayInfrastructureContractTest.java")
                            && !relative.endsWith("LocalPostgreSqlInfrastructureContractTest.java");
                })
                .toList();

        for (var activeFile : activeFiles) {
            assertThat(Files.readString(activeFile))
                    .as("active path %s", PROJECT.relativize(activeFile))
                    .doesNotContainIgnoringCase("neon.tech", "Neon runtime", "DrizzlePostgreSQLContainer");
        }
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath));
    }
}
