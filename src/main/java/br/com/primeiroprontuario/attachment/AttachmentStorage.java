package br.com.primeiroprontuario.attachment;

import java.io.InputStream;

public interface AttachmentStorage {

    StagedAttachment stage(InputStream content, long maximumBytes);

    InputStream openStaged(StagedAttachment stagedAttachment);

    void promote(StagedAttachment stagedAttachment, String storageKey);

    InputStream open(String storageKey);

    void delete(String storageKey);

    void discard(StagedAttachment stagedAttachment);

    record StagedAttachment(String token, long size, String sha256) {}
}
