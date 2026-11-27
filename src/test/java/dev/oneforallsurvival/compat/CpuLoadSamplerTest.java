package dev.oneforallsurvival.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class CpuLoadSamplerTest {
    /** Une mesure lente comme celle de Windows (≈ 0,1 s relevé chez Killian), ici 300 ms. */
    private static double lente(AtomicInteger appels, double valeur) {
        appels.incrementAndGet();
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return valeur;
    }

    private static void attendre(BooleanSupplier condition, long maxMs, String quoi) throws InterruptedException {
        long fin = System.currentTimeMillis() + maxMs;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) {
                throw new AssertionError("délai dépassé : " + quoi);
            }
            Thread.sleep(5);
        }
    }

    private static Thread filDeMesure(String nom) {
        return Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals(nom)).findFirst()
                .orElseThrow();
    }

    @Test
    void uneLectureNAttendJamaisLaMesure() throws InterruptedException {
        AtomicInteger appels = new AtomicInteger();
        CpuLoadSampler s = new CpuLoadSampler(() -> lente(appels, 0.42), () -> -1.0, 20, 60_000, "essai-lente");
        for (int i = 0; i < 50; i++) {
            long debut = System.nanoTime();
            s.processLoad();
            long ms = (System.nanoTime() - debut) / 1_000_000L;
            assertTrue(ms < 50, "lecture " + i + " : " + ms + " ms, la mesure dure 300 ms");
        }
        // La valeur arrive par le fil de fond, puis reste la dernière mesurée.
        attendre(() -> s.processLoad() == 0.42, 3_000, "première mesure");
        assertEquals(0.42, s.processLoad());
    }

    @Test
    void rienNeDemarreAvantLaPremiereLecture() throws InterruptedException {
        AtomicInteger appels = new AtomicInteger();
        CpuLoadSampler s = new CpuLoadSampler(() -> { appels.incrementAndGet(); return 0.1; }, () -> -1.0, 10, 60_000,
                "essai-paresseux");
        Thread.sleep(100);
        assertFalse(s.started());
        assertEquals(0, appels.get(), "panneau jamais affiché : aucune mesure");
        assertEquals(-1.0, s.processLoad(), "avant toute mesure");
        assertTrue(s.started());
        assertTrue(filDeMesure("essai-paresseux").isDaemon(), "le fil ne doit pas retenir la fermeture du jeu");
        attendre(() -> appels.get() > 0, 3_000, "mesure après la lecture");
    }

    @Test
    void laChargeDuSystemeNEstMesureeQuEnRepli() throws InterruptedException {
        AtomicInteger systeme = new AtomicInteger();
        CpuLoadSampler dispo = new CpuLoadSampler(() -> 0.3, () -> { systeme.incrementAndGet(); return 0.9; }, 10,
                60_000, "essai-dispo");
        dispo.processLoad();
        attendre(() -> dispo.samples() >= 3, 3_000, "trois mesures");
        assertEquals(0, systeme.get(), "charge du processus disponible : pas de repli");
        assertEquals(-1.0, dispo.systemLoad());

        CpuLoadSampler repli = new CpuLoadSampler(() -> -1.0, () -> 0.9, 10, 60_000, "essai-repli");
        repli.processLoad();
        attendre(() -> repli.systemLoad() == 0.9, 3_000, "repli sur la charge du système");
        assertEquals(-1.0, repli.processLoad());
    }

    @Test
    void uneMesureQuiEchoueNArretePasLeFil() throws InterruptedException {
        AtomicInteger appels = new AtomicInteger();
        CpuLoadSampler s = new CpuLoadSampler(() -> {
            if (appels.incrementAndGet() % 2 == 1) {
                throw new IllegalStateException("compteur indisponible");
            }
            return 0.5;
        }, () -> Double.NaN, 10, 60_000, "essai-erreur");
        s.processLoad();
        attendre(() -> appels.get() >= 5, 3_000, "mesures après des erreurs");
        assertTrue(filDeMesure("essai-erreur").isAlive());
        attendre(() -> s.processLoad() == -1.0 || s.processLoad() == 0.5, 3_000, "valeur lisible");
        // NaN (système) se lit -1, jamais NaN.
        attendre(() -> s.systemLoad() == -1.0, 3_000, "NaN ramené à -1");
    }

    @Test
    void sansLectureLeFilSEndortPuisRepart() throws InterruptedException {
        AtomicInteger appels = new AtomicInteger();
        CpuLoadSampler s = new CpuLoadSampler(() -> { appels.incrementAndGet(); return 0.2; }, () -> -1.0, 10, 150,
                "essai-veille");
        s.processLoad();
        attendre(s::idle, 3_000, "mise en veille sans lecture");
        int endormi = appels.get();
        Thread.sleep(300);
        assertEquals(endormi, appels.get(), "plus aucune mesure pendant la veille");
        s.processLoad();
        attendre(() -> appels.get() > endormi, 3_000, "reprise à la lecture suivante");
    }
}
