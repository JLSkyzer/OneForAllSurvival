package dev.oneforallsurvival.client.tab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.client.tab.TabLayout.Mayor;
import dev.oneforallsurvival.client.tab.TabLayout.Mode;
import dev.oneforallsurvival.client.tab.TabLayout.Result;
import dev.oneforallsurvival.client.tab.TabLayout.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mise en page comparée aux maquettes : les cas a, b et e reprennent les pseudos et métiers dessinés, et leurs bords
 * (gauche et bas du panneau) ont été relevés sur les PNG ; les foules de 40 et 80 joueurs sont celles de la section 8 de
 * la spécification (pseudos de 66 et 75 px au plus, « Agriculteur » le plus long des métiers, maire présent).
 */
class TabLayoutTest {

    private static final VanillaWidths W = VanillaWidths.INSTANCE;
    private static final String DAY = "14";
    private static final String CLOCK = "18:40";

    @Test
    void laTableDeLargeursRedonneCelleDeLaSpecification() {
        assertEquals(73, W.boldWidth("LOST ISLAND"));
        assertEquals(14, W.boldWidth("RP"));
        assertEquals(95, W.width("· liste des joueurs"));
        assertEquals(98, W.width("survivants sur l'île"));
        assertEquals(92, W.width("survivant sur l'île"));
        assertEquals(33, W.boldWidth("MAIRE"));
        assertEquals(34, W.width("de l'île"));
        assertEquals(24, W.width("999+"));
        assertEquals(40, W.width("999+ ms"));
        assertEquals(24, W.width("Jour"));
        assertEquals(30, W.width("Heure"));
        assertEquals(108, W.width("Vol 417 · île inconnue"));
        assertEquals(48, W.width("Survivant"));
        assertEquals(30, W.width("Métier"));
        assertEquals(12, W.width("ms"));
        assertEquals(8, W.width("…"));
        assertEquals(96, W.width("WWWWWWWWWWWWWWWW"));
        assertEquals(71, TabLayout.mayorTitleWidth(W));
    }

    // --- Maquettes dessinées --------------------------------------------------------------------------------------

    @Test
    void maquetteA_unJoueurSansMaire() {
        Result r = TabLayout.compute(List.of(new Row("Naufrage_Tom", "Citoyen")), null, 1, DAY, CLOCK, 960, 540, W);
        assertEquals(Mode.FULL, r.mode());
        assertEquals(1, r.cols());
        assertEquals(1, r.rows());
        assertFalse(r.hasMayor());
        assertEquals(316, r.x(), "bord gauche relevé sur a-1-joueur.png");
        assertEquals(75, r.y() + r.height(), "bas relevé sur a-1-joueur.png");
        assertEquals(69, r.height());
    }

    private static List<Row> maquetteB() {
        return List.of(new Row("Docteur_Ash", "Médecin"), new Row("Lila_RP", "Médecin"), new Row("Bricoleur_Jo", "Ingénieur"),
                new Row("Agent_Kovak", "Policier"), new Row("Capitaine_Rex", "Policier"), new Row("Nina_Potager", "Agriculteur"),
                new Row("Paysan_Lou", "Agriculteur"), new Row("Mia_Survie", "Citoyen"), new Row("Naufrage_Tom", "Citoyen"),
                new Row("Sable_Noir", "Citoyen"), new Row("xX_Coco_Xx", "Citoyen"));
    }

    @Test
    void maquetteB_douzeJoueursEchelle2() {
        Result r = TabLayout.compute(maquetteB(), new Mayor("Maire_Elsa", "Agriculteur"), 12, DAY, CLOCK, 960, 540, W);
        assertEquals(Mode.FULL, r.mode());
        assertEquals(2, r.cols());
        assertEquals(6, r.rows());
        assertEquals(430, r.width());
        assertEquals(265, r.x(), "bord gauche relevé sur b-12-joueurs.png");
        assertEquals(159, r.y() + r.height(), "bas relevé sur b-12-joueurs.png");
        assertEquals(153, r.height());
        assertEquals(11, r.shown());
        assertEquals(0, r.hidden());
        assertFalse(r.shortForms());
        // Colonne : pastille, métier, ms et barres.
        assertEquals(71, r.nameW());
        assertEquals(57, r.jobW());
        assertEquals(92, r.dot());
        assertEquals(98, r.jobX());
        assertEquals(185, r.msRight());
        assertEquals(189, r.bars());
        assertEquals(201, r.colWidth());
        assertEquals(28, r.mayorY());
        assertEquals(58, r.colsY());
        assertEquals(138, r.footY());
    }

    @Test
    void maquetteF_douzeJoueursEchelle3() {
        Result r = TabLayout.compute(maquetteB(), new Mayor("Maire_Elsa", "Agriculteur"), 12, DAY, CLOCK, 640, 360, W);
        assertEquals(2, r.cols());
        assertEquals(6, r.rows());
        assertEquals(105, r.x(), "bord gauche relevé sur f-12-joueurs-echelle3.png");
        assertEquals(159, r.y() + r.height(), "bas relevé sur f-12-joueurs-echelle3.png");
    }

    @Test
    void maquetteE_casLimites() {
        String metierLong = TabRules.truncate("Responsable du port de pêche", TabRules.JOB_MAX_W, W::width);
        List<Row> rows = List.of(new Row("Docteur_Ash", "Médecin"), new Row("WWWWWWWWWWWWWWWW", "Policier"),
                new Row("Lucky_Vince", "Gérant de casino"), new Row("Pecheur_Noe", "Pêcheur"),
                new Row("Capitaine_Marin", metierLong), new Row("Mia_Survie", "Citoyen"), new Row("Nouveau_Venu", "Citoyen"),
                new Row("Admin_Fantome", "Citoyen"));
        Result r = TabLayout.compute(rows, new Mayor("Maire_Elsa", "Citoyen"), 8, DAY, CLOCK, 960, 540, W);
        assertEquals(Mode.FULL, r.mode());
        assertEquals(1, r.cols());
        assertEquals(8, r.rows());
        assertEquals(96, r.nameW());
        assertEquals(87, r.jobW());
        assertEquals(313, r.x(), "bord gauche relevé sur e-cas-limites.png");
        assertEquals(181, r.y() + r.height(), "bas relevé sur e-cas-limites.png");
    }

    // --- Foules de la section 8 ------------------------------------------------------------------------------------

    /** {@code n} joueurs hors maire, dont le plus long pseudo mesure {@code nameW} px ; « Agriculteur » est présent. */
    private static List<Row> foule(int n, int nameW) {
        String[] metiers = {"Médecin", "Ingénieur", "Policier", "Agriculteur", "Citoyen"};
        List<Row> rows = new ArrayList<>();
        String plusLong = nameW == 75 ? "aaaaaaaaaaaal" : "a".repeat(nameW / 6);
        assertEquals(nameW, W.width(plusLong));
        rows.add(new Row(plusLong, "Citoyen"));
        for (int i = 1; i < n; i++) {
            rows.add(new Row("Joueur" + i, metiers[i % metiers.length]));
        }
        return rows;
    }

    private static Result quarante(int gw, int gh) {
        return TabLayout.compute(foule(39, 66), new Mayor("Iris_Palme", "Ingénieur"), 39, DAY, CLOCK, gw, gh, W);
    }

    private static Result quatreVingts(int gw, int gh) {
        return TabLayout.compute(foule(79, 75), new Mayor("Theo_Bambou", "Citoyen"), 78, DAY, CLOCK, gw, gh, W);
    }

    private static Result douze(int gw, int gh) {
        return TabLayout.compute(maquetteB(), new Mayor("Maire_Elsa", "Agriculteur"), 12, DAY, CLOCK, gw, gh, W);
    }

    private static void attendu(Result r, Mode mode, int cols, int rows, int hidden, int pw, int bottom, String cas) {
        assertEquals(mode, r.mode(), cas + " : mode");
        assertEquals(cols, r.cols(), cas + " : colonnes");
        assertEquals(rows, r.rows(), cas + " : lignes");
        assertEquals(hidden, r.hidden(), cas + " : joueurs cachés");
        if (pw > 0) {
            assertEquals(pw, r.width(), cas + " : largeur du panneau");
        }
        if (bottom > 0) {
            assertEquals(bottom, r.y() + r.height(), cas + " : bas du panneau");
        }
    }

    @Test
    void echelle1() {
        attendu(douze(1920, 1080), Mode.FULL, 2, 6, 0, 430, 159, "12 à l'échelle 1");
        attendu(quarante(1920, 1080), Mode.FULL, 4, 10, 0, 0, 0, "40 à l'échelle 1");
        attendu(quatreVingts(1920, 1080), Mode.FULL, 8, 10, 0, 0, 0, "80 à l'échelle 1");
    }

    @Test
    void echelle2() {
        attendu(douze(960, 540), Mode.FULL, 2, 6, 0, 430, 159, "12 à l'échelle 2 (b)");
        attendu(quarante(960, 540), Mode.FULL, 4, 10, 0, 836, 203, "40 à l'échelle 2 (c)");
        attendu(quatreVingts(960, 540), Mode.FULL, 4, 20, 0, 872, 313, "80 à l'échelle 2 (d)");
    }

    @Test
    void echelle3() {
        attendu(douze(640, 360), Mode.FULL, 2, 6, 0, 430, 159, "12 à l'échelle 3 (f)");
        attendu(quarante(640, 360), Mode.FULL, 2, 20, 0, 420, 313, "40 à l'échelle 3 (g)");
        attendu(quatreVingts(640, 360), Mode.COMPACT, 4, 20, 0, 484, 313, "80 à l'échelle 3 (h)");
    }

    @Test
    void echelle4() {
        attendu(douze(480, 270), Mode.FULL, 2, 6, 0, 430, 0, "12 à l'échelle 4");
        attendu(quarante(480, 270), Mode.COMPACT, 3, 13, 0, 340, 0, "40 à l'échelle 4");
        Result r = quatreVingts(480, 270);
        attendu(r, Mode.COMPACT, 3, 15, 35, 364, 258, "80 à l'échelle 4 (j)");
        assertEquals(44, r.shown());
        assertEquals(58, r.x(), "bord gauche relevé sur j-80-joueurs-echelle4.png");
    }

    @Test
    void petiteFenetre854x480() {
        attendu(douze(427, 240), Mode.FULL, 1, 11, 0, 340, 0, "12 en 854×480");
        attendu(quarante(427, 240), Mode.COMPACT, 3, 12, 4, 0, 0, "40 en 854×480");
        Result r = quatreVingts(427, 240);
        attendu(r, Mode.COMPACT, 3, 12, 44, 364, 225, "80 en 854×480 (i)");
        assertEquals(31, r.x(), "bord gauche relevé sur i-80-joueurs-854x480.png");
    }

    @Test
    void lePanneauTientToujoursDansLEcran() {
        Map<String, int[]> tailles = Map.of("320x240", new int[] {320, 240}, "427x240", new int[] {427, 240},
                "480x270", new int[] {480, 270}, "640x360", new int[] {640, 360}, "960x540", new int[] {960, 540});
        for (var e : tailles.entrySet()) {
            int gw = e.getValue()[0];
            int gh = e.getValue()[1];
            for (int n : new int[] {0, 1, 11, 39, 79, 200}) {
                Result r = TabLayout.compute(foule(Math.max(1, n), 75).subList(0, n), new Mayor("WWWWWWWWWWWWWWWW",
                        "Responsable du port de pêche"), n + 1, "1234", CLOCK, gw, gh, W);
                String cas = n + " joueurs en " + e.getKey();
                assertTrue(r.width() <= gw - 16, cas + " : largeur " + r.width());
                assertTrue(r.y() + r.height() <= gh - 6, cas + " : bas " + (r.y() + r.height()));
                assertEquals(n, r.shown() + r.hidden(), cas);
                if (r.hidden() > 0) {
                    assertEquals(r.cols() * r.rows() - 1, r.shown(), cas + " : une case pour « et N autres »");
                }
            }
        }
    }

    @Test
    void desPseudosCourtsNeFontPasChevaucherLesEnTetes() {
        Result r = TabLayout.compute(List.of(new Row("Dev", "Citoyen")), null, 1, DAY, CLOCK, 960, 540, W);
        assertEquals(W.width("Survivant"), r.nameW());
        assertTrue(r.dot() >= 13 + W.width("Survivant") + 8, "« Métier » commence après « Survivant »");
        Result compact = TabLayout.compute(foule(79, 75).stream().map(x -> new Row("Ab", x.job())).toList(), null, 79,
                DAY, CLOCK, 427, 240, W);
        assertEquals(Mode.COMPACT, compact.mode());
        assertTrue(compact.colWidth() >= 15 + W.width("Survivant"), "l'en-tête tient dans la colonne compacte");
    }

    @Test
    void sansAutreJoueurLaListeNAQueLEnTeteEtLePied() {
        Result r = TabLayout.compute(List.of(), new Mayor("Maire_Elsa", "Citoyen"), 1, DAY, CLOCK, 960, 540, W);
        assertEquals(-1, r.colsY());
        assertEquals(22 + 1 + 29 + 19, r.height());
    }

    @Test
    void auPlusEtroitLeMetierDuMaireEstTronque() {
        Result r = TabLayout.compute(foule(79, 75), new Mayor("WWWWWWWWWWWWWWWW", "Responsable du port de pêche"), 80, DAY,
                CLOCK, 320, 240, W);
        assertTrue(r.mayorJob().endsWith("…"), r.mayorJob());
        assertTrue(r.innerWidth() <= 320 - 32, "largeur intérieure " + r.innerWidth());
        assertTrue(r.shortForms());
    }
}
