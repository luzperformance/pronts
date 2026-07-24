package br.com.primeiroprontuario.attachment;

import br.com.primeiroprontuario.auth.DoctorPrincipal;
import br.com.primeiroprontuario.web.ApiPagination;
import br.com.primeiroprontuario.web.CorrelationIdFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class AttachmentController {

    private final AttachmentService attachments;

    AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @PostMapping("/api/v1/patients/{patientId}/attachments")
    ResponseEntity<AttachmentResponse> upload(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID consultationId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal DoctorPrincipal doctor,
            HttpServletRequest request) {
        var attachment = attachments.upload(patientId, consultationId, file, doctor.id(), (String)
                request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        return ResponseEntity.created(URI.create("/api/v1/attachments/" + attachment.getId()))
                .body(AttachmentResponse.from(attachment));
    }

    @GetMapping("/api/v1/patients/{patientId}/attachments")
    List<AttachmentResponse> list(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ApiPagination.validate(page, size);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        return attachments.list(patientId, pageable).getContent().stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    @GetMapping("/api/v1/attachments/{attachmentId}")
    AttachmentResponse find(@PathVariable UUID attachmentId) {
        return AttachmentResponse.from(attachments.find(attachmentId));
    }

    @GetMapping("/api/v1/attachments/{attachmentId}/content")
    ResponseEntity<InputStreamResource> download(
            @PathVariable UUID attachmentId,
            @AuthenticationPrincipal DoctorPrincipal doctor,
            HttpServletRequest request) {
        var download = attachments.download(
                attachmentId, doctor.id(), (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        var disposition = ContentDisposition.attachment()
                .filename(safeDownloadFilename(download.originalFilename()), StandardCharsets.UTF_8)
                .build();
        var mediaType = MediaType.parseMediaType(download.mediaType());
        if ("text/markdown".equals(download.mediaType())) {
            mediaType = new MediaType("text", "markdown", StandardCharsets.UTF_8);
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(download.content()));
    }

    @DeleteMapping("/api/v1/attachments/{attachmentId}")
    ResponseEntity<Void> remove(
            @PathVariable UUID attachmentId,
            @Valid @RequestBody RemoveAttachmentRequest removal,
            @AuthenticationPrincipal DoctorPrincipal doctor,
            HttpServletRequest request) {
        attachments.remove(attachmentId, removal.justification(), doctor.id(), (String)
                request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        return ResponseEntity.noContent().build();
    }

    private String safeDownloadFilename(String originalFilename) {
        var sanitized = new StringBuilder(originalFilename.length());
        originalFilename.codePoints().forEach(character -> {
            if (Character.isISOControl(character) || character == '/' || character == '\\') {
                sanitized.append('_');
            } else {
                sanitized.appendCodePoint(character);
            }
        });
        return sanitized.toString();
    }

    private record AttachmentResponse(
            UUID id,
            UUID patientId,
            UUID consultationId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String originalFilename,
            String mediaType,
            long size,
            String sha256,
            AttachmentStatus status,
            @JsonInclude(JsonInclude.Include.NON_NULL) UUID uploadedBy,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant createdAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) UUID removedBy,
            @JsonInclude(JsonInclude.Include.NON_NULL) String removalJustification,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant removedAt) {

        private static AttachmentResponse from(Attachment attachment) {
            var active = attachment.getStatus() == AttachmentStatus.ACTIVE;
            return new AttachmentResponse(
                    attachment.getId(),
                    attachment.getPatientId(),
                    attachment.getConsultationId(),
                    active ? attachment.getOriginalFilename() : null,
                    attachment.getMediaType(),
                    attachment.getSize(),
                    attachment.getSha256(),
                    attachment.getStatus(),
                    active ? attachment.getUploadedBy() : null,
                    active ? attachment.getCreatedAt() : null,
                    attachment.getRemovedBy(),
                    attachment.getRemovalJustification(),
                    attachment.getRemovedAt());
        }

        @Override
        public String toString() {
            return "AttachmentResponse[REDACTED]";
        }
    }

    private record RemoveAttachmentRequest(
            @NotBlank(message = "é obrigatória") String justification) {

        @Override
        public String toString() {
            return "RemoveAttachmentRequest[REDACTED]";
        }
    }
}
