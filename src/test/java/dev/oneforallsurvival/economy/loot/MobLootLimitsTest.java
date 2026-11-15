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
}
