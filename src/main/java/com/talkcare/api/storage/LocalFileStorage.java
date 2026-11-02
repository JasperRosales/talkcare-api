package com.talkcare.api.storage;

import com.talkcare.api.config.TalkCareProperties;
import com.talkcare.api.error.ValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Writes uploads to the local filesystem; they are served from {@code /uploads}. */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private static final Pattern DATA_URL_HEADER = Pattern.compile("^data:([a-z]+/[a-z0-9.+-]+);base64$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STORED_NAME = Pattern.compile("^[\\w-]+\\.(png|jpg|gif|webp)$");

    private final Path directory;
    private final String publicBaseUrl;

    public LocalFileStorage(TalkCareProperties properties) {
        this.directory = properties.uploadDir();
        this.publicBaseUrl = properties.publicBaseUrl();
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create UPLOAD_DIR " + directory, ex);
        }
    }

    @Override
    public StoredFile saveDataUrl(String dataUrl, UploadPolicy policy) {
        String value = dataUrl == null ? "" : dataUrl.trim();
        int comma = value.indexOf(',');
        var header = comma > 0 ? DATA_URL_HEADER.matcher(value.substring(0, comma)) : null;
        Optional<ImageType> type = header != null && header.matches() && comma < value.length() - 1
                ? ImageType.fromMimeType(header.group(1)).filter(policy.allowedTypes()::contains)
                : Optional.empty();
        if (type.isEmpty()) {
            throw new ValidationException("Please upload the " + policy.label() + " as " + describe(policy) + ".");
        }
        ImageType imageType = type.get();
        byte[] bytes = Base64.getMimeDecoder().decode(value.substring(comma + 1));
        String format = imageType.extension().toUpperCase(Locale.ROOT);
        if (bytes.length == 0 || !imageType.matches(bytes)) {
            throw new ValidationException("The " + policy.label() + " file is not a valid " + format + " image.");
        }
        if (bytes.length > policy.maxBytes()) {
            throw new ValidationException("The " + policy.label() + " must be smaller than "
                    + Math.round(policy.maxBytes() / 1024.0 / 1024.0) + " MB.");
        }
        String filename = policy.prefix() + "_" + UUID.randomUUID() + "." + imageType.extension();
        try {
            Files.write(directory.resolve(filename), bytes);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store upload " + filename, ex);
        }
        return new StoredFile(publicBaseUrl + "/uploads/" + filename, imageType.mimeType(), bytes.length);
    }

    @Override
    public void remove(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String filename = url.substring(url.lastIndexOf('/') + 1);
        if (!STORED_NAME.matcher(filename).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve(filename));
        } catch (IOException ex) {
            log.error("Could not remove upload {}: {}", filename, ex.getMessage());
        }
    }

    /** "PNG, JPG" in a stable order. */
    private static String describe(UploadPolicy policy) {
        return policy.allowedTypes().stream()
                .sorted(Comparator.naturalOrder())
                .map(t -> t.extension().toUpperCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }
}
