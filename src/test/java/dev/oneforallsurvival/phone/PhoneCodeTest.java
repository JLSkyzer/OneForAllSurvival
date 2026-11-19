package dev.oneforallsurvival.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class PhoneCodeTest {

    @Test
    void quatreChiffresAcceptes() {
        assertTrue(PhoneCode.isValid("0000"));
        assertTrue(PhoneCode.isValid("9137"));
    }

    @Test
    void autresFormesRefusees() {
        for (String bad : new String[] {"123", "12345", "12a4", " 1234", "1234 ", "", null, "12\n4"}) {
            assertFalse(PhoneCode.isValid(bad), "refuse : " + bad);
        }
    }

    @Test
    void chiffresPleineChasseRefuses() {
        assertFalse(PhoneCode.isValid("\uFF11\uFF12\uFF13\uFF14"));
        assertFalse(PhoneCode.isValid("\u0661\u0662\u0663\u0664"));
    }

    @Test
    void lEmpreinteSeVerifie() {
        String salt = PhoneCode.newSalt(RandomSource.create(1L));
        String hash = PhoneCode.hash(salt, "4821");
        assertTrue(PhoneCode.matches(salt, hash, "4821"));
        assertFalse(PhoneCode.matches(salt, hash, "4822"));
    }

    @Test
    void deuxSelsDeuxEmpreintes() {
        String a = PhoneCode.newSalt(RandomSource.create(1L));
        String b = PhoneCode.newSalt(RandomSource.create(2L));
        assertNotEquals(a, b);
        assertEquals(32, a.length());
        assertNotEquals(PhoneCode.hash(a, "4821"), PhoneCode.hash(b, "4821"));
    }

    @Test
    void lEmpreinteNeContientPasLeCode() {
        String salt = PhoneCode.newSalt(RandomSource.create(3L));
        String hash = PhoneCode.hash(salt, "4821");
        assertEquals(64, hash.length());
        assertFalse(hash.contains("4821"));
    }

    @Test
    void empreinteCorrompueOuAbsenteRefuse() {
        assertFalse(PhoneCode.matches("aa", "", "4821"));
        assertFalse(PhoneCode.matches("aa", PhoneCode.hash("aa", "4821"), "bad"));
    }
}
