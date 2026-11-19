package dev.oneforallsurvival.phone;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * Le code à quatre chiffres d'un téléphone. Il n'est jamais gardé : seule une empreinte SHA-256 salée l'est, et la
 * comparaison se fait à temps constant. (Quatre chiffres ne font que dix mille codes : c'est le blocage de
 * {@link PhoneLock} qui protège, pas l'empreinte.)
 */
public final class PhoneCode {

    public static final int LENGTH = 4;
    private static final int SALT_BYTES = 16;

    private PhoneCode() {}

    /** Exactement quatre chiffres ASCII, rien d'autre (ni espace, ni chiffre pleine chasse ou arabe). */
    public static boolean isValid(@Nullable String code) {
        if (code == null || code.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < LENGTH; i++) {
            char c = code.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** Un sel neuf : 16 octets en hexadécimal. */
    public static String newSalt(RandomSource random) {
        byte[] bytes = new byte[SALT_BYTES];
        for (int i = 0; i < SALT_BYTES; i++) {
            bytes[i] = (byte) random.nextInt(256);
        }
        return HexFormat.of().formatHex(bytes);
    }

    /** SHA-256 hexadécimal de {@code sel + ":" + code}. */
    public static String hash(String salt, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((salt + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 absent de la JVM", e);
        }
    }

    /** Le code essayé correspond-il à l'empreinte ? Un code mal formé ne correspond jamais. */
    public static boolean matches(String salt, String hash, String code) {
        if (!isValid(code) || hash == null) {
            return false;
        }
        return MessageDigest.isEqual(hash(salt, code).getBytes(StandardCharsets.UTF_8),
                hash.getBytes(StandardCharsets.UTF_8));
    }
}
