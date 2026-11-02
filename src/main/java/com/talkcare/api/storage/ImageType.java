package com.talkcare.api.storage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Accepted image formats; each knows its file extension and verifies its own magic bytes. */
public enum ImageType {

    PNG("image/png", "png") {
        private static final byte[] SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        @Override
        public boolean matches(byte[] b) {
            return b.length > 8 && Arrays.equals(b, 0, 8, SIGNATURE, 0, 8);
        }
    },
    JPEG("image/jpeg", "jpg") {
        @Override
        public boolean matches(byte[] b) {
            return b.length > 3 && b[0] == (byte) 0xff && b[1] == (byte) 0xd8 && b[2] == (byte) 0xff;
        }
    },
    GIF("image/gif", "gif") {
        @Override
        public boolean matches(byte[] b) {
            if (b.length <= 6) {
                return false;
            }
            String header = ascii(b, 0, 6);
            return header.equals("GIF87a") || header.equals("GIF89a");
        }
    },
    WEBP("image/webp", "webp") {
        @Override
        public boolean matches(byte[] b) {
            return b.length > 12 && ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 4).equals("WEBP");
        }
    };

    private final String mimeType;
    private final String extension;

    ImageType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    /** True when the bytes really are an image of this type. */
    public abstract boolean matches(byte[] bytes);

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }

    /** Accepts the non-standard {@code image/jpg} as JPEG. */
    public static Optional<ImageType> fromMimeType(String mimeType) {
        String normalized = mimeType.toLowerCase(Locale.ROOT).replace("image/jpg", "image/jpeg");
        return Arrays.stream(values()).filter(type -> type.mimeType.equals(normalized)).findFirst();
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
