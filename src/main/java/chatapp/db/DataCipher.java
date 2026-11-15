package chatapp.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encryption AT REST: message text and uploaded files are stored encrypted with AES-256-GCM, so
 * phpMyAdmin, a copy of the database or a look into server_uploads/ shows only unreadable data.
 *
 * - AES-GCM both encrypts and detects tampering (a changed byte makes decryption fail).
 * - Every item gets its own random 12-byte IV (nonce), so equal messages look different.
 * - Message text is bound to its conversation id (GCM "additional data"), so a stored message
 *   cannot be copied into another chat unnoticed.
 * - The 256-bit key lives in the file "chatapp.key" next to the server (created on first start).
 *   BACK THIS FILE UP: without it the encrypted messages and files can never be read again.
 * - Old plain-text rows and files from before this feature are still read normally.
 *
 * The server can read the messages (it has the key). This is not end-to-end encryption.
 */
public final class DataCipher {
    public static final String UNREADABLE = "[message could not be decrypted]";

    private static final String TEXT_PREFIX = "enc1:";
    private static final byte[] FILE_MAGIC = {'C', 'H', 'E', '1'};
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static DataCipher instance;

    private final SecretKeySpec key;

    private DataCipher(byte[] keyBytes) {
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** The shared instance; loads (or creates) the key file on first use. */
    public static synchronized DataCipher get() {
        if (instance == null) {
            try {
                instance = load(Paths.get(System.getProperty("chat.key.file", "chatapp.key")));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read or create the encryption key file: " + e.getMessage(), e);
            }
        }
        return instance;
    }

    /** Where the key lives (for the server log). */
    public static String keyFileName() {
        return Paths.get(System.getProperty("chat.key.file", "chatapp.key")).toAbsolutePath().toString();
    }

    static DataCipher load(Path file) throws IOException {
        if (Files.isRegularFile(file)) {
            byte[] raw = Base64.getDecoder().decode(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim());
            if (raw.length != 32) throw new IOException(file + " is not a valid 256-bit key file");
            return new DataCipher(raw);
        }
        byte[] fresh = new byte[32];
        RANDOM.nextBytes(fresh);
        Files.write(file, Base64.getEncoder().encode(fresh));
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows: no POSIX permissions, the file keeps the folder's normal access rules
        }
        return new DataCipher(fresh);
    }

    /** For tests: a cipher with a given key. */
    static DataCipher withKey(byte[] keyBytes) {
        return new DataCipher(keyBytes);
    }

    // ------------------------------------------------------------------ text

    /** @return "enc1:" + Base64(iv + ciphertext + tag), or null for null. */
    public String encryptText(String plain, long conversationId) {
        if (plain == null) return null;
        try {
            byte[] sealed = seal(plain.getBytes(StandardCharsets.UTF_8), Long.toString(conversationId).getBytes(StandardCharsets.UTF_8));
            return TEXT_PREFIX + Base64.getEncoder().encodeToString(sealed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** Decrypts what encryptText produced; text without the prefix (old messages) is returned unchanged. */
    public String decryptText(String stored, long conversationId) {
        if (stored == null || !stored.startsWith(TEXT_PREFIX)) return stored;
        try {
            byte[] sealed = Base64.getDecoder().decode(stored.substring(TEXT_PREFIX.length()));
            byte[] plain = open(sealed, Long.toString(conversationId).getBytes(StandardCharsets.UTF_8));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return UNREADABLE; // wrong key file, or the row was changed
        }
    }

    // ------------------------------------------------------------------ files

    /** @return "CHE1" + iv + ciphertext + tag */
    public byte[] encryptBytes(byte[] data) {
        try {
            byte[] sealed = seal(data, null);
            byte[] out = new byte[FILE_MAGIC.length + sealed.length];
            System.arraycopy(FILE_MAGIC, 0, out, 0, FILE_MAGIC.length);
            System.arraycopy(sealed, 0, out, FILE_MAGIC.length, sealed.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** Decrypts what encryptBytes produced; files stored before this feature are returned unchanged. */
    public byte[] decryptBytes(byte[] stored) throws IOException {
        if (stored.length < FILE_MAGIC.length || !Arrays.equals(Arrays.copyOf(stored, FILE_MAGIC.length), FILE_MAGIC)) {
            return stored;
        }
        try {
            return open(Arrays.copyOfRange(stored, FILE_MAGIC.length, stored.length), null);
        } catch (GeneralSecurityException e) {
            throw new IOException("The stored file could not be decrypted (wrong key file?)", e);
        }
    }

    // ------------------------------------------------------------------ AES-GCM

    private byte[] seal(byte[] plain, byte[] aad) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        if (aad != null) c.updateAAD(aad);
        byte[] ct = c.doFinal(plain);
        byte[] out = new byte[IV_BYTES + ct.length];
        System.arraycopy(iv, 0, out, 0, IV_BYTES);
        System.arraycopy(ct, 0, out, IV_BYTES, ct.length);
        return out;
    }

    private byte[] open(byte[] sealed, byte[] aad) throws GeneralSecurityException {
        if (sealed.length < IV_BYTES + TAG_BITS / 8) throw new GeneralSecurityException("data too short");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Arrays.copyOf(sealed, IV_BYTES)));
        if (aad != null) c.updateAAD(aad);
        return c.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
    }
}
