package com.talkcare.api.storage;

import java.util.Set;

/** What an upload may be: file-name prefix, accepted formats, size cap, and a label for messages. */
public record UploadPolicy(String prefix, Set<ImageType> allowedTypes, long maxBytes, String label) {

    private static final long MB = 1024 * 1024;

    /** Profile and chat photos: PNG or JPEG up to 5 MB. */
    public static UploadPolicy photo(String prefix, String label) {
        return new UploadPolicy(prefix, Set.of(ImageType.PNG, ImageType.JPEG), 5 * MB, label);
    }

    /** ID documents: any supported image format up to 8 MB. */
    public static UploadPolicy idDocument(String prefix) {
        return new UploadPolicy(prefix, Set.of(ImageType.values()), 8 * MB, "ID image");
    }
}
