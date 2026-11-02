package com.talkcare.api.storage;

/**
 * Stores uploaded images. {@link LocalFileStorage} writes to {@code UPLOAD_DIR}; on hosts without
 * a persistent disk, provide an object-storage implementation (S3, R2, ...) of this interface.
 */
public interface FileStorage {

    /** Decodes a {@code data:image/...;base64,} URL, validates type, content and size, and stores it. */
    StoredFile saveDataUrl(String dataUrl, UploadPolicy policy);

    /** Best-effort delete of a file previously returned by {@link #saveDataUrl}. */
    void remove(String url);

    record StoredFile(String url, String mimeType, long byteSize) {
    }
}
