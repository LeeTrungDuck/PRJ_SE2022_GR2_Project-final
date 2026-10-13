package utills;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * P1 fixes PBKDF2-SHA256/600000, 128-bit salt and 128-bit verifier; fits
 * varchar(50).
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String hash(String password) {
        byte[] bytes = new byte[32];
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        System.arraycopy(salt, 0, bytes, 0, 16);
        System.arraycopy(derive(password, salt), 0, bytes, 16, 16);
        return "P1$" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null || password.length() > 128) {
            return false;
        }
        if (!stored.startsWith("P1$")) {
            // Compatibility with existing seed accounts. New writes always use hash().
            return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(stored.substring(3));
            return bytes.length == 32 && MessageDigest.isEqual(Arrays.copyOfRange(bytes, 16, 32), derive(password, Arrays.copyOf(bytes, 16)));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt) throws IllegalStateException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 600000, 128);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }
}
