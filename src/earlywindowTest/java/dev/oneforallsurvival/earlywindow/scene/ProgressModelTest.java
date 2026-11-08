package dev.oneforallsurvival.earlywindow.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.earlywindow.scene.ProgressModel.Phase;
import org.junit.jupiter.api.Test;

class ProgressModelTest {
    @Test
    void lesPartsFontUn() {
        float somme = 0f;
        for (float w : ProgressModel.WEIGHTS) {
            assertTrue(w > 0f);
            somme += w;
        }
        assertEquals(1f, somme, 1e-6f);
        assertEquals(Phase.DONE.ordinal(), ProgressModel.WEIGHTS.length);
    }

    @Test
    void laBarreNeReculeJamais() {
        ProgressModel m = new ProgressModel();
        m.report(Phase.MOD_LOADING, 0.9f);
        float haut = m.displayed(10.0);
        m.report(Phase.RESOURCES, 0.1f);
        assertTrue(m.displayed(10.0) >= haut);
    }

    @Test
    void unRapportEnRetardEstIgnore() {
        ProgressModel m = new ProgressModel();
        m.report(Phase.RESOURCES, 0.5f);
        float v = m.displayed(10.0);
        m.report(Phase.MOD_DISCOVERY, 0.2f);
        assertEquals(v, m.displayed(10.0), 0f);
    }

    @Test
    void laBarreRattrapeEnDouceur() {
        ProgressModel m = new ProgressModel();
        m.report(Phase.RESOURCES, 0f); // cible bien au-dessus de 0,5
        assertTrue(m.displayed(0.1) <= 0.3f + 1e-6f);
    }

    /** Avancement réel de Minecraft relevé en tâche 8 (lancement 3) : {secondes depuis la première image, réel}. */
    private static final double[][] RELEVE_T8 = {
        {0.0, 0.703}, {1.012, 0.946}, {2.016, 0.979}, {3.03, 0.979}, {4.03, 0.979}, {5.046, 1.0}, {6.05, 1.0},
        {7.5, 1.0},
    };

    @Test
    void lesRessourcesSuiventLeTempsEtNonLAvanceDeMinecraft() {
        double attendu = ProgressModel.expectedSeconds(Phase.RESOURCES);
        // Une seconde après la première image, Minecraft dit 0,95 : la barre n'en montre que le temps écoulé.
        assertEquals((float) (1.012 / attendu), ProgressModel.resourcesFraction(0.946f, 1.012), 1e-6f);
        assertTrue(ProgressModel.resourcesFraction(0.946f, 1.012) < 0.25f);
        assertEquals(0f, ProgressModel.resourcesFraction(0.703f, 0.0), 0f);
        // À mi-chemin du rechargement, la barre est à mi-chemin de la phase.
        assertEquals(0.5f, ProgressModel.resourcesFraction(1f, attendu / 2), 1e-6f);
    }

    @Test
    void lesRessourcesNeDevancentJamaisLeRechargement() {
        // Rechargement plus lent que prévu (autre poste) : l'avancement réel borne la barre.
        assertEquals(0.5f, ProgressModel.resourcesFraction(0.5f, 30.0), 0f);
    }

    @Test
    void lesRessourcesRestentSousLeurPlafondJusquALaFin() {
        assertEquals(ProgressModel.RESOURCES_CAP, ProgressModel.resourcesFraction(1f, 100.0), 0f);
        assertTrue(ProgressModel.RESOURCES_CAP < 1f);
        ProgressModel m = new ProgressModel();
        m.report(Phase.RESOURCES, ProgressModel.resourcesFraction(1f, 100.0));
        assertTrue(m.displayed(10.0) < 1f);
        m.report(Phase.DONE, 1f);
        assertEquals(1f, m.displayed(10.0), 0f);
    }

    @Test
    void lesRessourcesNeReculentJamaisSurLeReleve() {
        float avant = -1f;
        for (double[] point : RELEVE_T8) {
            float f = ProgressModel.resourcesFraction((float) point[1], point[0]);
            assertTrue(f >= avant, "recul à " + point[0] + " s");
            avant = f;
        }
        // Barre entière, une seconde après le début des ressources : loin de la fin (≈ 97 % avant le ruling 26).
        ProgressModel m = new ProgressModel();
        m.report(Phase.RESOURCES, ProgressModel.resourcesFraction(0.946f, 1.012));
        float barre = m.displayed(10.0);
        assertTrue(barre < 0.85f, "barre à " + barre);
    }

    @Test
    void doneDonneUn() {
        ProgressModel m = new ProgressModel();
        m.report(Phase.DONE, 0f);
        assertEquals(1f, m.displayed(10.0), 0f);
    }
}
