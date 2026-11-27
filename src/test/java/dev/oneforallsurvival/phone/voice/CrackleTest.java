package dev.oneforallsurvival.phone.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Le grésillement d'une barre : décodage, bruit léger, environ une trame sur dix muette, réencodage. */
class CrackleTest {

    private static short[] trame(int valeur) {
        short[] pcm = new short[960];
        Arrays.fill(pcm, (short) valeur);
        return pcm;
    }

    @Test
    void lamemeGraineDonneLeMemeGresillement() {
        FakeCodec c1 = new FakeCodec();
        FakeCodec c2 = new FakeCodec();
        Crackle a = new Crackle(c1, 42L);
        Crackle b = new Crackle(c2, 42L);
        for (int i = 0; i < 50; i++) {
            byte[] entree = FakeCodec.bytes(trame(1000 + i));
            assertArrayEquals(a.apply(entree), b.apply(entree), "trame " + i);
        }
        Crackle autre = new Crackle(new FakeCodec(), 43L);
        Crackle temoin = new Crackle(new FakeCodec(), 42L);
        boolean differe = false;
        for (int i = 0; i < 50 && !differe; i++) {
            byte[] entree = FakeCodec.bytes(trame(1000));
            differe = !Arrays.equals(autre.apply(entree), temoin.apply(entree));
        }
        assertTrue(differe, "une autre graine donne un autre bruit");
    }

    @Test
    void environUneTrameSurDixDevientMuette() {
        Random rnd = new Random(7);
        int muettes = 0;
        int n = 20_000;
        for (int i = 0; i < n; i++) {
            short[] sortie = Crackle.degrade(trame(5000), rnd);
            boolean muette = true;
            for (short s : sortie) {
                if (s != 0) {
                    muette = false;
                    break;
                }
            }
            if (muette) {
                muettes++;
            }
        }
        double part = muettes / (double) n;
        assertTrue(part > 0.08 && part < 0.12, "part de trames muettes : " + part);
    }

    @Test
    void leBruitEstLegerEtBorne() {
        Random rnd = new Random(11);
        boolean bruit = false;
        for (int i = 0; i < 200; i++) {
            short[] sortie = Crackle.degrade(trame(1000), rnd);
            if (sortie[0] == 0 && sortie[1] == 0 && sortie[2] == 0) {
                continue;
            }
            for (short s : sortie) {
                assertTrue(Math.abs(s - 1000) <= Crackle.NOISE, "écart " + (s - 1000));
                bruit |= s != 1000;
            }
        }
        assertTrue(bruit, "un bruit est ajouté");
        short[] haut = Crackle.degrade(trame(Short.MAX_VALUE), new Random(3));
        short[] bas = Crackle.degrade(trame(Short.MIN_VALUE), new Random(3));
        assertEquals(960, haut.length);
        assertEquals(960, bas.length);
    }

    @Test
    void decodeEtReencodeParLeCodecDuFlux() {
        FakeCodec codec = new FakeCodec();
        Crackle c = new Crackle(codec, 1L);
        c.apply(FakeCodec.bytes(trame(10)));
        c.apply(FakeCodec.bytes(trame(10)));
        assertEquals(2, codec.decoded);
        assertEquals(2, codec.encoded);
        c.reset();
        assertEquals(1, codec.resets);
        assertFalse(codec.closed);
        c.close();
        assertTrue(codec.closed);
    }

    @Test
    void uneTrameIllisibleNeCassePas() {
        FakeCodec codec = new FakeCodec();
        Crackle c = new Crackle(codec, 1L);
        byte[] sortie = c.apply(new byte[] {1, 2, 3});
        assertTrue(sortie.length > 0, "une trame impaire se décode en trame tronquée, rien ne casse");
    }
}
