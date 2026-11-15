package dev.oneforallsurvival.economy.quest.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.Layout;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.Rect;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.RowLayout;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.RowView;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.SectionLayout;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.SectionView;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.Text;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.View;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * La mise en page de l'écran des quêtes, avec une police factice de 6 pixels par caractère (plus large que la vraie pour
 * presque tous les caractères) : le contenu le plus long tient dans 320 × 240, un titre trop long est coupé par « … »,
 * le contenu le plus court reste aligné sur les mêmes colonnes.
 */
class QuestScreenLayoutTest {

    private static final ToIntFunction<String> SIX = s -> 6 * s.length();
    private static final String TITRE_64 = "Rassembler les vivres du grand banquet de la fête du solstice !!";
    private static final List<String> CINQ_COUPURES = List.of("1 rubis", "3 émeraudes", "9 or", "5 argent", "7 cuivres");

    private static RowView longue(String titre, String bouton) {
        return new RowView(titre, CINQ_COUPURES, "9999 / 9999", bouton, null);
    }

    /** Titres de 64 caractères, récompense à cinq coupures, 3 + 5 lignes, attente pleine, onglet des événements. */
    private static View laPlusLongue() {
        List<RowView> jour = new ArrayList<>();
        List<RowView> semaine = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            jour.add(longue(TITRE_64, i == 0 ? "Réclamer" : "Relancer"));
        }
        for (int i = 0; i < 5; i++) {
            semaine.add(longue(TITRE_64, i == 4 ? null : "Réclamer"));
        }
        semaine.set(4, new RowView(TITRE_64, CINQ_COUPURES, "9999 / 9999", null, "Réclamée"));
        return new View("Quêtes", "Événements", "Plafond de la semaine atteint : à réclamer après lundi (999 999 999)",
                "Réclamer l'attente (8)",
                List.of(new SectionView("Aujourd'hui", "5 relances possibles", jour, "Aucune quête"),
                        new SectionView("Cette semaine", "", semaine, "Aucune quête")),
                "Journalières dans 23 h 59 — hebdomadaires dans 23 h 59");
    }

    private static List<Rect> rectangles(Layout l) {
        List<Rect> out = new ArrayList<>(l.tabs());
        l.tabLabels().forEach(t -> out.add(t.box()));
        out.add(l.cap().box());
        out.add(l.footer().box());
        out.add(l.list());
        if (l.pendingButton() != null) {
            out.add(l.pendingButton());
            out.add(l.pendingLabel().box());
        }
        for (SectionLayout s : l.sections()) {
            out.add(s.header().box());
            out.add(s.headerRight().box());
            for (RowLayout r : s.rows()) {
                out.addAll(List.of(r.box(), r.icon(), r.title().box(), r.bar(), r.progress().box(), r.reward().box()));
                if (r.button() != null) {
                    out.add(r.button());
                    out.add(r.buttonLabel().box());
                }
                if (r.status() != null) {
                    out.add(r.status().box());
                }
            }
        }
        return out;
    }

    private static List<Text> textes(Layout l) {
        List<Text> out = new ArrayList<>(l.tabLabels());
        out.add(l.cap());
        out.add(l.footer());
        if (l.pendingLabel() != null) {
            out.add(l.pendingLabel());
        }
        for (SectionLayout s : l.sections()) {
            out.add(s.header());
            out.add(s.headerRight());
            for (RowLayout r : s.rows()) {
                out.addAll(List.of(r.title(), r.progress(), r.reward()));
                if (r.buttonLabel() != null) {
                    out.add(r.buttonLabel());
                }
                if (r.status() != null) {
                    out.add(r.status());
                }
            }
        }
        return out;
    }

    private static void toutTient(Layout l, int largeur, int hauteur) {
        Rect ecran = new Rect(0, 0, largeur, hauteur);
        assertTrue(ecran.contains(l.frame()), "cadre hors de l'écran : " + l.frame());
        for (Rect r : rectangles(l)) {
            assertTrue(l.frame().contains(r), "hors du cadre : " + r + " dans " + l.frame());
        }
        for (Text t : textes(l)) {
            assertTrue(t.width() <= t.room(), "texte plus large que sa place : " + t);
            assertEquals(SIX.applyAsInt(t.shown()), t.width(), "largeur mal mesurée : " + t);
        }
        for (SectionLayout s : l.sections()) {
            assertFalse(s.header().box().intersects(s.headerRight().box()) && s.headerRight().width() > 0,
                    "en-tête mêlé : " + s.header() + " / " + s.headerRight());
            for (RowLayout r : s.rows()) {
                assertTrue(l.list().contains(r.box()), "ligne hors de la liste : " + r.box());
                assertFalse(r.title().box().intersects(r.icon()), "titre sur l'icône");
                assertFalse(r.reward().box().intersects(r.progress().box()), "récompense sur la progression : " + r);
                assertFalse(r.progress().box().intersects(r.bar()), "progression sur la barre");
                if (r.button() != null) {
                    assertFalse(r.title().area().intersects(r.button()), "titre sous le bouton");
                    assertFalse(r.reward().box().intersects(r.button()), "récompense sous le bouton");
                    assertTrue(r.button().contains(r.buttonLabel().box()), "libellé hors du bouton");
                }
            }
        }
        // Les blocs du haut et du bas ne chevauchent pas la liste.
        assertFalse(l.cap().box().intersects(l.list()), "plafond sur la liste");
        assertFalse(l.footer().box().intersects(l.list()), "bas sur la liste");
        for (Rect tab : l.tabs()) {
            assertFalse(tab.intersects(l.cap().box()), "onglet sur le plafond");
            if (l.pendingButton() != null) {
                assertFalse(tab.intersects(l.pendingButton()), "onglet sur l'attente");
            }
        }
    }

    @Test
    void toutTientEn320Par240() {
        Layout l = QuestScreenLayout.of(320, 240, SIX, laPlusLongue());
        toutTient(l, 320, 240);
        assertEquals(0, l.scrollMax(), "3 + 5 lignes doivent tenir sans défiler : " + l.contentHeight());
        assertEquals(2, l.tabs().size());
        assertNotNull(l.pendingButton());
        // La récompense à cinq coupures perd ses plus petites coupures, jamais une coupure à moitié.
        Text recompense = l.sections().get(0).rows().get(0).reward();
        assertTrue(recompense.cut() && recompense.shown().endsWith(" …") && recompense.shown().startsWith("1 rubis"),
                "récompense : " + recompense);
        assertEquals(String.join(" ", CINQ_COUPURES), recompense.full());
        // Et dans une grande interface : même cadre, centré.
        Layout grand = QuestScreenLayout.of(640, 360, SIX, laPlusLongue());
        toutTient(grand, 640, 360);
        assertEquals(new Rect(170, 67, 300, 226), grand.frame());
    }

    @Test
    void lesEvenementsTiennentAussi() {
        List<RowView> rows = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            rows.add(longue(TITRE_64, "Réclamer"));
        }
        View v = new View("Quêtes", "Événements", "999 999 / 1 000 000 cuivres cette semaine", "Réclamer l'attente (8)",
                List.of(new SectionView("Événements en cours", "", rows, null)), "Journalières dans 5 h 12 — hebdomadaires lundi");
        Layout l = QuestScreenLayout.of(320, 240, SIX, v);
        toutTient(l, 320, 240);
        assertEquals(0, l.scrollMax());
    }

    @Test
    void vingtLignesDefilentSansSortirDeLaLargeur() {
        List<RowView> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(longue(TITRE_64, "Réclamer"));
        }
        View v = new View("Quêtes", null, "0 / 1 000 cuivres cette semaine", null,
                List.of(new SectionView("Aujourd'hui", "", rows, null), new SectionView("Cette semaine", "", rows, null)),
                "bas");
        Layout l = QuestScreenLayout.of(320, 240, SIX, v);
        assertEquals(2 * QuestScreenLayout.HEADER_H + 20 * QuestScreenLayout.ROW_H - QuestScreenLayout.LIST_H, l.scrollMax());
        for (SectionLayout s : l.sections()) {
            for (RowLayout r : s.rows()) {
                assertTrue(r.box().x() == l.list().x() && r.box().w() == l.list().w(), "ligne hors de la largeur");
            }
        }
    }

    @Test
    void unTitreTropLongEstCoupe() {
        Layout l = QuestScreenLayout.of(320, 240, SIX, laPlusLongue());
        Text titre = l.sections().get(1).rows().get(0).title();
        assertTrue(titre.cut(), "titre de 64 caractères non coupé : " + titre);
        assertTrue(titre.shown().endsWith(QuestScreenLayout.ELLIPSIS), "sans « … » : " + titre.shown());
        assertEquals(TITRE_64, titre.full(), "le texte complet (bulle) est perdu");
        assertTrue(titre.width() <= titre.room());
        String sans = titre.shown().substring(0, titre.shown().length() - 1);
        assertTrue(TITRE_64.startsWith(sans), "début du titre changé : " + titre.shown());
        // Un caractère de plus ne tiendrait pas : la coupe prend toute la place.
        String plus = TITRE_64.substring(0, sans.length() + 1).stripTrailing() + QuestScreenLayout.ELLIPSIS;
        assertTrue(SIX.applyAsInt(plus) > titre.room() || plus.equals(titre.shown()), "coupé trop court : " + titre.shown());
        // Le plafond trop long aussi, avec le même signe.
        Layout court = QuestScreenLayout.of(320, 240, SIX, new View("Quêtes", null, "x".repeat(80), null, List.of(), "bas"));
        assertTrue(court.cap().cut() && court.cap().shown().endsWith(QuestScreenLayout.ELLIPSIS));
    }

    @Test
    void leContenuLePlusCourtResteAligne() {
        View v = new View("Quêtes", null, "0 / 1 000 cuivres cette semaine", null,
                List.of(new SectionView("Aujourd'hui", "", List.of(new RowView("Pêcher 6 poissons", List.of("1 or"), "0 / 6",
                        null, null)), "Aucune quête"), new SectionView("Cette semaine", "", List.of(), "Aucune quête")),
                "Journalières dans 3 min — hebdomadaires lundi");
        Layout l = QuestScreenLayout.of(320, 240, SIX, v);
        toutTient(l, 320, 240);
        Layout long_ = QuestScreenLayout.of(320, 240, SIX, laPlusLongue());
        assertEquals(1, l.tabs().size(), "onglet des événements sans événement");
        assertNull(l.pendingButton(), "bouton de l'attente sans attente");
        RowLayout seule = l.sections().get(0).rows().get(0);
        RowLayout pire = long_.sections().get(0).rows().get(0);
        assertFalse(seule.title().cut() || seule.reward().cut(), "contenu court coupé");
        // Mêmes colonnes et mêmes rangées que le contenu le plus long.
        assertEquals(pire.box(), seule.box());
        assertEquals(pire.icon(), seule.icon());
        assertEquals(pire.title().x(), seule.title().x());
        assertEquals(pire.bar(), seule.bar());
        assertEquals(pire.reward().x() + pire.reward().width(), seule.reward().x() + seule.reward().width(),
                "la récompense n'est plus alignée à droite");
        assertEquals(long_.cap().y(), l.cap().y());
        assertEquals(long_.footer().y(), l.footer().y());
        assertEquals(long_.frame(), l.frame());
        assertEquals(l.sections().get(0).header().x(), seule.title().x() - 22, "titre décalé de l'en-tête");
        // La section vide dit qu'elle est vide.
        assertNotNull(l.sections().get(1).empty());
        assertEquals("Aucune quête", l.sections().get(1).empty().shown());
    }
}
