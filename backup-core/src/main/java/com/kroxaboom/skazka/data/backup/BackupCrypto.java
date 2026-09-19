package com.kroxaboom.skazka.data.backup;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * RU: Формат зашифрованной резервной копии Skazka.
 * Контейнер аутентифицирует заголовок через AES-GCM AAD и дополнительно хранит SHA-256 исходных данных.
 *
 * EN: Skazka encrypted-backup container.
 * The header is authenticated with AES-GCM AAD and the container also stores the plaintext SHA-256.
 */
public final class BackupCrypto {
    public static final int CONTAINER_VERSION = 1;
    public static final int KDF_ITERATIONS = 250_000;
    public static final int HEADER_BYTES = 84;

    private static final byte[] MAGIC = "SKHBKP1\n".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder KEY_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder KEY_DECODER = Base64.getUrlDecoder();

    private BackupCrypto() {}

    public static String generateRecoveryKey() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return KEY_ENCODER.encodeToString(raw);
    }

    public static String normalizeRecoveryKey(String value) throws IOException {
        String clean = value == null ? "" : value.replaceAll("\\s+", "").trim();
        try {
            byte[] raw = KEY_DECODER.decode(clean);
            if (raw.length != 32) {
                throw new IOException("Recovery key must contain exactly 256 bits");
            }
            return KEY_ENCODER.encodeToString(raw);
        } catch (IllegalArgumentException error) {
            throw new IOException("Recovery key is not valid URL-safe Base64", error);
        }
    }

    public static String fingerprint(String recoveryKey) throws Exception {
        byte[] raw = KEY_DECODER.decode(normalizeRecoveryKey(recoveryKey));
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw);
        return "rk1:" + KEY_ENCODER.encodeToString(Arrays.copyOf(hash, 12));
    }

    public static Path encrypt(Path directory, byte[] plaintext, String recoveryKey) throws Exception {
        if (plaintext == null || plaintext.length == 0) {
            throw new IOException("Backup payload is empty");
        }

        Files.createDirectories(directory);
        Path target = Files.createTempFile(directory, "skazka-backup-", ".skb");

        byte[] salt = randomBytes(16);
        byte[] iv = randomBytes(12);
        byte[] header = header(
                salt,
                iv,
                plaintext.length,
                MessageDigest.getInstance("SHA-256").digest(plaintext)
        );

        Cipher cipher = encryptCipher(recoveryKey, salt, iv, header);
        boolean complete = false;
        try (FileOutputStream output = new FileOutputStream(target.toFile())) {
            output.write(header);

            byte[] first = cipher.update(plaintext);
            if (first != null && first.length > 0) {
                output.write(first);
            }

            byte[] last = cipher.doFinal();
            if (last != null && last.length > 0) {
                output.write(last);
            }

            output.getFD().sync();
            complete = true;
            return target;
        } finally {
            if (!complete) {
                Files.deleteIfExists(target);
            }
        }
    }

    public static Path encryptFile(Path directory, Path plaintext, String recoveryKey) throws Exception {
        if (plaintext == null || !Files.isRegularFile(plaintext) || Files.size(plaintext) == 0) {
            throw new IOException("Backup file is empty or missing");
        }

        Files.createDirectories(directory);
        Path target = Files.createTempFile(directory, "skazka-full-encrypted-", ".skb");

        byte[] salt = randomBytes(16);
        byte[] iv = randomBytes(12);
        long plainLength = Files.size(plaintext);
        byte[] header = header(salt, iv, plainLength, sha256Bytes(plaintext));
        Cipher cipher = encryptCipher(recoveryKey, salt, iv, header);

        boolean complete = false;
        try (InputStream input = Files.newInputStream(plaintext);
             FileOutputStream output = new FileOutputStream(target.toFile())) {
            output.write(header);
            byte[] buffer = new byte[64 * 1024];
            int read;

            while ((read = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Backup encryption was interrupted");
                }

                byte[] encrypted = cipher.update(buffer, 0, read);
                if (encrypted != null && encrypted.length > 0) {
                    output.write(encrypted);
                }
            }

            byte[] last = cipher.doFinal();
            if (last != null && last.length > 0) {
                output.write(last);
            }

            output.getFD().sync();
            complete = true;
            return target;
        } finally {
            if (!complete) {
                Files.deleteIfExists(target);
            }
        }
    }

    public static byte[] decrypt(Path encryptedFile, String recoveryKey, int maxPlainBytes) throws Exception {
        if (encryptedFile == null || !Files.isRegularFile(encryptedFile)) {
            throw new IOException("Backup file was not found");
        }
        if (maxPlainBytes < 1) {
            throw new IllegalArgumentException("Plaintext size limit must be positive");
        }

        try (InputStream input = Files.newInputStream(encryptedFile)) {
            byte[] header = readExact(input, HEADER_BYTES);
            Header parsed = parseHeader(header);

            if (parsed.plainLength < 1 || parsed.plainLength > maxPlainBytes) {
                throw new IOException("Backup payload exceeds the configured size limit");
            }

            Cipher cipher = decryptCipher(recoveryKey, header, parsed);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream output =
                    new ByteArrayOutputStream((int) Math.min(parsed.plainLength, 8L * 1024 * 1024));

            long total = decryptStream(
                    input,
                    cipher,
                    output,
                    digest,
                    parsed.plainLength,
                    maxPlainBytes
            );

            verifyPlaintext(total, parsed, digest.digest());
            return output.toByteArray();
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException(
                    "Backup could not be decrypted; check the recovery key and file integrity",
                    error
            );
        }
    }

    public static Path decryptToFile(
            Path encryptedFile,
            String recoveryKey,
            Path directory,
            long maxPlainBytes
    ) throws Exception {
        if (encryptedFile == null || !Files.isRegularFile(encryptedFile)) {
            throw new IOException("Backup file was not found");
        }
        if (maxPlainBytes < 1) {
            throw new IllegalArgumentException("Plaintext size limit must be positive");
        }

        Files.createDirectories(directory);
        Path target = Files.createTempFile(directory, "skazka-full-plain-", ".bin");
        boolean complete = false;

        try (InputStream input = Files.newInputStream(encryptedFile)) {
            byte[] header = readExact(input, HEADER_BYTES);
            Header parsed = parseHeader(header);

            if (parsed.plainLength < 1 || parsed.plainLength > maxPlainBytes) {
                throw new IOException("Backup payload exceeds the configured size limit");
            }

            Cipher cipher = decryptCipher(recoveryKey, header, parsed);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total;

            try (FileOutputStream output = new FileOutputStream(target.toFile())) {
                total = decryptStream(
                        input,
                        cipher,
                        output,
                        digest,
                        parsed.plainLength,
                        maxPlainBytes
                );
                output.getFD().sync();
            }

            verifyPlaintext(total, parsed, digest.digest());
            if (Files.size(target) != parsed.plainLength) {
                throw new IOException("Decrypted backup length does not match its header");
            }

            complete = true;
            return target;
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException(
                    "Backup could not be decrypted; check the recovery key and file integrity",
                    error
            );
        } finally {
            if (!complete) {
                Files.deleteIfExists(target);
            }
        }
    }

    public static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(sha256Bytes(file));
    }

    public static boolean looksEncrypted(byte[] prefix) {
        if (prefix == null || prefix.length < MAGIC.length) {
            return false;
        }

        for (int i = 0; i < MAGIC.length; i++) {
            if (prefix[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static Cipher encryptCipher(
            String recoveryKey,
            byte[] salt,
            byte[] iv,
            byte[] header
    ) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                derive(recoveryKey, salt, KDF_ITERATIONS),
                new GCMParameterSpec(128, iv)
        );
        cipher.updateAAD(header);
        return cipher;
    }

    private static Cipher decryptCipher(
            String recoveryKey,
            byte[] header,
            Header parsed
    ) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                derive(recoveryKey, parsed.salt, parsed.iterations),
                new GCMParameterSpec(128, parsed.iv)
        );
        cipher.updateAAD(header);
        return cipher;
    }

    private static SecretKey derive(String recoveryKey, byte[] salt, int iterations) throws Exception {
        String normalized = normalizeRecoveryKey(recoveryKey);
        PBEKeySpec spec = new PBEKeySpec(normalized.toCharArray(), salt, iterations, 256);
        try {
            byte[] encoded = SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            return new SecretKeySpec(encoded, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] header(
            byte[] salt,
            byte[] iv,
            long plainLength,
            byte[] plainHash
    ) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(HEADER_BYTES);
        try (DataOutputStream output = new DataOutputStream(raw)) {
            output.write(MAGIC);
            output.writeInt(CONTAINER_VERSION);
            output.writeInt(KDF_ITERATIONS);
            output.write(salt);
            output.write(iv);
            output.writeLong(plainLength);
            output.write(plainHash);
        }

        byte[] result = raw.toByteArray();
        if (result.length != HEADER_BYTES) {
            throw new IOException("Unexpected backup header length");
        }
        return result;
    }

    private static Header parseHeader(byte[] header) throws Exception {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(header))) {
            byte[] magic = new byte[MAGIC.length];
            input.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("Unsupported encrypted backup format");
            }

            int version = input.readInt();
            int iterations = input.readInt();
            if (version != CONTAINER_VERSION || iterations < 100_000 || iterations > 1_000_000) {
                throw new IOException("Unsupported backup encryption version");
            }

            byte[] salt = new byte[16];
            byte[] iv = new byte[12];
            byte[] hash = new byte[32];

            input.readFully(salt);
            input.readFully(iv);
            long plainLength = input.readLong();
            input.readFully(hash);

            return new Header(iterations, salt, iv, plainLength, hash);
        }
    }

    private static long decryptStream(
            InputStream encrypted,
            Cipher cipher,
            OutputStream output,
            MessageDigest digest,
            long expectedLength,
            long maxPlainBytes
    ) throws Exception {
        long total = 0;

        try (CipherInputStream decrypted = new CipherInputStream(encrypted, cipher)) {
            byte[] buffer = new byte[64 * 1024];
            int read;

            while ((read = decrypted.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Backup decryption was interrupted");
                }

                total += read;
                if (total > maxPlainBytes || total > expectedLength) {
                    throw new IOException("Decrypted backup length is invalid");
                }

                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        } catch (IOException error) {
            throw new IOException(
                    "Backup could not be decrypted; check the recovery key and file integrity",
                    error
            );
        }

        return total;
    }

    private static void verifyPlaintext(long total, Header parsed, byte[] digest) throws IOException {
        if (total != parsed.plainLength || !MessageDigest.isEqual(digest, parsed.plainHash)) {
            throw new IOException("Backup checksum does not match");
        }
    }

    private static byte[] sha256Bytes(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    private static byte[] readExact(InputStream input, int length) throws IOException {
        byte[] output = new byte[length];
        int offset = 0;

        while (offset < length) {
            int read = input.read(output, offset, length - offset);
            if (read < 0) {
                throw new EOFException("Backup file is truncated");
            }
            if (read == 0) {
                continue;
            }
            offset += read;
        }

        return output;
    }

    private static byte[] randomBytes(int length) {
        byte[] output = new byte[length];
        RANDOM.nextBytes(output);
        return output;
    }

    private static final class Header {
        final int iterations;
        final byte[] salt;
        final byte[] iv;
        final long plainLength;
        final byte[] plainHash;

        Header(int iterations, byte[] salt, byte[] iv, long plainLength, byte[] plainHash) {
            this.iterations = iterations;
            this.salt = salt;
            this.iv = iv;
            this.plainLength = plainLength;
            this.plainHash = plainHash;
        }
    }
}
