package dev.oneforallsurvival.economy.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class MobLootLimitsTest {

    private static final UUID JOUEUR = UUID.fromString("00000000-0000-0000-0000-00000000abcd");
    private static final UUID AUTRE = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static final LocalDate JOUR = LocalDate.of(2026, 11, 15);
    private static final String WITHER = "minecraft:wither";

    @Test
    void unRaidPlafonneSesGainsTousJoueursConfondus() {
        MobLootLimits limites = new MobLootLimits();
        assertEquals(100, limites.raidRemaining("minecraft:overworld#7", 100));
        limites.addRaid("minecraft:overworld#7", 60);
        limites.addRaid("minecraft:overworld#7", 40);
        assertEquals(0, limites.raidRemaining("minecraft:overworld#7", 100));
        // Un autre raid repart à zéro, comme le même identifiant dans une autre dimension.
        assertEquals(100, limites.raidRemaining("minecraft:overworld#8", 100));
        assertEquals(100, limites.raidRemaining("minecraft:the_nether#7", 100));
        // Un plafond baissé sous ce qui est déjà payé ne rend rien de négatif.
        assertEquals(0, limites.raidRemaining("minecraft:overworld#7", 50));
    }

    @Test
    void unBossParJourEtParType() {
        MobLootLimits limites = new MobLootLimits();
        assertTrue(limites.bossAllowed(JOUEUR, WITHER, JOUR, 1));
        limites.addBoss(JOUEUR, WITHER, JOUR);
        assertFalse(limites.bossAllowed(JOUEUR, WITHER, JOUR, 1));
        assertTrue(limites.bossAllowed(JOUEUR, WITHER, JOUR, 2));
        assertTrue(limites.bossAllowed(JOUEUR, "minecraft:ender_dragon", JOUR, 1));
        assertTrue(limites.bossAllowed(AUTRE, WITHER, JOUR, 1));
        assertTrue(limites.bossAllowed(JOUEUR, WITHER, JOUR.plusDays(1), 1));
        // Zéro par jour : jamais.
        assertFalse(limites.bossAllowed(AUTRE, WITHER, JOUR, 0));
    }

    @Test
    void lesLimitesSurviventALaSauvegarde() {
        MobLootLimits limites = new MobLootLimits();
        limites.addRaid("minecraft:overworld#7", 60);
        limites.addBoss(JOUEUR, WITHER, JOUR);
        limites.addBoss(JOUEUR, WITHER, JOUR);
        MobLootLimits back = MobLootLimits.load(limites.save(new CompoundTag()));
        assertEquals(40, back.raidRemaining("minecraft:overworld#7", 100));
        assertEquals(2, back.bossCount(JOUEUR, WITHER, JOUR));
        assertEquals(0, back.bossCount(JOUEUR, WITHER, JOUR.plusDays(1)));
    }

    @Test
    void unNouveauJourEffaceLaVeille() {
        MobLootLimits limites = new MobLootLimits();
        limites.addBoss(JOUEUR, WITHER, JOUR);
        limites.addBoss(JOUEUR, "minecraft:elder_guardian", JOUR);
        limites.addBoss(JOUEUR, WITHER, JOUR.plusDays(1));
        assertEquals(1, limites.bossCount(JOUEUR, WITHER, JOUR.plusDays(1)));
        // Les jours passés de ce joueur ne sont plus gardés.
        assertEquals(1, limites.bossEntries());
    }

    @Test
    void unJourDePaieQuiReculeNeRouvrePasLeButinDuBoss() {
        MobLootLimits limites = new MobLootLimits();
        limites.addBoss(JOUEUR, WITHER, JOUR);
        // Le jour de paie recule (horloge, payHour ou fuseau changés) : le butin du jour D reste épuisé.
        assertFalse(limites.bossAllowed(JOUEUR, WITHER, JOUR.minusDays(1), 1));
        // Un compte fait pendant ce jour antérieur ne remplace pas l'entrée plus récente.
        limites.addBoss(JOUEUR, WITHER, JOUR.minusDays(1));
        assertEquals(1, limites.bossCount(JOUEUR, WITHER, JOUR));
        assertFalse(limites.bossAllowed(JOUEUR, WITHER, JOUR, 1));
        // Le jour D revenu, le butin du jour D est toujours épuisé, et il se rouvre bien le lendemain.
        assertTrue(limites.bossAllowed(JOUEUR, WITHER, JOUR.plusDays(1), 1));
    }

    private static final long HEURE = 3_600_000L;

    @Test
    void lePlafondHoraireTronqueEtSeRouvreApresSoixanteMinutes() {
        MobLootLimits limites = new MobLootLimits();
        long t0 = 1_000_000_000L;
        assertEquals(200, limites.hourlyRemaining(JOUEUR, t0, 200));
        limites.addHourly(JOUEUR, t0, 150);
        assertEquals(50, limites.hourlyRemaining(JOUEUR, t0 + 1, 200));
        limites.addHourly(JOUEUR, t0 + 60_000L, 50);
        assertEquals(0, limites.hourlyRemaining(JOUEUR, t0 + 120_000L, 200));
        assertEquals(200, limites.hourlyEarned(JOUEUR, t0 + 120_000L));
        // Les autres joueurs ont leur propre fenêtre.
        assertEquals(200, limites.hourlyRemaining(AUTRE, t0 + 120_000L, 200));
        // Fenêtre glissante : le premier gain sort à t0 + 60 min, le second une minute plus tard.
        assertEquals(0, limites.hourlyRemaining(JOUEUR, t0 + HEURE - 1, 200));
        assertEquals(150, limites.hourlyRemaining(JOUEUR, t0 + HEURE, 200));
        assertEquals(200, limites.hourlyRemaining(JOUEUR, t0 + HEURE + 60_000L, 200));
        // Zéro : pas de plafond.
        assertTrue(limites.hourlyRemaining(JOUEUR, t0, 0) == Long.MAX_VALUE);
    }

    @Test
    void lePlafondHoraireSurvitALaSauvegarde() {
        MobLootLimits limites = new MobLootLimits();
        limites.addHourly(JOUEUR, 5_000L, 120);
        MobLootLimits back = MobLootLimits.load(limites.save(new CompoundTag()));
        assertEquals(80, back.hourlyRemaining(JOUEUR, 6_000L, 200));
        assertEquals(200, back.hourlyRemaining(JOUEUR, 5_000L + HEURE, 200));
    }
}
