package com.bankdash.profile.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * An avatar upload that passed the checks: one of the allowed image types, at most {@link #MAX_BYTES}, and bytes
 * that really start like that type. The image is later served back with its stored type, so the declared type
 * must not be trusted on its own.
 */
public record AvatarImage(String contentType, byte[] data) {

    public static final int MAX_BYTES = 1024 * 1024;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final Map<String, Signature> TYPES = Map.of(
            "image/png", data -> startsWith(data, PNG, 0),
            "image/jpeg", data -> startsWith(data, JPEG, 0),
            // RIFF <4-byte size> WEBP
            "image/webp", data -> startsWith(data, "RIFF".getBytes(StandardCharsets.US_ASCII), 0) && startsWith(data, "WEBP".getBytes(StandardCharsets.US_ASCII), 8));

    /**
     * @throws UnsupportedAvatarTypeException if the type is not PNG, JPEG or WebP
     * @throws AvatarTooLargeException        if it has more than {@link #MAX_BYTES}
     * @throws InvalidAvatarException         if it is empty or its bytes are not of the declared type
     */
    public static AvatarImage of(String contentType, byte[] data) {
        Signature signature = TYPES.get(contentType);
        if (signature == null) {
            throw new UnsupportedAvatarTypeException(contentType);
        }
        if (data.length > MAX_BYTES) {
            throw new AvatarTooLargeException();
        }
        if (data.length == 0 || !signature.matches(data)) {
            throw new InvalidAvatarException("The file is not a valid " + contentType.substring("image/".length()).toUpperCase() + " image");
        }
        return new AvatarImage(contentType, data);
    }

    private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
        return data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    @FunctionalInterface
    private interface Signature {
        boolean matches(byte[] data);
    }

    public static class UnsupportedAvatarTypeException extends RuntimeException {

        public UnsupportedAvatarTypeException(String contentType) {
            super("Unsupported image type " + contentType + "; use PNG, JPEG or WebP");
        }
    }

    public static class AvatarTooLargeException extends RuntimeException {

        public AvatarTooLargeException() {
            super("The image must be at most 1 MB");
        }
    }

    public static class InvalidAvatarException extends RuntimeException {

        public InvalidAvatarException(String message) {
            super(message);
        }
    }
}
