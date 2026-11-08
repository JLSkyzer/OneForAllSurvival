package dev.oneforallsurvival.earlywindow.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.oneforallsurvival.earlywindow.scene.ProgressModel.Phase;
import dev.oneforallsurvival.earlywindow.scene.StatusMessages.Status;
import org.junit.jupiter.api.Test;

class StatusMessagesTest {
    @Test
    void demarrageDeForge() {
        assertEquals(Phase.FORGE_START, StatusMessages.phaseOf(null, "Forge loading 47.4.28"));
        assertEquals(new Status("Les moteurs toussent…", "démarrage"),
                StatusMessages.of(Phase.FORGE_START, "", 0, 0));
    }

    @Test
    void decouverteDesMods() {
        assertEquals(new Status("Inventaire de la soute…", "recherche des mods"),
                StatusMessages.of(Phase.MOD_DISCOVERY, "", 0, 0));
    }

    @Test
    void chargementDesMods() {
        assertEquals(Phase.MOD_LOADING, StatusMessages.phaseOf("EARLY", "Loading mods"));
        assertEquals(Phase.MOD_LOADING, StatusMessages.phaseOf("Mod Gather", "Mod Gather working"));
        assertEquals(new Status("Tri des caisses de l'épave…", "mods 9/18"),
                StatusMessages.of(Phase.MOD_LOADING, "Mod Gather working", 9, 18));
    }

    @Test
    void registres() {
        assertEquals(Phase.REGISTRIES,
                StatusMessages.phaseOf("State transition LOAD_REGISTRIES running", null));
        assertEquals(Phase.REGISTRIES,
                StatusMessages.phaseOf(null, "REGISTERING minecraft:worldgen/biome"));
        assertEquals(new Status("Le camp s'organise…", "registres"),
                StatusMessages.of(Phase.REGISTRIES, "", 0, 0));
    }

    @Test
    void texturesEtSons() {
        assertEquals(new Status("Exploration du littoral…", "textures"),
                StatusMessages.of(Phase.RESOURCES, "", 0, 0));
    }

    @Test
    void fin() {
        assertEquals("Bienvenue sur Lost Island.", StatusMessages.of(Phase.DONE, "", 0, 0).theme());
    }

    @Test
    void unLibelleInconnuGardeLaPhaseCourante() {
        // phaseOf rend null : l'appelant garde sa phase.
        assertNull(StatusMessages.phaseOf("Autre barre", "Autre message"));
        assertNull(StatusMessages.phaseOf(null, null));
    }

    @Test
    void leDetailDesModsPorteLeCompteEtLeNom() {
        assertEquals("mods 23/154 : create",
                StatusMessages.of(Phase.MOD_LOADING, "create", 23, 154).detail());
        assertEquals("mods 23/154",
                StatusMessages.of(Phase.MOD_LOADING, "Mod Gather working", 23, 154).detail());
    }
}
