package dev.oneforallsurvival.client.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.client.title.TitleLayout.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Place du panneau d'un autre mod (Quick-Skin) sur l'écran titre : {@link TitleLayout#panel}. */
class TitlePanelTest {
    private static final int G = TitleLayout.PANEL_GAP;

    private static boolean touche(Rect a, Rect b) {
        // chevauchement ou écart inférieur à PANEL_GAP
        return a.x() - G < b.right() && b.x() < a.right() + G && a.y() - G < b.bottom() && b.y() < a.bottom() + G;
    }

    /** Ce que l'écran titre met autour : logo, nos quatre boutons, rangée du bas, version, mention de Mojang. */
    private static List<Rect> obstacles(int w, int h, Rect logo) {
        List<int[]> tailles = List.of(new int[] {40, 20}, new int[] {20, 20}, new int[] {20, 20});
        int copyW = 196; // « Copyright Mojang AB. Do not distribute! » à la police de Minecraft
        List<Rect> rangee = TitleLayout.bottomRow(w, h, tailles, w - copyW - 2 - TitleLayout.ROW_GAP);
        List<Rect> out = new ArrayList<>(TitleLayout.mainButtons(w, h, logo, rangee));
        out.addAll(rangee);
        out.add(logo);
        out.add(new Rect(w - copyW - 2, h - 10, copyW, 10));
        out.add(new Rect(w - 88 - 2, h - 20, 88, 9)); // « Minecraft 1.20.1 »
        return out;
    }

    @Test
    void seulsLesWidgetsDeQuickSkinFormentLePanneau() {
        // Classe du widget, ou classe du lambda d'action d'un bouton de Minecraft (nom d'une classe cachée).
        assertTrue(TitleWidgets.inPanel("com.quickskin.mod.client.gui.widget.PlayerWidget"));
        assertTrue(TitleWidgets.inPanel("com.quickskin.mod.client.gui.integration.MenuIntegration$$Lambda$4211/0x0000"));
        assertFalse(TitleWidgets.inPanel("net.minecraft.client.gui.components.Button"));
        assertFalse(TitleWidgets.inPanel("com.simibubi.create.foundation.gui.ScreenOpener$$Lambda$1/0x0"));
        assertFalse(TitleWidgets.inPanel("dev.oneforallsurvival.client.title.GoldButton"));
        assertFalse(TitleWidgets.inPanel("com.quickskinner.Autre"), "le paquet entier, pas un préfixe de nom");
        assertFalse(TitleWidgets.inPanel(null));
    }

    @Test
    void lApercuDuJoueurOccupeLaPlaceDeSonModele() {
        // Relevé de Quick-Skin à 854 x 480, échelle 2 : pieds en (180, 316), échelle 58 (tête 34 px au-dessus du widget).
        Rect r = TitleLayout.modelFootprint(180, 316, 58f);
        assertEquals(new Rect(180 - 34, 316 - 116, 69, 116), r);
        // isMouseOutsideModelArea de PlayerWidget : hauteur (int) (2 x échelle), largeur (int) (hauteur x 0,6).
        assertEquals(new Rect(100 - 52, 200 - 174, 104, 174), TitleLayout.modelFootprint(100, 200, 87.2f));
    }

    @Test
    void aDroiteEtAuMilieuQuandRienNeGene() {
        Rect p = new Rect(30, 500, 100, 150); // n'importe où : seule la taille compte
        Optional<Rect> place = TitleLayout.panel(400, 300, p, List.of(new Rect(10, 10, 150, 200)));
        assertEquals(new Rect(400 - G - 100, (300 - 150) / 2, 100, 150), place.orElseThrow());
    }

    @Test
    void unObstacleAuMilieuDecaleLePanneauEnHauteur() {
        Rect obstacle = new Rect(250, 140, 150, 20);
        Rect place = TitleLayout.panel(400, 300, new Rect(0, 0, 100, 100), List.of(obstacle)).orElseThrow();
        assertEquals(400 - G - 100, place.x(), "toujours collé à droite");
        assertFalse(touche(place, obstacle));
        // Le plus près du milieu : juste au-dessus ou juste au-dessous de l'obstacle.
        assertTrue(place.bottom() + G == obstacle.y() || place.y() - G == obstacle.bottom(), "place " + place);
    }

    @Test
    void sansPlaceLibreRienNestPropose() {
        assertTrue(TitleLayout.panel(400, 300, new Rect(0, 0, 100, 400), List.of()).isEmpty(), "plus haut que l'écran");
        // Moitié droite entièrement prise.
        assertTrue(TitleLayout.panel(400, 300, new Rect(0, 0, 100, 100), List.of(new Rect(150, 0, 250, 300))).isEmpty());
    }

    @Test
    void jamaisSurLeTitreAuxTaillesDEssai() {
        // Interfaces des essais en jeu : 854 x 480 aux échelles 1 et 2, 1920 x 1040 aux échelles 1 à 4, plus 320 x 240
        // (le plus petit écran que Minecraft donne).
        int[][] ecrans = {{854, 480}, {427, 240}, {1920, 1040}, {960, 520}, {640, 346}, {480, 260}, {320, 240}};
        int[][] panneaux = {{98, 20}, {100, 170}, {144, 190}, {120, 230}};
        for (int[] e : ecrans) {
            Rect logo = new Rect(Math.max(12, e[0] * 3 / 80), e[1] / 10, Math.min(260, e[0] / 3), e[1] / 6);
            List<Rect> autour = obstacles(e[0], e[1], logo);
            for (int[] t : panneaux) {
                Optional<Rect> place = TitleLayout.panel(e[0], e[1], new Rect(0, 0, t[0], t[1]), autour);
                if (place.isEmpty()) {
                    // Vérifié par une recherche exhaustive indépendante : aucune place libre du côté droit.
                    for (int x = G; x + t[0] <= e[0] - G; x++) {
                        for (int y = G; y + t[1] <= e[1] - G; y++) {
                            Rect r = new Rect(x, y, t[0], t[1]);
                            if (x < e[0] / 2 - t[0] / 2) {
                                continue;
                            }
                            assertFalse(autour.stream().noneMatch(o -> touche(r, o)), "place libre manquée : " + r
                                    + " pour " + t[0] + "x" + t[1] + " sur " + e[0] + "x" + e[1]);
                        }
                    }
                    continue;
                }
                Rect r = place.get();
                String cas = t[0] + "x" + t[1] + " sur " + e[0] + "x" + e[1] + " : " + r;
                assertEquals(t[0], r.w(), cas);
                assertEquals(t[1], r.h(), cas);
                assertTrue(r.x() >= G && r.y() >= G && r.right() <= e[0] - G && r.bottom() <= e[1] - G, "dans l'écran, " + cas);
                assertTrue(r.x() + r.w() / 2 >= e[0] / 2 - 1, "côté droit, " + cas);
                for (Rect o : autour) {
                    assertFalse(touche(r, o), "touche " + o + ", " + cas);
                }
            }
        }
    }
}
