package br.com.primeiroprontuario.attachment;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
class AttachmentContentDetector {

    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_END = "%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_HEADER = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    DetectedContent detect(
            AttachmentStorage storage,
            AttachmentStorage.StagedAttachment stagedAttachment,
            String originalFilename,
            String informedMediaType) {
        byte[] content;
        try (var input = storage.openStaged(stagedAttachment)) {
            content = input.readAllBytes();
        } catch (IOException exception) {
            throw new AttachmentStorageException(exception);
        }

        var detected = detectContent(content);
        var extension = extensionOf(originalFilename);
        var normalizedMediaType = normalizeMediaType(informedMediaType);
        if (!detected.extensions().contains(extension) || !detected.mediaType().equals(normalizedMediaType)) {
            throw new UnsupportedAttachmentTypeException();
        }
        return new DetectedContent(detected.mediaType());
    }

    private AllowedType detectContent(byte[] content) {
        if (isPdf(content)) {
            return AllowedType.PDF;
        }
        if (startsWith(content, PNG_HEADER) && isReadableImage(content)) {
            return AllowedType.PNG;
        }
        if (content.length >= 4
                && content[0] == (byte) 0xFF
                && content[1] == (byte) 0xD8
                && content[2] == (byte) 0xFF
                && content[content.length - 2] == (byte) 0xFF
                && content[content.length - 1] == (byte) 0xD9
                && isReadableImage(content)) {
            return AllowedType.JPEG;
        }
        if (isUtf8Markdown(content)) {
            return AllowedType.MARKDOWN;
        }
        throw new UnsupportedAttachmentTypeException();
    }

    private boolean isPdf(byte[] content) {
        if (!startsWith(content, PDF_HEADER)) {
            return false;
        }
        var tailStart = Math.max(0, content.length - 1024);
        for (var index = content.length - PDF_END.length; index >= tailStart; index--) {
            if (startsWithAt(content, PDF_END, index)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReadableImage(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            return image != null && image.getWidth() > 0 && image.getHeight() > 0;
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean isUtf8Markdown(byte[] content) {
        if (content.length == 0) {
            return false;
        }
        try {
            var text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            return !text.isBlank() && text.chars().noneMatch(character -> character == 0);
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private boolean startsWith(byte[] content, byte[] expected) {
        return startsWithAt(content, expected, 0);
    }

    private boolean startsWithAt(byte[] content, byte[] expected, int offset) {
        return offset >= 0
                && content.length >= offset + expected.length
                && Arrays.equals(content, offset, offset + expected.length, expected, 0, expected.length);
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            throw new UnsupportedAttachmentTypeException();
        }
        var separator = originalFilename.lastIndexOf('.');
        if (separator < 0 || separator == originalFilename.length() - 1) {
            throw new UnsupportedAttachmentTypeException();
        }
        return originalFilename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeMediaType(String informedMediaType) {
        if (informedMediaType == null) {
            throw new UnsupportedAttachmentTypeException();
        }
        return informedMediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    record DetectedContent(String mediaType) {}

    private enum AllowedType {
        PDF("application/pdf", Set.of("pdf")),
        JPEG("image/jpeg", Set.of("jpg", "jpeg")),
        PNG("image/png", Set.of("png")),
        MARKDOWN("text/markdown", Set.of("md", "markdown"));

        private final String mediaType;
        private final Set<String> extensions;

        AllowedType(String mediaType, Set<String> extensions) {
            this.mediaType = mediaType;
            this.extensions = extensions;
        }

        String mediaType() {
            return mediaType;
        }

        Set<String> extensions() {
            return extensions;
        }
    }
}
