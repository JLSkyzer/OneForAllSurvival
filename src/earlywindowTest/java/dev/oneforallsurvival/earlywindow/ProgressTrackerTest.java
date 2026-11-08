package dev.oneforallsurvival.earlywindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.earlywindow.ProgressTracker.Meter;
import dev.oneforallsurvival.earlywindow.ProgressTracker.Reading;
import dev.oneforallsurvival.earlywindow.scene.ProgressModel;
import dev.oneforallsurvival.earlywindow.scene.ProgressModel.Phase;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Libellés et messages réels relevés en tâche 0 (logs/lostisland-earlywindow.log). */
class ProgressTrackerTest {
    private static final Meter EARLY_BOOT = new Meter("EARLY", "Bootstrapping Minecraft", 0, 0);
    private static final Meter EARLY_GFX = new Meter("EARLY", "Initializing Game Graphics", 0, 0);
    private static final Meter EARLY_MODS = new Meter("EARLY", "Loading mods", 0, 0);

    @Test
    void laFenetreOuvreSurLeDemarrage() {
        Reading r = new ProgressTracker().update(0.1, List.of(), List.of());
        assertEquals(Phase.FORGE_START, r.phase());
        assertEquals("Les moteurs toussent… (démarrage)", r.status().line());
    }

    @Test
    void leMessageDeForgeOuvreLaDecouverteMemeAvecLaBarreEarlyDuDebut() {
        ProgressTracker t = new ProgressTracker();
        t.update(0.0, List.of(EARLY_BOOT), List.of());
        Reading r = t.update(1.0, List.of(EARLY_BOOT), List.of("Forge loading 47.4.28"));
        assertEquals(Phase.MOD_DISCOVERY, r.phase());
        assertEquals("Inventaire de la soute… (recherche des mods)", r.status().line());
        // Libellés de la découverte relevés en tâche 7, puis le passage de la fenêtre : toujours la découverte.
        for (String label : List.of("Loaded language provider javafml 47", "Scanning mod candidates",
                "Loading bootstrap resources")) {
            assertEquals(Phase.MOD_DISCOVERY, t.update(5.0, List.of(new Meter("EARLY", label, 0, 0)), List.of()).phase());
        }
        assertEquals(Phase.MOD_DISCOVERY, t.update(12.0, List.of(EARLY_GFX), List.of()).phase());
    }

    @Test
    void laDecouverteAvanceAuTempsSansJamaisAnnoncerSaFin() {
        ProgressTracker t = new ProgressTracker();
        t.update(0.0, List.of(), List.of("Forge loading 47.4.28"));
        float mid = t.update(ProgressModel.expectedSeconds(Phase.MOD_DISCOVERY) / 2, List.of(), List.of()).fraction();
        assertEquals(0.5f, mid, 0.01f);
        assertEquals(ProgressTracker.CREEP_MAX, t.update(300.0, List.of(), List.of()).fraction(), 1e-6f);
    }

    @Test
    void lesModsSuiventModGather() {
        ProgressTracker t = new ProgressTracker();
        t.update(0.0, List.of(EARLY_BOOT), List.of("Forge loading 47.4.28"));
        Reading r = t.update(21.9, List.of(new Meter("Mod Gather", "Mod Gather: dispatching CONSTRUCT", 9, 18),
                EARLY_MODS), List.of("Forge version 47.4.28"));
        assertEquals(Phase.MOD_LOADING, r.phase());
        assertEquals(0.5f, r.fraction(), 1e-6f);
        assertEquals("Tri des caisses de l'épave… (mods 9/18)", r.status().line());
        // Seule la barre EARLY « Loading mods » suffit aussi à entrer dans la phase.
        ProgressTracker u = new ProgressTracker();
        assertEquals(Phase.MOD_LOADING, u.update(20.9, List.of(EARLY_MODS), List.of()).phase());
    }

    @Test
    void lesRegistresPuisLesRessourcesPuisLaFin() {
        ProgressTracker t = new ProgressTracker();
        t.update(21.0, List.of(new Meter("Mod Gather", "Mod Gather working", 18, 18)), List.of());
        Reading reg = t.update(28.9, List.of(new Meter("State transition LOAD_REGISTRIES running", "", 0, 0),
                new Meter("Mod Gather", "Mod Gather working", 18, 18)), List.of("REGISTERING minecraft:worldgen/biome"));
        assertEquals(Phase.REGISTRIES, reg.phase());
        assertEquals("Le camp s'organise… (registres)", reg.status().line());
        Reading res = t.update(31.0, List.of(new Meter(ProgressTracker.RESOURCES_METER, ProgressTracker.RESOURCES_METER,
                40, 100)), List.of());
        assertEquals(Phase.RESOURCES, res.phase());
        assertEquals(0.4f, res.fraction(), 1e-6f);
        assertEquals("Exploration du littoral… (textures)", res.status().line());
        Reading done = t.update(44.0, List.of(), List.of());
        assertEquals(Phase.DONE, done.phase());
        assertEquals(1f, done.fraction());
        assertEquals("Bienvenue sur Lost Island.", done.status().line());
    }

    @Test
    void laPhaseNeReculeJamais() {
        ProgressTracker t = new ProgressTracker();
        t.update(0.0, List.of(new Meter("State transition LOAD_REGISTRIES running", "", 0, 0)), List.of());
        Reading r = t.update(1.0, List.of(EARLY_MODS), List.of("Forge version 47.4.28"));
        assertEquals(Phase.REGISTRIES, r.phase());
        t.enter(Phase.MOD_LOADING, 2.0);
        assertEquals(Phase.REGISTRIES, t.phase());
        // Sans barre des ressources vue, l'absence de barre ne veut pas dire « fini ».
        assertTrue(t.update(3.0, List.of(), List.of()).phase() != Phase.DONE);
    }
}
