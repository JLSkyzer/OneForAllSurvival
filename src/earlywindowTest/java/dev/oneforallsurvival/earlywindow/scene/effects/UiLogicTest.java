package dev.oneforallsurvival.earlywindow.scene.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.earlywindow.scene.StatusMessages;
import dev.oneforallsurvival.earlywindow.scene.StoryClock;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Logique du logo et de la barre, sans GL : pourcentage, texte d'état, courbe des lettres, reflets. */
class UiLogicTest {
    @Test
    void lePourcentageEstCeluiDeComposePy() {
        assertEquals("69 %", ProgressUi.percent(0.69f));
        assertEquals("0 %", ProgressUi.percent(0f));
        assertEquals("100 %", ProgressUi.percent(1f));
        // int(p * 100 + 1e-4) : 0.29 * 100 vaut 28.999… en binaire, le 1e-4 le ramène à 29.
        assertEquals("29 %", ProgressUi.percent(0.29f));
    }

    @Test
    void leTexteSuitLeVraiChargementPuisSouhaiteLaBienvenue() {
        StatusMessages.Status s = new StatusMessages.Status("Exploration du littoral…", "textures");
        assertEquals("Exploration du littoral… (textures)", ProgressUi.statusLine(s, 0.69f));
        assertEquals("Bienvenue sur Lost Island.", ProgressUi.statusLine(s, 1f));
        assertEquals("", ProgressUi.statusLine(null, 0.2f));
    }

    @Test
    void laBarreArriveDesLOuvertureEnTempsReelSansAttendreLeRecit() {
        // Demande de Killian : visible dès les premières images, noir d'ouverture compris (récit encore à t = 0).
        assertEquals(0.0, ProgressUi.intro(Double.NaN), 1e-12); // images de l'interface pas encore arrivées
        assertEquals(0.0, ProgressUi.intro(0.0), 1e-12);
        assertEquals(0.5, ProgressUi.intro(ProgressUi.INTRO_SECONDS / 2), 1e-12);
        assertEquals(1.0, ProgressUi.intro(ProgressUi.INTRO_SECONDS), 1e-12);
        assertEquals(1.0, ProgressUi.intro(1.5), 1e-12); // avant le début du récit (≈ 2 s, ruling 14) : pleine
        assertTrue(ProgressUi.intro(0.1) > 0);
        assertTrue(ProgressUi.INTRO_SECONDS < StoryClock.BAR_IN);
    }

    @Test
    void lesLettresRebondissentCommeEaseOutBack() {
        // Valeurs de ease_out_back de compose.py.
        assertEquals(0.0, LogoEffect.easeOutBack(0), 1e-12);
        assertEquals(1.0, LogoEffect.easeOutBack(1), 1e-12);
        assertEquals(1.099352, LogoEffect.easeOutBack(0.6), 1e-6);
        assertEquals(1.0, LogoEffect.easeOutBack(3), 1e-12);
    }

    @Test
    void lesRefletsDeLaVideoPuisCeuxDeLAubeTenue() {
        double start = StoryClock.BAR_IN + 0.1;
        // Premier reflet à start + 1.4 (récit), à mi-course 0.55 s plus tard.
        List<double[]> first = LogoEffect.sweeps(start + 1.4 + 0.55, 50);
        assertEquals(1, first.size());
        assertEquals(0.5, first.get(0)[0], 1e-9);
        assertEquals(1.0, first.get(0)[1], 1e-12);
        // Second reflet à LOAD_END + 0.25 = 29.25.
        assertEquals(1, LogoEffect.sweeps(29.8, 29.8).size());
        assertTrue(LogoEffect.sweeps(32, 32).isEmpty());
        // Récit tenu : un reflet par période de temps réel, à pleine intensité au milieu.
        double period = LogoEffect.HELD_SWEEP_PERIOD;
        List<double[]> held = LogoEffect.sweeps(StoryClock.HOLD, 10 * period + 0.55);
        assertEquals(1, held.size());
        assertEquals(1.0, held.get(0)[1], 1e-12);
        assertTrue(LogoEffect.sweeps(StoryClock.HOLD, 10 * period + 3).isEmpty());
    }
}
