package dev.oneforallsurvival.grid.supplier.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.Field;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.GapLine;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.GapView;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.Layout;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.Rect;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.RowLayout;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.RowView;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.Text;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.View;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * La mise en page du panneau du poste, avec une police factice de 6 pixels par caractère (plus large que la vraie pour
 * presque tous les caractères) : le contenu le plus long tient dans 320 × 240, un nom trop long est coupé par « … » (le
 * texte complet reste pour la bulle), un poste neuf ne montre que le champ du nom.
 */
class PowerPanelLayoutTest {

    private static final ToIntFunction<String> SIX = s -> 6 * s.length();
    private static final String NOM_24 = "Compagnie des Eaux Vives";
    private static final String NOM_16 = "Abcdefghijklmnop";
    private static final List<String> CINQ_COUPURES = List.of("1 rubis", "3 émeraudes", "9 or", "5 argent", "7 cuivres");

    /** Nom de 24 caractères, 64 clients aux noms de 16 caractères, montants à cinq coupures, écart en alerte. */
    private static View leContenuLePlusLong() {
        List<RowView> clients = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            List<String> boutons = i % 3 == 0 ? List.of("Accepter", "Refuser")
                    : i % 3 == 1 ? List.of("Rétablir", "Détacher") : List.of("Couper", "Détacher");
            clients.add(new RowView(NOM_16 + " (" + NOM_16 + ")", "Coupé, résiliation en cours",
                    "Auj. 99 999 999 kFE · hier 99 999 999 kFE", "Impayés :", CINQ_COUPURES, boutons));
        }
        String montant = String.join(" ", CINQ_COUPURES);
        return new View(NOM_24, NOM_24, "Caisse", "Nom du fournisseur (1 à 24 caractères)", "Fonder",
                "Prix : " + montant + " les 100 000 FE · demain " + montant,
                "Abonnement : " + montant + " par jour · demain " + montant, "OK",
                "Plafond du maire : " + montant + " (demain " + montant + ") · taxe 20 % (demain 20 %)",
                List.of("Sortie", "Compteurs", "Écart"),
                new GapView("Aujourd'hui", "99 999 999 kFE", "99 999 999 kFE", "99 999 999 kFE (100 %)",
                        99_999_999_000L, 0L),
                new GapView("Hier", "99 999 999 kFE", "99 999 999 kFE", "-99 999 999 kFE (100 %)", 0L,
                        99_999_999_000L),
                10, "Caisse :", CINQ_COUPURES, "Clients (64 sur 64)", clients, "Aucun client");
    }

    private static View unPosteNeuf() {
        return new View(null, "Nouveau poste", "Caisse", "Nom du fournisseur (1 à 24 caractères)", "Fonder", "", "",
                "OK", "", List.of("Sortie", "Compteurs", "Écart"), new GapView("", "", "", "", 0L, 0L),
                new GapView("", "", "", "", 0L, 0L), 10, "", List.of(), "", List.of(), "");
    }

    private static List<Text> textes(Layout l) {
        List<Text> out = new ArrayList<>();
        out.add(l.title());
        if (l.tillLabel() != null) {
            out.add(l.tillLabel());
        }
        if (l.nameLabel() != null) {
            out.add(l.nameLabel());
            out.add(l.foundLabel());
        }
        for (Field f : new Field[] {l.price(), l.subscription()}) {
            if (f != null) {
                out.add(f.label());
                out.add(f.buttonLabel());
            }
        }
        if (l.cap() != null) {
            out.add(l.cap());
            out.addAll(l.gapHeaders());
            for (GapLine g : List.of(l.today(), l.yesterday())) {
                out.addAll(List.of(g.label(), g.out(), g.meters(), g.gap()));
            }
            out.add(l.till());
            out.add(l.listHeader());
        }
        if (l.empty() != null) {
            out.add(l.empty());
        }
        return out;
    }

    /** Les textes d'une ligne de client. */
    private static List<Text> textes(RowLayout r) {
        List<Text> out = new ArrayList<>(List.of(r.name(), r.state(), r.usage(), r.unpaid()));
        out.addAll(r.buttonLabels());
        return out;
    }

    private static List<Rect> boites(Layout l) {
        List<Rect> out = new ArrayList<>();
        textes(l).forEach(t -> out.add(t.box()));
        if (l.tillButton() != null) {
            out.add(l.tillButton());
        }
        if (l.nameField() != null) {
            out.add(l.nameField());
            out.add(l.foundButton());
        }
        for (Field f : new Field[] {l.price(), l.subscription()}) {
            if (f != null) {
                out.add(f.box());
                out.add(f.button());
            }
        }
        if (l.list() != null) {
            out.add(l.list());
        }
        return out;
    }

    @Test
    void toutTientEn320Par240() {
        View v = leContenuLePlusLong();
        Layout l = PowerPanelLayout.of(320, 240, SIX, v);
        Rect ecran = new Rect(0, 0, 320, 240);
        assertTrue(ecran.contains(l.frame()), "le cadre tient dans 320 × 240 : " + l.frame());

        // Tout est dans le cadre ; chaque ligne de la liste, une fois défilée jusqu'à elle, dans la liste.
        for (Rect r : boites(l)) {
            assertTrue(l.frame().contains(r), "hors du cadre : " + r);
        }
        for (RowLayout r : l.rows()) {
            int defilement = Math.min(l.scrollMax(), Math.max(0, r.box().y() - l.list().y()));
            List<Rect> morceaux = new ArrayList<>(List.of(r.box()));
            morceaux.addAll(r.buttons());
            textes(r).forEach(t -> morceaux.add(t.box()));
            for (Rect m : morceaux) {
                assertTrue(l.list().contains(m.shifted(-defilement)), "ligne défilée hors de la liste : " + m);
            }
        }
        // Chaque texte tient dans la place qui lui est réservée.
        List<Text> tous = new ArrayList<>(textes(l));
        l.rows().forEach(r -> tous.addAll(textes(r)));
        for (Text t : tous) {
            assertTrue(t.width() <= t.room(), "le texte « " + t.shown() + " » déborde de sa place (" + t.width() + " > "
                    + t.room() + ")");
        }
        // Les 64 clients sont tous mis en page, la liste défile.
        assertEquals(64, l.rows().size());
        assertTrue(l.scrollMax() > 0, "64 clients ne tiennent pas sans défiler");
        assertEquals(l.contentHeight() - l.list().h(), l.scrollMax());
        // Aucune ligne ne chevauche la suivante, et dans une ligne, ni texte ni bouton ne se chevauchent.
        for (int i = 0; i < l.rows().size(); i++) {
            RowLayout r = l.rows().get(i);
            if (i + 1 < l.rows().size()) {
                assertFalse(r.box().intersects(l.rows().get(i + 1).box()), "lignes " + i + " et " + (i + 1));
            }
            List<Rect> morceaux = new ArrayList<>(List.of(r.name().area(), r.state().box(), r.usage().area(),
                    r.unpaid().box()));
            morceaux.addAll(r.buttons());
            for (int a = 0; a < morceaux.size(); a++) {
                assertTrue(r.box().contains(morceaux.get(a)), "ligne " + i + ", morceau " + a + " hors de la ligne");
                for (int b = a + 1; b < morceaux.size(); b++) {
                    assertFalse(morceaux.get(a).intersects(morceaux.get(b)),
                            "ligne " + i + " : " + morceaux.get(a) + " chevauche " + morceaux.get(b));
                }
            }
        }
        // Les champs ne chevauchent ni leur libellé ni leur bouton ; la liste commence sous la caisse.
        for (Field f : new Field[] {l.price(), l.subscription()}) {
            assertFalse(f.label().area().intersects(f.box()), "libellé sur le champ");
            assertFalse(f.box().intersects(f.button()), "champ sur le bouton");
        }
        assertTrue(l.till().y() + PowerPanelLayout.TEXT_H <= l.list().y(), "la caisse passe sous la liste");
        // Les montants longs sont coupés, jamais au milieu d'une coupure, et gardés entiers pour la bulle.
        assertTrue(l.till().cut() && l.till().shown().endsWith(PowerPanelLayout.ELLIPSIS), "caisse coupée");
        assertTrue(l.till().full().endsWith(String.join(" ", CINQ_COUPURES)), "caisse complète gardée");
        RowLayout r0 = l.rows().get(0);
        assertTrue(r0.unpaid().full().equals("Impayés : " + String.join(" ", CINQ_COUPURES)),
                "impayés complets gardés");
        assertTrue(r0.unpaid().shown().startsWith("Impayés : 1 rubis"), "les plus grandes coupures d'abord");
        // L'écart au-delà du seuil est en alerte, aujourd'hui (perte) comme hier (compteurs au-dessus de la sortie).
        assertTrue(l.today().alert() && l.yesterday().alert(), "écart en alerte");
    }

    @Test
    void enGrandeInterfaceLeCadreEstCentre() {
        Layout l = PowerPanelLayout.of(640, 360, SIX, leContenuLePlusLong());
        assertEquals((640 - PowerPanelLayout.FRAME_W) / 2, l.frame().x());
        assertEquals((360 - PowerPanelLayout.FRAME_H) / 2, l.frame().y());
        for (Rect r : boites(l)) {
            assertTrue(l.frame().contains(r), "hors du cadre : " + r);
        }
    }

    @Test
    void unNomTropLongEstCoupe() {
        Layout l = PowerPanelLayout.of(320, 240, SIX, leContenuLePlusLong());
        Text nom = l.rows().get(0).name();
        assertTrue(nom.cut(), "« abonné (joueur) » de 35 caractères ne tient pas : " + nom.shown());
        assertTrue(nom.shown().endsWith(PowerPanelLayout.ELLIPSIS), "coupé par « … »");
        assertEquals(NOM_16 + " (" + NOM_16 + ")", nom.full(), "le nom complet reste pour la bulle");
        assertTrue(nom.width() <= nom.room());
        // Le nom du fournisseur, à une police deux fois plus large, est coupé aussi, et garde sa place au bouton.
        Layout large = PowerPanelLayout.of(320, 240, s -> 12 * s.length(), leContenuLePlusLong());
        assertTrue(large.title().cut() && large.title().full().equals(NOM_24), "titre coupé : " + large.title().shown());
        assertFalse(large.title().box().intersects(large.tillButton()), "le titre passe sous le bouton de la caisse");
        // Un nom court n'est pas coupé.
        View court = leContenuLePlusLong();
        List<RowView> un = List.of(new RowView("Ab (Ab)", "Actif", "Auj. 0 FE · hier 0 FE", "Impayés :", List.of(),
                List.of("Couper", "Détacher")));
        Layout l2 = PowerPanelLayout.of(320, 240, SIX, new View(court.supplier(), court.title(), court.tillButton(),
                court.nameLabel(), court.foundButton(), court.priceLine(), court.subscriptionLine(), court.okButton(),
                court.capLine(), court.gapHeaders(), court.today(), court.yesterday(), court.warnPercent(),
                court.tillLabel(), court.tillParts(), court.listHeader(), un, court.empty()));
        assertFalse(l2.rows().get(0).name().cut());
        assertEquals(0, l2.scrollMax(), "un client tient sans défiler");
    }

    /**
     * Aux valeurs courantes, rien n'est coupé : un client sans impayé ne montre rien à leur place (jamais « 0 cuivre »),
     * la consommation d'aujourd'hui et d'hier se lit entière, un impayé ordinaire aussi, et l'état le plus long.
     */
    @Test
    void auxValeursCourantesRienNEstCoupe() {
        View v = leContenuLePlusLong();
        List<RowView> courants = List.of(
                new RowView("Bob", "À accepter", "Auj. 0 FE · hier 0 FE", "Impayés :", List.of(), List.of("Accepter", "Refuser")),
                new RowView("Alice", "Coupé", "Auj. 0 FE · hier 1 240 kFE", "Impayés :", List.of("1 or", "2 argent", "5 cuivres"),
                        List.of("Rétablir", "Détacher")),
                new RowView("Atelier (Eve)", "Actif", "Auj. 1 234 kFE · hier 980 kFE", "Impayés :", List.of(),
                        List.of("Couper", "Détacher")),
                new RowView("Dan", "En résiliation", "Auj. 52 kFE · hier 61 kFE", "Impayés :", List.of("2 argent"),
                        List.of("Couper", "Valider")));
        Layout l = PowerPanelLayout.of(320, 240, SIX, new View("Ab", "Ab", v.tillButton(), v.nameLabel(),
                v.foundButton(), "Prix : 1 cuivre", "Abonnement : 0 cuivre par jour", v.okButton(),
                "Plafond : 2 argent les 100 000 FE · taxe 0 %", v.gapHeaders(),
                new GapView("Aujourd'hui", "1 300 kFE", "1 286 kFE", "14 kFE (1 %)", 1_300_000L, 1_286_000L),
                new GapView("Hier", "1 300 kFE", "1 255 kFE", "45 kFE (3 %)", 1_300_000L, 1_255_000L), 10,
                "Caisse :", List.of("4 or", "7 cuivres"), "Clients (4)", courants, "Aucun client"));
        List<Text> tous = new ArrayList<>(textes(l));
        l.rows().forEach(r -> tous.addAll(List.of(r.name(), r.state(), r.usage(), r.unpaid())));
        for (Text t : tous) {
            assertFalse(t.cut(), "coupé aux valeurs courantes : « " + t.full() + " » → « " + t.shown() + " »");
        }
        RowLayout bob = l.rows().get(0);
        assertEquals("", bob.unpaid().shown(), "sans impayé, rien n'est affiché");
        assertEquals(0, bob.unpaid().width());
        assertFalse(l.today().alert() || l.yesterday().alert(), "écarts ordinaires : pas d'alerte");
    }

    @Test
    void unPosteNeufMontreLeChampDuNom() {
        Layout neuf = PowerPanelLayout.of(320, 240, SIX, unPosteNeuf());
        assertNotNull(neuf.nameField(), "un poste neuf montre le champ du nom");
        assertNotNull(neuf.foundButton());
        assertTrue(neuf.frame().contains(neuf.nameField()) && neuf.frame().contains(neuf.foundButton()));
        assertFalse(neuf.nameField().intersects(neuf.foundButton()));
        assertTrue(neuf.nameField().w() >= 24 * 6 + 8, "le champ prend un nom de 24 caractères");
        assertNull(neuf.price(), "pas de tarif sans fournisseur");
        assertNull(neuf.tillButton(), "pas de caisse sans fournisseur");
        assertTrue(neuf.rows().isEmpty() && neuf.list() == null, "pas de clients sans fournisseur");

        Layout fonde = PowerPanelLayout.of(320, 240, SIX, leContenuLePlusLong());
        assertNull(fonde.nameField(), "un poste fondé ne montre pas le champ du nom");
        assertNotNull(fonde.price());
        assertNotNull(fonde.tillButton());
    }

    @Test
    void lEcartNestEnAlerteQuAuDelaDuSeuil() {
        // 10 % et 100 000 FE : les deux doivent être dépassés.
        assertFalse(PowerPanelLayout.gapAlert(1_000_000L, 900_000L, 10), "10 % pile : pas d'alerte");
        assertTrue(PowerPanelLayout.gapAlert(1_000_000L, 899_999L, 10), "au-delà de 10 % et de 100 000 FE");
        assertFalse(PowerPanelLayout.gapAlert(500_000L, 400_000L, 10), "20 % mais 100 000 FE pile : pas d'alerte");
        assertFalse(PowerPanelLayout.gapAlert(100_000_000L, 95_000_000L, 10), "5 000 000 FE mais 5 % : pas d'alerte");
        assertTrue(PowerPanelLayout.gapAlert(0L, 200_000L, 10), "des compteurs sans sortie notée");
        assertTrue(PowerPanelLayout.gapAlert(Long.MAX_VALUE, 0L, 10), "sans débordement");
        assertFalse(PowerPanelLayout.gapAlert(0L, 0L, 10));
    }
}
