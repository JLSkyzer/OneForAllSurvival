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

    @Test
    void doneDonneUn() {
        ProgressModel m = new ProgressModel();
        m.report(Phase.DONE, 0f);
        assertEquals(1f, m.displayed(10.0), 0f);
    }
}
