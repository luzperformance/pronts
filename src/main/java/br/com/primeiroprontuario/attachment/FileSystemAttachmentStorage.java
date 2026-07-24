package br.com.primeiroprontuario.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileSystemAttachmentStorage implements AttachmentStorage {

    private static final Set<java.nio.file.attribute.PosixFilePermission> DIRECTORY_PERMISSIONS =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<java.nio.file.attribute.PosixFilePermission> FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OTHER_PERMISSIONS = Set.of(
            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE);

    private final Path stagingDirectory;
    private final Path contentDirectory;

    public FileSystemAttachmentStorage(@Value("${app.attachments.directory}") String directory) {
        var root = Path.of(directory).toAbsolutePath().normalize();
        stagingDirectory = root.resolve("staging");
        contentDirectory = root.resolve("content");
        try {
            createStorageRoot(root);
            createPrivateDirectory(stagingDirectory);
            createPrivateDirectory(contentDirectory);
        } catch (IOException exception) {
            throw new AttachmentStorageException(exception);
        }
    }

    @Override
    public StagedAttachment stage(InputStream content, long maximumBytes) {
        var token = UUID.randomUUID().toString();
        var target = stagingPath(token);
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (content;
                    OutputStream output =
                            Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                makePrivateFile(target);
                var buffer = new byte[8192];
                int read;
                while ((read = content.read(buffer)) != -1) {
                    size += read;
                    if (size > maximumBytes) {
                        throw new AttachmentTooLargeException();
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            } catch (RuntimeException | IOException exception) {
                Files.deleteIfExists(target);
                throw exception;
            }
            return new StagedAttachment(token, size, HexFormat.of().formatHex(digest.digest()));
        } catch (AttachmentTooLargeException exception) {
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new AttachmentStorageException(exception);
        }
    }

    @Override
    public InputStream openStaged(StagedAttachment stagedAttachment) {
        return openPath(stagingPath(stagedAttachment.token()));
    }

    @Override
    public void promote(StagedAttachment stagedAttachment, String storageKey) {
        var source = stagingPath(stagedAttachment.token());
        var target = contentPath(storageKey);
        try {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(source, target);
            }
            makePrivateFile(target);
        } catch (IOException exception) {
            throw new AttachmentStorageException(exception);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        return openPath(contentPath(storageKey));
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(contentPath(storageKey));
        } catch (IOException exception) {
            throw new AttachmentStorageException(exception);
        }
    }

    @Override
    public void discard(StagedAttachment stagedAttachment) {
        try {
            Files.deleteIfExists(stagingPath(stagedAttachment.token()));
        } catch (IOException exception) {
            throw new AttachmentStorageException(exception);
        }
    }

    private InputStream openPath(Path path) {
        try {
            return Files.newInputStream(path, StandardOpenOption.READ);
        } catch (IOException exception) {
            throw new AttachmentStorageException(exception);
        }
    }

    private Path stagingPath(String token) {
        return resolveUuid(stagingDirectory, token);
    }

    private Path contentPath(String storageKey) {
        return resolveUuid(contentDirectory, storageKey);
    }

    private Path resolveUuid(Path directory, String value) {
        var canonical = UUID.fromString(value).toString();
        return directory.resolve(canonical);
    }

    private void createPrivateDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        if (Files.getFileAttributeView(directory, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(directory, DIRECTORY_PERMISSIONS);
        }
    }

    private void createStorageRoot(Path root) throws IOException {
        Files.createDirectories(root);
        if (Files.getFileAttributeView(root, java.nio.file.attribute.PosixFileAttributeView.class) == null) {
            return;
        }
        try {
            Files.setPosixFilePermissions(root, DIRECTORY_PERMISSIONS);
        } catch (IOException exception) {
            var permissions = Files.getPosixFilePermissions(root);
            if (!Files.isWritable(root) || permissions.stream().anyMatch(OTHER_PERMISSIONS::contains)) {
                throw exception;
            }
        }
    }

    private void makePrivateFile(Path file) throws IOException {
        if (Files.getFileAttributeView(file, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(file, FILE_PERMISSIONS);
        }
    }
}
