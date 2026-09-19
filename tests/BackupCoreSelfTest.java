import com.kroxaboom.skazka.data.backup.BackupCrypto;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class BackupCoreSelfTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("skazka-backup-test-");
        try {
            String key = BackupCrypto.generateRecoveryKey();
            check(key.equals(BackupCrypto.normalizeRecoveryKey(key)), "recovery-key normalization");
            check(BackupCrypto.fingerprint(key).startsWith("rk1:"), "recovery-key fingerprint");

            byte[] payload = "Skazka backup compatibility test".getBytes(StandardCharsets.UTF_8);
            Path encrypted = BackupCrypto.encrypt(directory, payload, key);

            byte[] prefix = Files.readAllBytes(encrypted);
            check(prefix.length > BackupCrypto.HEADER_BYTES, "encrypted container length");
            check(BackupCrypto.looksEncrypted(prefix), "encrypted magic");

            byte[] decrypted = BackupCrypto.decrypt(encrypted, key, 1024 * 1024);
            check(Arrays.equals(payload, decrypted), "byte-array round trip");

            boolean wrongKeyRejected = false;
            try {
                BackupCrypto.decrypt(encrypted, BackupCrypto.generateRecoveryKey(), 1024 * 1024);
            } catch (Exception expected) {
                wrongKeyRejected = true;
            }
            check(wrongKeyRejected, "wrong key rejected");

            Path tampered = directory.resolve("tampered.skb");
            byte[] damaged = Files.readAllBytes(encrypted);
            damaged[damaged.length - 1] ^= 0x01;
            Files.write(tampered, damaged);

            boolean tamperRejected = false;
            try {
                BackupCrypto.decrypt(tampered, key, 1024 * 1024);
            } catch (Exception expected) {
                tamperRejected = true;
            }
            check(tamperRejected, "tampered ciphertext rejected");

            boolean sizeLimitRejected = false;
            try {
                BackupCrypto.decrypt(encrypted, key, payload.length - 1);
            } catch (Exception expected) {
                sizeLimitRejected = true;
            }
            check(sizeLimitRejected, "plaintext size limit");

            Path plainFile = directory.resolve("plain.bin");
            Files.write(plainFile, payload);
            Path encryptedFile = BackupCrypto.encryptFile(directory, plainFile, key);
            Path restoredFile = BackupCrypto.decryptToFile(
                    encryptedFile,
                    key,
                    directory,
                    1024 * 1024
            );
            check(Arrays.equals(payload, Files.readAllBytes(restoredFile)), "file round trip");
            check(
                    BackupCrypto.sha256(plainFile).equals(BackupCrypto.sha256(restoredFile)),
                    "file SHA-256"
            );

            System.out.println("PASS: Skazka Backup Core encryption/container compatibility");
        } finally {
            deleteTree(directory);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }

        try (var paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
