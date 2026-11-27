package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.client.web.WebScreen.Rect;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * L'écran de repli tient dans son cadre de l'interface la plus petite (320 × 240) aux plus grandes : clavier sous le
 * titre et les points, Réglages avec un libellé au-dessus de chaque champ de code, rien qui déborde ni se chevauche.
 */
class PhoneFallbackLayoutTest {

    private static final int LH = 9; // hauteur de ligne de la police de Minecraft

    private static Rect inner(int guiW, int guiH) {
        return PhoneFallbackLayout.inner(PhoneFrameLayout.fit(guiW, guiH), LH);
    }

    private static void dedans(Rect r, Rect zone, String quoi) {
        assertTrue(r.x() >= zone.x() && r.y() >= zone.y() && r.x() + r.width() <= zone.x() + zone.width()
                && r.y() + r.height() <= zone.y() + zone.height(), quoi + " " + r + " hors de " + zone);
    }

    private static boolean chevauche(Rect a, Rect b) {
        return a.x() < b.x() + b.width() && b.x() < a.x() + a.width() && a.y() < b.y() + b.height()
                && b.y() < a.y() + a.height();
    }

    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080", "320, 480"})
    void lesReglagesTiennentAvecLeursLibelles(int w, int h) {
        Rect zone = inner(w, h);
        PhoneFallbackLayout.Settings s = PhoneFallbackLayout.settings(zone, LH);
        Map<String, Rect> rows = s.rows();
        assertEquals(PhoneFallbackLayout.SETTINGS_ROWS, List.copyOf(rows.keySet()));
        assertEquals("sim", PhoneFallbackLayout.SETTINGS_ROWS.get(0), "« Carte SIM » en tête des Réglages");
        int bas = s.textTop() + 2 * (LH + 1); // propriétaire et message au-dessus des lignes
        for (String nom : PhoneFallbackLayout.SETTINGS_ROWS) {
            Rect r = rows.get(nom);
            dedans(r, zone, nom);
            assertTrue(r.height() >= PhoneFallbackLayout.MIN_ROW, nom + " trop bas : " + r);
            if (PhoneFallbackLayout.LABELLED.contains(nom)) {
                int haut = s.labelTops().get(nom);
                assertTrue(haut >= bas, "libellé de " + nom + " sur la ligne précédente");
                assertTrue(haut + LH <= r.y(), "libellé de " + nom + " sur son champ");
            } else {
                assertFalse(s.labelTops().containsKey(nom), nom + " n'a pas de libellé");
            }
            assertTrue(r.y() >= bas, nom + " chevauche la ligne précédente");
            bas = r.y() + r.height();
        }
        assertEquals(PhoneFallbackLayout.LABELLED, List.copyOf(s.labelTops().keySet()));
    }

    /**
     * L'accueil (tâche 13b) : l'appli « Antenne » du fondateur au-dessus de « Réglages », à la même place qu'elle soit
     * vive ou grisée ; grisée exactement quand le serveur ne l'ouvrirait pas ; absente pour les autres.
     */
    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080", "320, 480"})
    void lAccueilMontreLAppliViveOuGrisee(int w, int h) {
        Rect zone = inner(w, h);
        PhoneFallbackLayout.Home vive = PhoneFallbackLayout.home(zone, true, true);
        PhoneFallbackLayout.Home grisee = PhoneFallbackLayout.home(zone, true, false);
        PhoneFallbackLayout.Home autre = PhoneFallbackLayout.home(zone, false, false);
        assertTrue(vive.antenna() != null && grisee.antenna() != null, "le fondateur voit l'appli, vive ou grisée");
        assertFalse(vive.antennaDimmed(), "ouvrable : vive");
        assertTrue(grisee.antennaDimmed(), "pas ouvrable : grisée");
        assertEquals(vive.antenna(), grisee.antenna(), "grisée à la même place");
        assertTrue(autre.antenna() == null && !autre.antennaDimmed(), "pas fondateur : pas d'appli");
        for (PhoneFallbackLayout.Home home : List.of(vive, grisee, autre)) {
            assertEquals(vive.settings(), home.settings(), "« Réglages » ne bouge pas");
            dedans(home.settings(), zone, "réglages");
        }
        Rect appli = vive.antenna();
        dedans(appli, zone, "appli Antenne");
        assertTrue(appli.height() >= PhoneFallbackLayout.MIN_ROW, "appli trop basse : " + appli);
        assertTrue(appli.y() + appli.height() < vive.settings().y(), "appli sur « Réglages » : " + appli);
    }

    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080"})
    void leClavierTientSousLeTitre(int w, int h) {
        Rect zone = inner(w, h);
        List<Rect> touches = PhoneFallbackLayout.keypad(zone);
        assertEquals(11, touches.size());
        // Le texte de l'écran du code avec les lignes d'un blocage, tel que drawCode le dessine.
        int texte = PhoneFallbackLayout.codeText(zone, LH, PhoneFallbackLayout.LOCKOUT_LINES).bottom();
        for (int i = 0; i < touches.size(); i++) {
            Rect t = touches.get(i);
            dedans(t, zone, "touche " + i);
            assertTrue(t.y() >= texte, "touche " + i + " sur le texte du code : " + t);
            assertTrue(t.height() >= PhoneFallbackLayout.MIN_ROW, "touche " + i + " trop basse");
            for (int j = i + 1; j < touches.size(); j++) {
                assertFalse(chevauche(t, touches.get(j)), "touches " + i + " et " + j);
            }
        }
        // Effacer sous le 1, le 0 sous le 2.
        assertEquals(touches.get(0).x(), touches.get(9).x());
        assertEquals(touches.get(1).x(), touches.get(10).x());
    }

    /**
     * Au choix du code (téléphone neuf), le bouton « Fond suivant » se glisse entre le message (une ligne : pas de
     * blocage possible avant le premier code) et le clavier, sans toucher ni l'un ni l'autre.
     */
    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080", "320, 480"})
    void leFondDuChoixDuCodeTientEntreLeTexteEtLeClavier(int w, int h) {
        Rect zone = inner(w, h);
        Rect fond = PhoneFallbackLayout.setupWallpaper(zone, LH);
        dedans(fond, zone, "fond suivant");
        assertTrue(fond.height() >= PhoneFallbackLayout.MIN_ROW, "fond suivant trop bas : " + fond);
        assertTrue(fond.height() <= PhoneFallbackLayout.MAX_ROW, "fond suivant trop haut : " + fond);
        // Le texte du choix du code (une ligne de message), tel que drawCode le dessine.
        int texte = PhoneFallbackLayout.codeText(zone, LH, 1).bottom();
        assertTrue(fond.y() >= texte, "fond suivant sur le texte du code : " + fond + ", texte jusqu'à " + texte);
        for (Rect t : PhoneFallbackLayout.keypad(zone)) {
            assertFalse(chevauche(fond, t), "fond suivant sur la touche " + t);
            assertTrue(fond.y() + fond.height() < t.y(), "fond suivant sous le haut du clavier : " + fond + " / " + t);
        }
    }

    /**
     * Le texte de l'écran du code ({@code drawCode} dessine aux positions de {@code codeText}) : titre, points, puis
     * chaque ligne de message entière (hauteur de police) avant {@code bottom}, sans chevauchement ; le clavier et le
     * bouton « Fond suivant » commencent sous {@code bottom}. Une ligne oubliée dans {@code bottom} fait passer le bouton
     * ou le clavier sur le texte.
     */
    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080", "320, 480"})
    void leTexteDuCodeSArreteAvantLeBoutonEtLeClavier(int w, int h) {
        Rect zone = inner(w, h);
        for (int lignes = 1; lignes <= PhoneFallbackLayout.LOCKOUT_LINES; lignes++) {
            PhoneFallbackLayout.CodeText t = PhoneFallbackLayout.codeText(zone, LH, lignes);
            assertTrue(t.titleY() >= zone.y(), "titre hors de la zone");
            assertTrue(t.dotsY() >= t.titleY() + LH, "points sur le titre");
            assertTrue(t.dot() >= 3 && t.dot() <= 6, "points de " + t.dot() + " px");
            assertTrue(t.messageY() >= t.dotsY() + t.dot(), "message sur les points");
            assertTrue(t.lineStep() >= LH, "lignes de message qui se chevauchent : pas de " + t.lineStep());
            int derniere = t.messageY() + (lignes - 1) * t.lineStep() + LH;
            assertTrue(t.bottom() >= derniere, lignes + " ligne(s) : texte jusqu'à " + derniere + ", bas annoncé "
                    + t.bottom());
        }
        assertEquals(3, PhoneFallbackLayout.LOCKOUT_LINES, "« bloqué » et le décompte sur deux lignes au plus");
        assertTrue(PhoneFallbackLayout.keypad(zone).get(0).y() >= PhoneFallbackLayout.codeText(zone, LH, 3).bottom(),
                "clavier sur les lignes du blocage");
        assertTrue(PhoneFallbackLayout.setupWallpaper(zone, LH).y() >= PhoneFallbackLayout.codeText(zone, LH, 1).bottom(),
                "« Fond suivant » sur la ligne de message");
    }

    /** Les lignes de la feuille « Carte SIM » au plus long : 41 cartes, « Demander une carte », 16 opérateurs. */
    private static List<String> lignesSim(int cartes, int operateurs) {
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < cartes; i++) {
            ids.add("sim-choice-" + i);
        }
        ids.add("sim-request");
        for (int i = 0; i < operateurs; i++) {
            ids.add(String.format("request-%012x", i));
        }
        return ids;
    }

    /**
     * La feuille « Carte SIM » de l'écran de repli tient dans la zone de 320 × 240 à 1920 × 1080 avec 41 cartes et 16
     * opérateurs : « Retirer » en haut et « Retour » en bas restent en place, la liste défile entre eux ; à chaque cran
     * de défilement, seules des lignes entières et sans chevauchement sont montrées, et chaque ligne se montre à un cran
     * au moins.
     */
    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080", "320, 480"})
    void laFeuilleSimTientEtDefile(int w, int h) {
        Rect zone = inner(w, h);
        List<String> ids = lignesSim(41, 16);
        PhoneFallbackLayout.SimSheet haut = PhoneFallbackLayout.simSheet(zone, LH, ids, 0);
        dedans(haut.remove(), zone, "retirer");
        dedans(haut.back(), zone, "retour");
        dedans(haut.list(), zone, "liste");
        assertTrue(haut.remove().y() >= haut.textTop() + PhoneFallbackLayout.SIM_TEXT_LINES * (LH + 1),
                "retirer sur le texte de la ligne");
        assertFalse(chevauche(haut.remove(), haut.list()), "retirer sur la liste");
        assertFalse(chevauche(haut.back(), haut.list()), "retour sur la liste");
        assertTrue(haut.remove().height() >= PhoneFallbackLayout.MIN_ROW && haut.back().height()
                >= PhoneFallbackLayout.MIN_ROW, "boutons trop bas");
        assertTrue(haut.maxScroll() > 0, "57 lignes doivent défiler à " + w + " x " + h);
        java.util.Set<String> vues = new java.util.HashSet<>();
        int pas = haut.rowStep();
        assertTrue(pas > 0, "pas de défilement nul");
        for (int defile = 0; defile <= haut.maxScroll() + pas; defile += pas) {
            PhoneFallbackLayout.SimSheet s = PhoneFallbackLayout.simSheet(zone, LH, ids, defile);
            assertTrue(s.scroll() >= 0 && s.scroll() <= s.maxScroll(), "défilement hors bornes : " + s.scroll());
            assertFalse(s.rows().isEmpty(), "aucune ligne montrée au cran " + defile);
            List<Rect> montrees = List.copyOf(s.rows().values());
            for (int i = 0; i < montrees.size(); i++) {
                Rect r = montrees.get(i);
                dedans(r, s.list(), "ligne au cran " + defile);
                assertTrue(r.height() >= PhoneFallbackLayout.MIN_ROW, "ligne trop basse : " + r);
                for (int j = i + 1; j < montrees.size(); j++) {
                    assertFalse(chevauche(r, montrees.get(j)), "lignes qui se chevauchent au cran " + defile);
                }
            }
            vues.addAll(s.rows().keySet());
        }
        assertEquals(java.util.Set.copyOf(ids), vues, "lignes jamais montrées");
        PhoneFallbackLayout.SimSheet bas = PhoneFallbackLayout.simSheet(zone, LH, ids, Integer.MAX_VALUE);
        assertEquals(bas.maxScroll(), bas.scroll());
        assertTrue(bas.rows().containsKey(ids.get(ids.size() - 1)), "la dernière ligne au bas de la liste");
        PhoneFallbackLayout.SimSheet negatif = PhoneFallbackLayout.simSheet(zone, LH, ids, -50);
        assertEquals(0, negatif.scroll());
        assertTrue(negatif.rows().containsKey(ids.get(0)));
    }

    @Test
    void uneFeuilleSimCourteNeDefilePas() {
        PhoneFallbackLayout.SimSheet s = PhoneFallbackLayout.simSheet(inner(640, 360), LH, lignesSim(1, 1), 30);
        assertEquals(0, s.maxScroll());
        assertEquals(0, s.scroll());
        assertEquals(3, s.rows().size());
        assertEquals(null, s.track(), "rien à défiler : pas de barre");
        for (Rect r : s.rows().values()) {
            assertEquals(s.list().width(), r.width(), "sans barre, la ligne prend toute la largeur");
        }
    }

    /**
     * Revue de la tâche 12 (I2) : une liste qui défile montre une barre de défilement fine à droite (aucune ligne cachée
     * sans indice), à toutes les tailles ; la barre ne recouvre aucune ligne, son curseur est en haut au départ, en bas
     * à la fin, et descend avec le défilement. Cas de la démo : quatre cartes et trois opérateurs à 320 × 240.
     */
    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080", "320, 480"})
    void uneListeQuiDefileMontreSaBarre(int w, int h) {
        Rect zone = inner(w, h);
        for (List<String> ids : List.of(lignesSim(41, 16), lignesSim(4, 3))) {
            PhoneFallbackLayout.SimSheet haut = PhoneFallbackLayout.simSheet(zone, LH, ids, 0);
            if (haut.maxScroll() == 0) {
                assertEquals(null, haut.track(), "rien à défiler : pas de barre");
                continue;
            }
            Rect piste = haut.track();
            assertTrue(piste != null && haut.thumb() != null, "la liste défile sans barre à " + w + " x " + h);
            // Relecture de la tâche 12 : la barre vit dans la marge droite, dans le cadre, hors de la liste ; les lignes
            // gardent toute la largeur de la liste (les numéros entiers à 320 × 240).
            Rect cadre = PhoneFrameLayout.fit(w, h);
            dedans(piste, cadre, "barre");
            assertTrue(piste.x() >= haut.list().x() + haut.list().width() + 1, "barre hors de la liste, 1 px d'écart : "
                    + piste + " / " + haut.list());
            assertEquals(haut.list().y(), piste.y(), "barre en face de la liste");
            for (Rect r : haut.rows().values()) {
                assertEquals(haut.list().width(), r.width(), "avec barre, la ligne garde toute la largeur de la liste");
            }
            assertTrue(piste.width() >= 2 && piste.width() <= 4, "barre fine : " + piste);
            assertEquals(haut.list().height(), piste.height(), "barre de toute la hauteur de la liste");
            int bas = Integer.MAX_VALUE;
            int precedent = -1;
            for (int defile = 0; defile <= haut.maxScroll() + haut.rowStep(); defile += haut.rowStep()) {
                PhoneFallbackLayout.SimSheet s = PhoneFallbackLayout.simSheet(zone, LH, ids, defile);
                dedans(s.thumb(), s.track(), "curseur");
                assertTrue(s.thumb().height() >= 6, "curseur trop petit : " + s.thumb());
                assertTrue(s.thumb().y() >= precedent, "le curseur remonte au cran " + defile);
                precedent = s.thumb().y();
                for (Rect r : s.rows().values()) {
                    assertFalse(chevauche(r, s.track()), "une ligne sous la barre au cran " + defile);
                }
            }
            assertEquals(piste.y(), haut.thumb().y(), "curseur en haut au départ");
            PhoneFallbackLayout.SimSheet fin = PhoneFallbackLayout.simSheet(zone, LH, ids, Integer.MAX_VALUE);
            assertEquals(fin.track().y() + fin.track().height(), fin.thumb().y() + fin.thumb().height(),
                    "curseur en bas à la fin");
        }
        // À 320 × 240, la démo (quatre cartes, « Demander », trois opérateurs) défile : la barre l'annonce.
        if (w == 320 && h == 240) {
            assertTrue(PhoneFallbackLayout.simSheet(zone, LH, lignesSim(4, 3), 0).track() != null,
                    "huit lignes à 320 x 240 : la barre doit se voir");
        }
    }

    /**
     * Un nom d'opérateur de 24 caractères ne tient pas dans une ligne de la feuille à 320 × 240 : il est coupé et finit
     * par « … », sans dépasser la place du texte ; un nom court reste entier.
     */
    @Test
    void unNomDOperateurDe24CaracteresEstCoupe() {
        java.util.function.ToIntFunction<String> largeur = s -> 6 * s.codePointCount(0, s.length());
        String nom = "Operateur-De-Lost-Island";
        assertEquals(24, nom.length());
        PhoneFallbackLayout.SimSheet s = PhoneFallbackLayout.simSheet(inner(320, 240), LH, lignesSim(0, 1), 0);
        int place = PhoneFallbackLayout.textRoom(s.list().width());
        assertTrue(largeur.applyAsInt(nom) > place, "le nom devait dépasser : place " + place);
        String coupe = PhoneFallbackLayout.ellipsize(nom, place, largeur);
        assertTrue(coupe.endsWith("…"), coupe);
        assertTrue(largeur.applyAsInt(coupe) <= place, coupe + " dépasse " + place);
        assertTrue(nom.startsWith(coupe.substring(0, coupe.length() - 1)), coupe);
        assertTrue(coupe.length() > 2, "coupé trop court : " + coupe);
        assertEquals("Lagon", PhoneFallbackLayout.ellipsize("Lagon", place, largeur));
        assertEquals("", PhoneFallbackLayout.ellipsize("Lagon", 3, largeur), "pas même la place de « … »");
    }

    /**
     * La barre d'état du repli : rétrécie pour tenir, jamais sous {@code MIN_STATUS_SCALE} ; au-delà, le nom de
     * l'opérateur est coupé par « … » (revue de la tâche 8, M1).
     */
    @Test
    void laBarreDEtatNeDescendPasSousSonPlancher() {
        java.util.function.ToIntFunction<String> largeur = s -> 6 * s.codePointCount(0, s.length());
        int place = 88;
        PhoneFallbackLayout.StatusLine longue = PhoneFallbackLayout.statusLine("21:57  ", "Coopérative du Lagon Sud",
                place, largeur);
        assertTrue(longue.scale() >= PhoneFallbackLayout.MIN_STATUS_SCALE, "échelle " + longue.scale());
        assertTrue(largeur.applyAsInt(longue.text()) * longue.scale() <= place + 0.01, longue.toString());
        assertTrue(longue.text().startsWith("21:57  Coop") && longue.text().endsWith("…"), longue.text());
        PhoneFallbackLayout.StatusLine courte = PhoneFallbackLayout.statusLine("21:57  ", "Mairie", place, largeur);
        assertEquals("21:57  Mairie", courte.text());
        assertEquals(1f, courte.scale());
        PhoneFallbackLayout.StatusLine moyenne = PhoneFallbackLayout.statusLine("21:57  ", "Lagon du Nord", 100,
                largeur);
        assertEquals("21:57  Lagon du Nord", moyenne.text(), "rétrécie sans coupe tant que le plancher suffit");
        assertTrue(moyenne.scale() < 1f && moyenne.scale() >= PhoneFallbackLayout.MIN_STATUS_SCALE);
    }

    /**
     * Un message plus long que la ligne passe à la ligne entre les mots, à l'échelle 1 si deux lignes suffisent, sinon
     * au plancher 0,75 ; jamais plus de deux lignes, jamais sous le plancher ; ce qui ne tient pas finit par « … »
     * (revue de la tâche 8, tour 2).
     */
    @Test
    void unMessageLongPasseALaLigne() {
        java.util.function.ToIntFunction<String> largeur = s -> 6 * s.codePointCount(0, s.length());
        PhoneFallbackLayout.Wrapped court = PhoneFallbackLayout.wrap("Carte SIM retirée.", 120, largeur, 2);
        assertEquals(List.of("Carte SIM retirée."), court.lines());
        assertEquals(1f, court.scale());
        // 36 caractères : deux lignes de 15 caractères au plus à l'échelle 1 (90 px).
        PhoneFallbackLayout.Wrapped deux = PhoneFallbackLayout.wrap("Seul le propriétaire peut le faire.", 90, largeur,
                2);
        assertEquals(List.of("Seul le", "propriétaire", "peut le faire."), PhoneFallbackLayout.wrap(
                "Seul le propriétaire peut le faire.", 90, largeur, 3).lines(), "passage glouton entre les mots");
        assertEquals(2, deux.lines().size(), deux.toString());
        assertEquals(PhoneFallbackLayout.MIN_TEXT_SCALE, deux.scale(), "trois lignes à l'échelle 1 : le plancher");
        assertFalse(deux.cut());
        for (String l : deux.lines()) {
            assertTrue(largeur.applyAsInt(l) * deux.scale() <= 90, l);
        }
        // Trop long pour deux lignes même au plancher : coupé par « … » à la fin de la seconde.
        PhoneFallbackLayout.Wrapped trop = PhoneFallbackLayout.wrap(
                "La file de demandes de cet opérateur est pleine. Réessayez plus tard.", 90, largeur, 2);
        assertTrue(trop.cut());
        assertEquals(2, trop.lines().size());
        assertTrue(trop.lines().get(1).endsWith("…"), trop.toString());
        assertTrue(trop.scale() >= PhoneFallbackLayout.MIN_TEXT_SCALE);
        for (String l : trop.lines()) {
            assertTrue(largeur.applyAsInt(l) * trop.scale() <= 90 + 0.01, l);
        }
    }

    /** La barre d'état lâche l'heure, puis des barres toutes vides, avant de couper un texte d'état. */
    @Test
    void laBarreDEtatLacheLHeurePuisLesBarresVides() {
        java.util.function.ToIntFunction<String> largeur = s -> 6 * s.codePointCount(0, s.length());
        PhoneFallbackLayout.Status large = PhoneFallbackLayout.status("21:57  ", "Mairie", 14, false, 200, largeur);
        assertEquals("21:57  Mairie", large.line().text());
        assertTrue(large.bars());
        PhoneFallbackLayout.Status sansHeure = PhoneFallbackLayout.status("21:57  ", "Pas de SIM", 0, false, 62,
                largeur);
        assertEquals("Pas de SIM", sansHeure.line().text(), "l'heure cède sa place");
        assertFalse(sansHeure.line().cut());
        PhoneFallbackLayout.Status sansBarres = PhoneFallbackLayout.status("21:57  ", "Pas de service", 14, true, 64,
                largeur);
        assertFalse(sansBarres.bars(), "quatre barres vides cèdent leur place");
        assertEquals("Pas de service", sansBarres.line().text());
        PhoneFallbackLayout.Status avecBarres = PhoneFallbackLayout.status("", "Coopérative du Lagon Sud", 14, false,
                62, largeur);
        assertTrue(avecBarres.bars(), "des barres pleines restent");
        assertTrue(avecBarres.line().cut() && avecBarres.line().text().endsWith("…"));
        assertEquals(PhoneFallbackLayout.MIN_STATUS_SCALE, avecBarres.line().scale());
    }

    /** Seules les lignes qui portent une donnée libre (carte, opérateur) sont coupées par « … » ; un texte fixe non. */
    @Test
    void seulesLesDonneesLibresSontCoupees() {
        assertTrue(PhoneFallbackLayout.cutWithEllipsis("sim-choice-12"));
        assertTrue(PhoneFallbackLayout.cutWithEllipsis("request-3f2a9c1b0d4e"));
        assertFalse(PhoneFallbackLayout.cutWithEllipsis("request-none"));
        assertFalse(PhoneFallbackLayout.cutWithEllipsis("sim-none"));
        assertFalse(PhoneFallbackLayout.cutWithEllipsis("sim-request"));
        assertFalse(PhoneFallbackLayout.cutWithEllipsis("sim-remove"));
        assertFalse(PhoneFallbackLayout.cutWithEllipsis("back"));
    }

    @Test
    void effacerDevientUneFlecheQuandSonLibelleNeTientPlus() {
        // « Effacer » fait 40 px dans la police de Minecraft ; touche de 28 px à 320 x 240 : 24 px de place.
        assertTrue(PhoneFallbackLayout.clearAsSymbol(40, 28));
        assertFalse(PhoneFallbackLayout.clearAsSymbol(40, 44), "assez de place : le mot, à peine rétréci");
        assertFalse(PhoneFallbackLayout.clearAsSymbol(30, 34), "tient à l'échelle 1");
        int petite = PhoneFallbackLayout.keypad(inner(320, 240)).get(9).width();
        assertTrue(PhoneFallbackLayout.clearAsSymbol(40, petite), "la touche à 320 x 240 fait " + petite + " px");
    }

    @Test
    void echelleDAjustement() {
        assertEquals(1f, PhoneFallbackLayout.fit(50, 86, 1f));
        assertEquals(0.5f, PhoneFallbackLayout.fit(172, 86, 1f));
        assertEquals(1f, PhoneFallbackLayout.fit(0, 86, 1f));
        assertEquals(82, PhoneFallbackLayout.textRoom(86));
    }
}
