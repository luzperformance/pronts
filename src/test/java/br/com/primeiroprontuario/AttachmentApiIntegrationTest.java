package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.primeiroprontuario.attachment.AttachmentStorage;
import br.com.primeiroprontuario.attachment.FileSystemAttachmentStorage;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        classes = PrimeiroProntuarioApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.doctor.username=doctor",
            "app.doctor.password=valid-test-password",
            "server.servlet.session.cookie.secure=false"
        })
@Import(AttachmentApiIntegrationTest.TestStorageConfiguration.class)
class AttachmentApiIntegrationTest extends DrizzleSpringIntegrationTest {

    private static final Path STORAGE_DIRECTORY = createStorageDirectory();

    @Container
    @ServiceConnection
    private static final DrizzlePostgreSQLContainer POSTGRESQL = new DrizzlePostgreSQLContainer();

    @LocalServerPort
    private int port;

    @Autowired
    private FaultInjectingAttachmentStorage faultInjectingStorage;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void attachmentStorageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.attachments.directory", STORAGE_DIRECTORY::toString);
    }

    @BeforeEach
    void resetStorageFaults() {
        faultInjectingStorage.reset();
    }

    @Test
    void validPdfCanBeUploadedListedAndRetrievedWithoutStorageDetails() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.026-58");
        var content = minimalPdf();

        var created = upload(client, patientId, "laudo.pdf", "application/pdf", content, null);

        assertThat(created.statusCode()).isEqualTo(201);
        var attachmentId = JsonPath.<String>read(created.body(), "$.id");
        assertThat(created.headers().firstValue("Location")).contains("/api/v1/attachments/" + attachmentId);
        assertThat(JsonPath.<String>read(created.body(), "$.patientId")).isEqualTo(patientId);
        assertThat(JsonPath.<String>read(created.body(), "$.originalFilename")).isEqualTo("laudo.pdf");
        assertThat(JsonPath.<String>read(created.body(), "$.mediaType")).isEqualTo("application/pdf");
        assertThat(JsonPath.<Integer>read(created.body(), "$.size")).isEqualTo(content.length);
        assertThat(JsonPath.<String>read(created.body(), "$.sha256")).isEqualTo(sha256(content));
        assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("ACTIVE");
        assertThat(created.body()).doesNotContain("storage", STORAGE_DIRECTORY.toString());

        var listed = get(client, "/api/v1/patients/" + patientId + "/attachments");
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(listed.body(), "$[*].id")).containsExactly(attachmentId);

        var details = get(client, "/api/v1/attachments/" + attachmentId);
        assertThat(details.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(details.body(), "$.id")).isEqualTo(attachmentId);
        assertThat(details.body()).doesNotContain("storage", STORAGE_DIRECTORY.toString());
    }

    @Test
    void jpegPngAndMarkdownAreAcceptedWithoutConsultationAndUploadAuditIsMinimal() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.027-39");
        var jpeg = image("jpg");
        var png = image("png");
        var markdown = "# Evolução\n\nConteúdo textual em UTF-8.\n".getBytes(StandardCharsets.UTF_8);

        var jpegCreated = upload(client, patientId, "../../arquivo-perigoso.jpg", "image/jpeg", jpeg, null);
        var pngCreated = upload(client, patientId, "imagem.png", "image/png", png, null);
        var markdownCreated = upload(client, patientId, "evolucao.md", "text/markdown", markdown, null);

        assertThat(jpegCreated.statusCode()).isEqualTo(201);
        assertThat(pngCreated.statusCode()).isEqualTo(201);
        assertThat(markdownCreated.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(jpegCreated.body(), "$.originalFilename"))
                .isEqualTo("../../arquivo-perigoso.jpg");
        assertThat(JsonPath.<String>read(jpegCreated.body(), "$.mediaType")).isEqualTo("image/jpeg");
        assertThat(JsonPath.<String>read(pngCreated.body(), "$.mediaType")).isEqualTo("image/png");
        assertThat(JsonPath.<String>read(markdownCreated.body(), "$.mediaType")).isEqualTo("text/markdown");
        assertThat(JsonPath.<Object>read(markdownCreated.body(), "$.consultationId"))
                .isNull();

        var audit = get(client, "/api/v1/audit-events?action=ATTACHMENT_UPLOADED&size=100");
        assertThat(audit.statusCode()).isEqualTo(200);
        for (var response : List.of(jpegCreated, pngCreated, markdownCreated)) {
            var attachmentId = JsonPath.<String>read(response.body(), "$.id");
            var events = JsonPath.<List<Map<String, Object>>>read(
                    audit.body(), "$.content[?(@.targetId == '" + attachmentId + "')]");
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.get("action")).isEqualTo("ATTACHMENT_UPLOADED");
                assertThat(event.get("targetType")).isEqualTo("ATTACHMENT");
                assertThat(event.get("changedFields")).isEqualTo(List.of());
            });
        }
        assertThat(audit.body())
                .doesNotContain(
                        "../../arquivo-perigoso.jpg", "Conteúdo textual em UTF-8", STORAGE_DIRECTORY.toString());
    }

    @Test
    void falseExtensionInvalidContentAndUnallowedInformedTypeReturnUnsupportedMediaType() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.028-10");

        var invalidContent = upload(
                client,
                patientId,
                "documento.pdf",
                "application/pdf",
                "isto não é um PDF".getBytes(StandardCharsets.UTF_8),
                null);
        var falseExtension = upload(client, patientId, "documento.png", "image/png", minimalPdf(), null);
        var falseInformedType = upload(client, patientId, "documento.pdf", "text/plain", minimalPdf(), null);
        var unsupported = upload(
                client,
                patientId,
                "programa.exe",
                "application/octet-stream",
                new byte[] {0x00, 0x01, 0x02, 0x03},
                null);

        for (var rejected : List.of(invalidContent, falseExtension, falseInformedType, unsupported)) {
            assertThat(rejected.statusCode()).isEqualTo(415);
            assertThat(JsonPath.<String>read(rejected.body(), "$.type"))
                    .isEqualTo("urn:problem:unsupported-media-type");
            assertThat(rejected.body()).doesNotContain(STORAGE_DIRECTORY.toString());
        }
        var listed = get(client, "/api/v1/patients/" + patientId + "/attachments");
        assertThat(JsonPath.<List<Object>>read(listed.body(), "$")).isEmpty();
    }

    @Test
    void tenMebibytesIsAcceptedAndTheNextByteReturnsPayloadTooLargeBeforeDefinitiveStorage() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.029-09");
        var atLimit = new byte[10 * 1024 * 1024];
        Arrays.fill(atLimit, (byte) 'a');
        var aboveLimit = Arrays.copyOf(atLimit, atLimit.length + 1);
        aboveLimit[aboveLimit.length - 1] = 'b';

        var accepted = upload(client, patientId, "limite.md", "text/markdown", atLimit, null);
        var filesBeforeRejectedUpload = storedFileCount();
        var rejected = upload(client, patientId, "acima.md", "text/markdown", aboveLimit, null);

        assertThat(accepted.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<Integer>read(accepted.body(), "$.size")).isEqualTo(atLimit.length);
        assertThat(rejected.statusCode()).isEqualTo(413);
        assertThat(JsonPath.<String>read(rejected.body(), "$.type")).isEqualTo("urn:problem:payload-too-large");
        assertThat(storedFileCount()).isEqualTo(filesBeforeRejectedUpload);
        var listed = get(client, "/api/v1/patients/" + patientId + "/attachments");
        assertThat(JsonPath.<List<String>>read(listed.body(), "$[*].originalFilename"))
                .containsExactly("limite.md");
    }

    @Test
    void attachmentListRejectsAnOversizedPage() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.038-91");

        var response = get(client, "/api/v1/patients/" + patientId + "/attachments?size=101");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].field")).isEqualTo("size");
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].message"))
                .isEqualTo("deve estar entre 1 e 100");
    }

    @Test
    void uploadWithoutTheRequiredFileReturnsSanitizedProblemDetails() throws Exception {
        var client = authenticatedClient();
        var csrf = csrf(client);
        var boundary = "pp-attachment-" + UUID.randomUUID();

        var response = send(
                client,
                "POST",
                "/api/v1/patients/" + UUID.randomUUID() + "/attachments",
                ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8),
                csrf.headerName(),
                csrf.token(),
                "multipart/form-data; boundary=" + boundary);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:invalid-request");
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo("Um ou mais campos são inválidos.");
        assertThat(JsonPath.<String>read(response.body(), "$.errors[0].field")).isEqualTo("file");
        assertThat(response.headers().firstValue("X-Correlation-ID"))
                .contains(JsonPath.<String>read(response.body(), "$.correlationId"));
        assertThat(response.body()).doesNotContain("MissingServletRequestPartException", "stackTrace");
    }

    @Test
    void consultationCanBeLinkedOnlyWhenItBelongsToTheAttachmentPatient() throws Exception {
        var client = authenticatedClient();
        var firstPatientId = createPatient(client, "100.000.030-34");
        var secondPatientId = createPatient(client, "100.000.031-15");
        var consultationId = createConsultation(client, firstPatientId);

        var linked = upload(client, firstPatientId, "consulta.pdf", "application/pdf", minimalPdf(), consultationId);
        var conflict = upload(client, secondPatientId, "consulta.pdf", "application/pdf", minimalPdf(), consultationId);

        assertThat(linked.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(linked.body(), "$.consultationId")).isEqualTo(consultationId);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(conflict.body(), "$.type")).isEqualTo("urn:problem:conflict");
        var secondPatientAttachments = get(client, "/api/v1/patients/" + secondPatientId + "/attachments");
        assertThat(JsonPath.<List<Object>>read(secondPatientAttachments.body(), "$"))
                .isEmpty();
    }

    @Test
    void failureAtTheStorageBoundaryAfterPromotionIsCompensated() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.032-04");
        var filesBeforeUpload = storedFileCount();
        faultInjectingStorage.failAfterNextPromotion();

        var failed = upload(client, patientId, "falha.pdf", "application/pdf", minimalPdf(), null);

        assertThat(failed.statusCode()).isEqualTo(500);
        assertThat(failed.body()).doesNotContain("controlled", STORAGE_DIRECTORY.toString());
        assertThat(storedFileCount()).isEqualTo(filesBeforeUpload);
        var listed = get(client, "/api/v1/patients/" + patientId + "/attachments");
        assertThat(JsonPath.<List<Object>>read(listed.body(), "$")).isEmpty();
    }

    @Test
    void auditFailureRollsBackMetadataAndCompensatesThePromotedFile() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.033-87");
        var filesBeforeUpload = storedFileCount();
        var auditBeforeFailure = get(client, "/api/v1/audit-events?action=ATTACHMENT_UPLOADED&size=100");
        var auditCountBeforeFailure = JsonPath.<Number>read(auditBeforeFailure.body(), "$.totalElements")
                .longValue();
        executeAsMigration(POSTGRESQL, """
                ALTER TABLE audit_event
                ADD CONSTRAINT pp016_force_audit_failure
                CHECK (action <> 'ATTACHMENT_UPLOADED')
                NOT VALID
                """);

        HttpResponse<String> failed;
        try {
            failed = upload(client, patientId, "auditoria.pdf", "application/pdf", minimalPdf(), null);
        } finally {
            executeAsMigration(POSTGRESQL, "ALTER TABLE audit_event DROP CONSTRAINT pp016_force_audit_failure");
        }

        assertThat(failed.statusCode()).isEqualTo(500);
        assertThat(storedFileCount()).isEqualTo(filesBeforeUpload);
        var listed = get(client, "/api/v1/patients/" + patientId + "/attachments");
        assertThat(JsonPath.<List<Object>>read(listed.body(), "$")).isEmpty();
        var auditAfterFailure = get(client, "/api/v1/audit-events?action=ATTACHMENT_UPLOADED&size=100");
        assertThat(JsonPath.<Number>read(auditAfterFailure.body(), "$.totalElements")
                        .longValue())
                .isEqualTo(auditCountBeforeFailure);
    }

    @Test
    void authenticatedDownloadStreamsTheUploadedBytesWithSafeHeadersAndMinimalAudit() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.034-68");
        var original = minimalPdf();
        var created = upload(client, patientId, "../../relatorio-final.pdf", "application/pdf", original, null);
        var attachmentId = JsonPath.<String>read(created.body(), "$.id");

        var downloaded = getBytes(client, "/api/v1/attachments/" + attachmentId + "/content");

        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).containsExactly(original);
        assertThat(sha256(downloaded.body())).isEqualTo(sha256(original));
        assertThat(downloaded.headers().firstValue("Content-Type")).contains("application/pdf");
        var disposition = downloaded.headers().firstValue("Content-Disposition").orElseThrow();
        assertThat(disposition).startsWith("attachment;");
        assertThat(disposition).doesNotContain("\r", "\n", STORAGE_DIRECTORY.toString());
        assertThat(downloaded.headers().map().toString()).doesNotContain("storage", STORAGE_DIRECTORY.toString());

        var audit = get(client, "/api/v1/audit-events?action=ATTACHMENT_DOWNLOADED");
        var events = JsonPath.<List<Map<String, Object>>>read(
                audit.body(), "$.content[?(@.targetId == '" + attachmentId + "')]");
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("ATTACHMENT_DOWNLOADED");
            assertThat(event.get("targetType")).isEqualTo("ATTACHMENT");
            assertThat(event.get("changedFields")).isEqualTo(List.of());
        });
        assertThat(audit.body())
                .doesNotContain(
                        "../../relatorio-final.pdf",
                        STORAGE_DIRECTORY.toString(),
                        new String(original, StandardCharsets.US_ASCII));
    }

    @Test
    void downloadRequiresSessionAndMissingAttachmentReturnsSafeNotFound() throws Exception {
        var unauthenticated = HttpClient.newHttpClient();

        var unauthorized = getBytes(unauthenticated, "/api/v1/attachments/" + UUID.randomUUID() + "/content");
        var authenticated = authenticatedClient();
        var missing = getBytes(authenticated, "/api/v1/attachments/" + UUID.randomUUID() + "/content");

        assertThat(unauthorized.statusCode()).isEqualTo(401);
        assertThat(new String(unauthorized.body(), StandardCharsets.UTF_8))
                .doesNotContain(STORAGE_DIRECTORY.toString());
        assertThat(missing.statusCode()).isEqualTo(404);
        var missingBody = new String(missing.body(), StandardCharsets.UTF_8);
        assertThat(JsonPath.<String>read(missingBody, "$.type")).isEqualTo("urn:problem:resource-not-found");
        assertThat(missingBody).doesNotContain(STORAGE_DIRECTORY.toString());
    }

    @Test
    void markdownIsDownloadedAsUtf8AttachmentAndEncodedFilenameCannotInjectAHeader() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.035-49");
        var markdown = "# Anotação\n\nTexto em UTF-8: ação.\n".getBytes(StandardCharsets.UTF_8);
        var created = uploadWithEncodedFilename(
                client, patientId, "anota%C3%A7%C3%A3o%0D%0AX-Injected%3A%20yes.md", "text/markdown", markdown);
        assertThat(created.statusCode()).isEqualTo(201);
        var attachmentId = JsonPath.<String>read(created.body(), "$.id");

        var downloaded = getBytes(client, "/api/v1/attachments/" + attachmentId + "/content");

        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).containsExactly(markdown);
        assertThat(downloaded.headers().firstValue("Content-Type"))
                .hasValueSatisfying(
                        value -> assertThat(value.toLowerCase()).contains("text/markdown", "charset=utf-8"));
        assertThat(downloaded.headers().firstValue("Content-Disposition"))
                .hasValueSatisfying(
                        value -> assertThat(value).startsWith("attachment;").doesNotContain("\r", "\n"));
        assertThat(downloaded.headers().firstValue("X-Injected")).isEmpty();
        assertThat(new String(downloaded.body(), StandardCharsets.UTF_8))
                .isEqualTo("# Anotação\n\nTexto em UTF-8: ação.\n")
                .doesNotContain("<html", "<script");
    }

    @Test
    void attachmentRemovalCreatesMinimalTombstoneAndMakesBinaryUnavailable() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.036-20");
        var content = minimalPdf();
        var filesBeforeUpload = storedFileCount();
        var created = upload(client, patientId, "laudo-sensivel.pdf", "application/pdf", content, null);
        var attachmentId = JsonPath.<String>read(created.body(), "$.id");

        var removed = remove(client, attachmentId, "Anexo enviado para paciente incorreto");

        assertThat(removed.statusCode()).isEqualTo(204);
        assertThat(storedFileCount()).isEqualTo(filesBeforeUpload);

        var listed = get(client, "/api/v1/patients/" + patientId + "/attachments");
        assertThat(JsonPath.<List<Object>>read(listed.body(), "$")).isEmpty();

        var tombstone = get(client, "/api/v1/attachments/" + attachmentId);
        assertThat(tombstone.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(tombstone.body(), "$.id")).isEqualTo(attachmentId);
        assertThat(JsonPath.<String>read(tombstone.body(), "$.patientId")).isEqualTo(patientId);
        assertThat(JsonPath.<String>read(tombstone.body(), "$.mediaType")).isEqualTo("application/pdf");
        assertThat(JsonPath.<Integer>read(tombstone.body(), "$.size")).isEqualTo(content.length);
        assertThat(JsonPath.<String>read(tombstone.body(), "$.sha256")).isEqualTo(sha256(content));
        assertThat(JsonPath.<String>read(tombstone.body(), "$.status")).isEqualTo("REMOVED");
        assertThat(JsonPath.<String>read(tombstone.body(), "$.removalJustification"))
                .isEqualTo("Anexo enviado para paciente incorreto");
        assertThat(JsonPath.<String>read(tombstone.body(), "$.removedBy")).isNotBlank();
        assertThat(JsonPath.<String>read(tombstone.body(), "$.removedAt")).isNotBlank();
        assertThat(tombstone.body())
                .doesNotContain(
                        "originalFilename",
                        "laudo-sensivel.pdf",
                        "uploadedBy",
                        "createdAt",
                        "storage",
                        STORAGE_DIRECTORY.toString());

        var unavailable = getBytes(client, "/api/v1/attachments/" + attachmentId + "/content");
        assertThat(unavailable.statusCode()).isEqualTo(410);
        var unavailableBody = new String(unavailable.body(), StandardCharsets.UTF_8);
        assertThat(JsonPath.<String>read(unavailableBody, "$.type")).isEqualTo("urn:problem:resource-gone");

        var audit = get(client, "/api/v1/audit-events?action=ATTACHMENT_REMOVED");
        var events = JsonPath.<List<Map<String, Object>>>read(
                audit.body(), "$.content[?(@.targetId == '" + attachmentId + "')]");
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("ATTACHMENT_REMOVED");
            assertThat(event.get("targetType")).isEqualTo("ATTACHMENT");
            assertThat(event.get("changedFields")).isEqualTo(List.of());
        });
        assertThat(audit.body())
                .doesNotContain(
                        "Anexo enviado para paciente incorreto",
                        "laudo-sensivel.pdf",
                        STORAGE_DIRECTORY.toString(),
                        new String(content, StandardCharsets.US_ASCII));
    }

    @Test
    void blankRemovalJustificationIsRejectedWithoutChangingTheActiveAttachment() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.037-00");
        var content = minimalPdf();
        var created = upload(client, patientId, "laudo.pdf", "application/pdf", content, null);
        var attachmentId = JsonPath.<String>read(created.body(), "$.id");
        var filesBeforeRemoval = storedFileCount();

        var rejected = remove(client, attachmentId, "   ");

        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(rejected.body(), "$.errors[*].field"))
                .containsExactly("justification");
        assertThat(storedFileCount()).isEqualTo(filesBeforeRemoval);
        var details = get(client, "/api/v1/attachments/" + attachmentId);
        assertThat(JsonPath.<String>read(details.body(), "$.status")).isEqualTo("ACTIVE");
        var downloaded = getBytes(client, "/api/v1/attachments/" + attachmentId + "/content");
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).containsExactly(content);
    }

    @Test
    void repeatedRemovalDoesNotChangeTheTombstoneOrDuplicateAudit() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.042-78");
        var created = upload(client, patientId, "laudo.pdf", "application/pdf", minimalPdf(), null);
        var attachmentId = JsonPath.<String>read(created.body(), "$.id");
        assertThat(remove(client, attachmentId, "Justificativa original").statusCode())
                .isEqualTo(204);
        var originalTombstone = get(client, "/api/v1/attachments/" + attachmentId);
        var filesAfterFirstRemoval = storedFileCount();

        var repeated = remove(client, attachmentId, "Tentativa posterior");

        assertThat(repeated.statusCode()).isEqualTo(204);
        assertThat(storedFileCount()).isEqualTo(filesAfterFirstRemoval);
        var preservedTombstone = get(client, "/api/v1/attachments/" + attachmentId);
        assertThat(preservedTombstone.body()).isEqualTo(originalTombstone.body());
        var audit = get(client, "/api/v1/audit-events?action=ATTACHMENT_REMOVED");
        var events = JsonPath.<List<Map<String, Object>>>read(
                audit.body(), "$.content[?(@.targetId == '" + attachmentId + "')]");
        assertThat(events).hasSize(1);
    }

    @Test
    void storageDeletionFailureKeepsRecoverableTombstoneAndRetryFinishesWithoutDuplicateAudit() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.039-72");
        var created = upload(client, patientId, "laudo.pdf", "application/pdf", minimalPdf(), null);
        var attachmentId = JsonPath.<String>read(created.body(), "$.id");
        var filesBeforeRemoval = storedFileCount();
        faultInjectingStorage.failNextDeletion();

        var failed = remove(client, attachmentId, "Anexo indevido");

        assertThat(failed.statusCode()).isEqualTo(500);
        assertThat(failed.body()).doesNotContain("controlled", STORAGE_DIRECTORY.toString());
        assertThat(storedFileCount()).isEqualTo(filesBeforeRemoval);
        var pendingTombstone = get(client, "/api/v1/attachments/" + attachmentId);
        assertThat(JsonPath.<String>read(pendingTombstone.body(), "$.status")).isEqualTo("REMOVED");
        assertThat(getBytes(client, "/api/v1/attachments/" + attachmentId + "/content")
                        .statusCode())
                .isEqualTo(410);

        var recovered = remove(client, attachmentId, "Tentativa de recuperação");

        assertThat(recovered.statusCode()).isEqualTo(204);
        assertThat(storedFileCount()).isEqualTo(filesBeforeRemoval - 1);
        var completedTombstone = get(client, "/api/v1/attachments/" + attachmentId);
        assertThat(completedTombstone.body()).isEqualTo(pendingTombstone.body());
        var audit = get(client, "/api/v1/audit-events?action=ATTACHMENT_REMOVED");
        var events = JsonPath.<List<Map<String, Object>>>read(
                audit.body(), "$.content[?(@.targetId == '" + attachmentId + "')]");
        assertThat(events).hasSize(1);
    }

    @Test
    void auditFailureRollsBackRemovalAndKeepsTheActiveBinary() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.040-06");
        var content = minimalPdf();
        var created = upload(client, patientId, "laudo.pdf", "application/pdf", content, null);
        var attachmentId = JsonPath.<String>read(created.body(), "$.id");
        var filesBeforeRemoval = storedFileCount();
        executeAsMigration(POSTGRESQL, """
                ALTER TABLE audit_event
                ADD CONSTRAINT pp018_force_removal_audit_failure
                CHECK (action <> 'ATTACHMENT_REMOVED')
                NOT VALID
                """);

        HttpResponse<String> failed;
        try {
            failed = remove(client, attachmentId, "Auditoria indisponível");
        } finally {
            executeAsMigration(POSTGRESQL, "ALTER TABLE audit_event DROP CONSTRAINT pp018_force_removal_audit_failure");
        }

        assertThat(failed.statusCode()).isEqualTo(500);
        assertThat(storedFileCount()).isEqualTo(filesBeforeRemoval);
        var listed = get(client, "/api/v1/patients/" + patientId + "/attachments");
        assertThat(JsonPath.<List<String>>read(listed.body(), "$[*].id")).containsExactly(attachmentId);
        var details = get(client, "/api/v1/attachments/" + attachmentId);
        assertThat(JsonPath.<String>read(details.body(), "$.status")).isEqualTo("ACTIVE");
        var downloaded = getBytes(client, "/api/v1/attachments/" + attachmentId + "/content");
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).containsExactly(content);
        var audit = get(client, "/api/v1/audit-events?action=ATTACHMENT_REMOVED");
        var events = JsonPath.<List<Map<String, Object>>>read(
                audit.body(), "$.content[?(@.targetId == '" + attachmentId + "')]");
        assertThat(events).isEmpty();
    }

    @Test
    void databaseFailureAfterBinaryDeletionRemainsRecoverableWithoutChangingTheTombstone() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.041-97");
        var created = upload(client, patientId, "laudo.pdf", "application/pdf", minimalPdf(), null);
        var attachmentId = JsonPath.<String>read(created.body(), "$.id");
        var filesBeforeRemoval = storedFileCount();
        executeAsMigration(POSTGRESQL, """
                ALTER TABLE attachment
                ADD CONSTRAINT pp018_force_cleanup_completion_failure
                CHECK (status <> 'REMOVED' OR binary_cleanup_pending)
                NOT VALID
                """);

        HttpResponse<String> failed;
        try {
            failed = remove(client, attachmentId, "Anexo indevido");
        } finally {
            executeAsMigration(
                    POSTGRESQL, "ALTER TABLE attachment DROP CONSTRAINT pp018_force_cleanup_completion_failure");
        }

        assertThat(failed.statusCode()).isEqualTo(500);
        assertThat(storedFileCount()).isEqualTo(filesBeforeRemoval - 1);
        var pendingTombstone = get(client, "/api/v1/attachments/" + attachmentId);
        assertThat(JsonPath.<String>read(pendingTombstone.body(), "$.status")).isEqualTo("REMOVED");

        var recovered = remove(client, attachmentId, "Tentativa de recuperação");

        assertThat(recovered.statusCode()).isEqualTo(204);
        assertThat(storedFileCount()).isEqualTo(filesBeforeRemoval - 1);
        var completedTombstone = get(client, "/api/v1/attachments/" + attachmentId);
        assertThat(completedTombstone.body()).isEqualTo(pendingTombstone.body());
        var audit = get(client, "/api/v1/audit-events?action=ATTACHMENT_REMOVED");
        var events = JsonPath.<List<Map<String, Object>>>read(
                audit.body(), "$.content[?(@.targetId == '" + attachmentId + "')]");
        assertThat(events).hasSize(1);
    }

    private HttpClient authenticatedClient() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        var login = send(client, "POST", "/api/v1/auth/login", """
                {"username":"doctor","password":"valid-test-password"}
                """, null, null, "application/json");
        assertThat(login.statusCode()).isEqualTo(200);
        return client;
    }

    private String createPatient(HttpClient client, String cpf) throws Exception {
        var csrf = csrf(client);
        var created = send(
                client,
                "POST",
                "/api/v1/patients",
                """
                {
                  "fullName": "Ana Souza",
                  "motherName": "Maria Souza",
                  "birthDate": "1990-05-20",
                  "cpf": "%s",
                  "phone": "(11) 99999-1234"
                }
                """.formatted(cpf),
                csrf.headerName(),
                csrf.token(),
                "application/json");
        assertThat(created.statusCode()).isEqualTo(201);
        return JsonPath.read(created.body(), "$.id");
    }

    private String createConsultation(HttpClient client, String patientId) throws Exception {
        var csrf = csrf(client);
        var created = send(
                client,
                "POST",
                "/api/v1/patients/" + patientId + "/consultations",
                "{}",
                csrf.headerName(),
                csrf.token(),
                "application/json");
        assertThat(created.statusCode()).isEqualTo(201);
        return JsonPath.read(created.body(), "$.id");
    }

    private HttpResponse<String> upload(
            HttpClient client,
            String patientId,
            String filename,
            String mediaType,
            byte[] content,
            String consultationId)
            throws Exception {
        var boundary = "pp-attachment-" + UUID.randomUUID();
        var body = multipartBody(boundary, filename, mediaType, content, consultationId);
        var csrf = csrf(client);
        return send(
                client,
                "POST",
                "/api/v1/patients/" + patientId + "/attachments",
                body,
                csrf.headerName(),
                csrf.token(),
                "multipart/form-data; boundary=" + boundary);
    }

    private HttpResponse<String> uploadWithEncodedFilename(
            HttpClient client, String patientId, String encodedFilename, String mediaType, byte[] content)
            throws Exception {
        var boundary = "pp-attachment-" + UUID.randomUUID();
        var body = new ByteArrayOutputStream();
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"file\"; filename*=UTF-8''" + encodedFilename + "\r\n");
        write(body, "Content-Type: " + mediaType + "\r\n\r\n");
        body.write(content);
        write(body, "\r\n--" + boundary + "--\r\n");
        var csrf = csrf(client);
        return send(
                client,
                "POST",
                "/api/v1/patients/" + patientId + "/attachments",
                body.toByteArray(),
                csrf.headerName(),
                csrf.token(),
                "multipart/form-data; boundary=" + boundary);
    }

    private byte[] multipartBody(
            String boundary, String filename, String mediaType, byte[] content, String consultationId)
            throws IOException {
        var body = new ByteArrayOutputStream();
        if (consultationId != null) {
            write(body, "--" + boundary + "\r\n");
            write(body, "Content-Disposition: form-data; name=\"consultationId\"\r\n\r\n");
            write(body, consultationId + "\r\n");
        }
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
        write(body, "Content-Type: " + mediaType + "\r\n\r\n");
        body.write(content);
        write(body, "\r\n--" + boundary + "--\r\n");
        return body.toByteArray();
    }

    private void write(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return send(client, "GET", path, (byte[]) null, null, null, null);
    }

    private HttpResponse<String> remove(HttpClient client, String attachmentId, String justification) throws Exception {
        var csrf = csrf(client);
        return send(
                client,
                "DELETE",
                "/api/v1/attachments/" + attachmentId,
                """
                {"justification":"%s"}
                """.formatted(justification),
                csrf.headerName(),
                csrf.token(),
                "application/json");
    }

    private HttpResponse<byte[]> getBytes(HttpClient client, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private Csrf csrf(HttpClient client) throws Exception {
        var response = get(client, "/api/v1/auth/csrf");
        return new Csrf(JsonPath.read(response.body(), "$.headerName"), JsonPath.read(response.body(), "$.token"));
    }

    private HttpResponse<String> send(
            HttpClient client,
            String method,
            String path,
            String body,
            String csrfHeader,
            String csrfToken,
            String contentType)
            throws Exception {
        return send(
                client,
                method,
                path,
                body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                csrfHeader,
                csrfToken,
                contentType);
    }

    private HttpResponse<String> send(
            HttpClient client,
            String method,
            String path,
            byte[] body,
            String csrfHeader,
            String csrfToken,
            String contentType)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        if (csrfHeader != null) {
            builder.header(csrfHeader, csrfToken);
        }
        var publisher =
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body);
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static byte[] minimalPdf() {
        return """
                %PDF-1.4
                1 0 obj
                << /Type /Catalog >>
                endobj
                trailer
                << /Root 1 0 R >>
                %%EOF
                """.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] image(String format) throws IOException {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336699);
        var output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static Path createStorageDirectory() {
        try {
            return Files.createTempDirectory("pp-attachments-");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static long storedFileCount() throws IOException {
        try (var files = Files.walk(STORAGE_DIRECTORY)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private record Csrf(String headerName, String token) {}

    @TestConfiguration
    static class TestStorageConfiguration {

        @Bean
        @Primary
        FaultInjectingAttachmentStorage faultInjectingAttachmentStorage(
                FileSystemAttachmentStorage fileSystemAttachmentStorage) {
            return new FaultInjectingAttachmentStorage(fileSystemAttachmentStorage);
        }
    }

    static class FaultInjectingAttachmentStorage implements AttachmentStorage {

        private final AttachmentStorage delegate;
        private final AtomicBoolean failAfterPromotion = new AtomicBoolean();
        private final AtomicBoolean failDeletion = new AtomicBoolean();

        FaultInjectingAttachmentStorage(AttachmentStorage delegate) {
            this.delegate = delegate;
        }

        void failAfterNextPromotion() {
            failAfterPromotion.set(true);
        }

        void failNextDeletion() {
            failDeletion.set(true);
        }

        void reset() {
            failAfterPromotion.set(false);
            failDeletion.set(false);
        }

        @Override
        public StagedAttachment stage(java.io.InputStream content, long maximumBytes) {
            return delegate.stage(content, maximumBytes);
        }

        @Override
        public java.io.InputStream openStaged(StagedAttachment stagedAttachment) {
            return delegate.openStaged(stagedAttachment);
        }

        @Override
        public void promote(StagedAttachment stagedAttachment, String storageKey) {
            delegate.promote(stagedAttachment, storageKey);
            if (failAfterPromotion.getAndSet(false)) {
                throw new IllegalStateException("controlled storage boundary failure");
            }
        }

        @Override
        public java.io.InputStream open(String storageKey) {
            return delegate.open(storageKey);
        }

        @Override
        public void delete(String storageKey) {
            if (failDeletion.getAndSet(false)) {
                throw new IllegalStateException("controlled storage boundary failure");
            }
            delegate.delete(storageKey);
        }

        @Override
        public void discard(StagedAttachment stagedAttachment) {
            delegate.discard(stagedAttachment);
        }
    }
}
