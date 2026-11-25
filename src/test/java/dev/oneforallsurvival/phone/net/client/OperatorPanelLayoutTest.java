package dev.oneforallsurvival.phone.net.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.phone.net.AntennaLoad;
import dev.oneforallsurvival.phone.net.OperatorActionPacket.Action;
import dev.oneforallsurvival.phone.net.OperatorView;
import dev.oneforallsurvival.phone.net.PhoneNetwork;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Button;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Layout;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Pane;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Rect;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Row;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Text;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * La mise en page du panneau de l'opérateur (lot T2, tâche 9), sans lancer le jeu, avec une police factice de 6 pixels
 * par caractère (plus large que la vraie pour presque tous les caractères) et les textes français de {@code fr_fr.json} :
 * le contenu le plus long tient de 320 × 240 à 1920 × 1080 sans aucun texte coupé ni rétréci sous 0,75 ; une antenne à
 * fonder ne montre que le champ du nom ; un regardeur sans droit aux cartes n'a aucun bouton de carte ; une antenne qui
 * consomme au-delà de ce qu'elle reçoit est en rouge.
 */
class OperatorPanelLayoutTest {

    private static final ToIntFunction<String> SIX = s -> 6 * s.codePointCount(0, s.length());
    private static final String NOM_24 = "Compagnie des Eaux Vives";
    private static final String TITULAIRE_16 = "Abcdefghijklmnop";
    private static final int[][] TAILLES = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480},
            {960, 540}, {1280, 720}, {1920, 1080}};

    private static JsonObject fr() {
        String racine = System.getProperty("oneforallsurvival.mainResources");
        assertNotNull(racine, "propriété oneforallsurvival.mainResources absente");
        try {
            return JsonParser.parseString(Files.readString(Paths.get(racine).resolve(
                    "assets/oneforallsurvival/lang/fr_fr.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Les textes français : la clé lue dans fr_fr.json ; une caisse à cinq coupures pour tout montant. */
    private static OperatorPanelLayout.Texts francais() {
        JsonObject fr = fr();
        return new OperatorPanelLayout.Texts() {
            @Override
            public String t(String key, Object... args) {
                assertTrue(fr.has(key), "clé absente de fr_fr.json : " + key);
                return String.format(fr.get(key).getAsString(), args);
            }

            @Override
            public String coins(long amount) {
                return amount >= 1_000_000L ? "1 rubis 3 émeraudes 9 or 5 argent 7 cuivres"
                        : amount == 0L ? "0 cuivre" : "9 or 9 argent 9 cuivres";
            }

            @Override
            public String number(long value) {
                return String.format("%,d", value).replace(',', ' ');
            }
        };
    }

    /** Le contenu le plus long : nom de 24, 32 antennes, 32 cartes aux titulaires de 16, 32 demandes, 32 opérateurs. */
    private static OperatorView leContenuLePlusLong(OperatorView.Rights droits) {
        List<OperatorView.AntennaLine> antennes = new ArrayList<>();
        for (int i = 0; i < OperatorView.MAX_LIST; i++) {
            antennes.add(new OperatorView.AntennaLine(new UUID(1, i), "minecraft:the_nether",
                    new BlockPos(-29_999_999, -64, 29_999_999), MachineState.values()[i % 4],
                    new AntennaLoad.Breakdown(9_999, 99_999, 99_999), 6_912_000_000L, 6_912_000_000L, i == 0,
                    i % 2 == 1));
        }
        List<OperatorView.CardLine> cartes = new ArrayList<>();
        for (int i = 0; i < OperatorView.MAX_LIST; i++) {
            cartes.add(new OperatorView.CardLine(new UUID(2, i), "0612345678", i % 5 == 0 ? "" : TITULAIRE_16,
                    SimCard.State.values()[i % 3]));
        }
        List<OperatorView.RequestLine> demandes = new ArrayList<>();
        for (int i = 0; i < OperatorView.MAX_LIST; i++) {
            demandes.add(new OperatorView.RequestLine(new UUID(3, i), TITULAIRE_16, 1_789_000_000_000L));
        }
        List<PhoneNetwork.RelayState> relais = new ArrayList<>();
        for (int i = 0; i < OperatorView.MAX_LIST; i++) {
            relais.add(new PhoneNetwork.RelayState(String.format("%012x", i), NOM_24, 32, i % 2 == 0 ? 0 : 16));
        }
        return new OperatorView(OperatorView.Source.BLOCK, new BlockPos(1, 64, 1), 3, "0123456789ab", NOM_24, droits,
                1_000L, 999L, 1_000L, 5_000_000L, antennes, 99, 100, 3_200, cartes, demandes, relais);
    }

    private static final OperatorView.Rights FONDATEUR = new OperatorView.Rights(true, true, true, false);

    private static List<Text> textes(Layout l) {
        List<Text> out = new ArrayList<>();
        out.add(l.title());
        out.addAll(l.tabLabels());
        if (l.name() != null) {
            out.add(l.name().label());
            out.add(l.name().buttonLabel());
        }
        for (Pane p : l.panes()) {
            for (Row r : p.rows()) {
                out.addAll(r.texts());
                for (Button b : r.buttons()) {
                    out.add(b.label());
                }
            }
        }
        return out;
    }

    private static void dedans(Rect cadre, Rect r, String quoi) {
        assertTrue(cadre.contains(r), quoi + " " + r + " hors de " + cadre);
    }

    @Test
    void toutTientEn320Par240() {
        OperatorPanelLayout.Texts fr = francais();
        for (int[] taille : TAILLES) {
            String ou = taille[0] + "x" + taille[1] + " : ";
            Layout l = OperatorPanelLayout.of(taille[0], taille[1], SIX, leContenuLePlusLong(FONDATEUR), fr);
            Rect ecran = new Rect(0, 0, taille[0], taille[1]);
            dedans(ecran, l.frame(), ou + "cadre");
            assertNull(l.name(), ou + "un opérateur fondé n'a pas de champ du nom");
            assertEquals(3, l.tabs().size(), ou + "trois onglets");
            for (Rect onglet : l.tabs()) {
                dedans(l.frame(), onglet, ou + "onglet");
            }
            for (Text t : textes(l)) {
                assertFalse(t.cut(), ou + "texte coupé : « " + t.full() + " » montré « " + t.shown() + " »");
                assertTrue(t.scale() >= OperatorPanelLayout.MIN_SCALE, ou + "texte trop petit : " + t);
                assertTrue(t.width() * t.scale() <= t.room() + 0.001f, ou + "texte plus large que sa place : " + t);
            }
            for (Pane p : l.panes()) {
                dedans(l.frame(), p.list(), ou + "liste");
                assertFalse(p.rows().isEmpty(), ou + "volet vide");
                int bas = p.list().y();
                for (Row r : p.rows()) {
                    assertTrue(r.box().x() >= p.list().x() && r.box().x() + r.box().w() <= p.list().x() + p.list().w(),
                            ou + "ligne hors de la liste en largeur : " + r.id());
                    assertTrue(r.box().y() >= bas, ou + "lignes qui se chevauchent : " + r.id());
                    bas = r.box().y() + r.box().h();
                    for (Text t : r.texts()) {
                        dedans(r.box(), t.area(), ou + "texte de " + r.id());
                    }
                    for (Button b : r.buttons()) {
                        dedans(r.box(), b.box(), ou + "bouton de " + r.id());
                        for (Text t : r.texts()) {
                            assertFalse(b.box().intersects(t.drawn()), ou + "bouton sur un texte dans " + r.id()
                                    + " : " + t.full());
                        }
                    }
                    if (r.field() != null) {
                        dedans(r.box(), r.field(), ou + "champ de " + r.id());
                    }
                }
                assertEquals(Math.max(0, bas - p.list().y() - p.list().h()), p.scrollMax(), ou + "défilement");
            }
        }
    }

    @Test
    void uneAntenneAFonderMontreLeChampDuNom() {
        OperatorView aFonder = new OperatorView(OperatorView.Source.BLOCK, new BlockPos(1, 64, 1), 1, "", "",
                new OperatorView.Rights(false, false, false, true), 0L, null, 0L, 0L, List.of(), 0, 0, 0, List.of(),
                List.of(), List.of());
        for (int[] taille : TAILLES) {
            Layout l = OperatorPanelLayout.of(taille[0], taille[1], SIX, aFonder, francais());
            assertNotNull(l.name(), "le champ du nom");
            assertTrue(l.tabs().isEmpty() && l.panes().isEmpty(), "rien d'autre qu'un nom à donner");
            dedans(l.frame(), l.name().field(), "champ du nom");
            dedans(l.frame(), l.name().button(), "bouton « Fonder »");
            assertFalse(l.name().field().intersects(l.name().button()), "champ et bouton se chevauchent");
            assertFalse(l.name().label().cut() || l.name().buttonLabel().cut(), "libellés coupés");
        }
        // Sans le droit de nommer (ingénieur outillé devant l'antenne d'un autre) : aucun champ.
        OperatorView sansDroit = new OperatorView(OperatorView.Source.BLOCK, new BlockPos(1, 64, 1), 1, "", "",
                new OperatorView.Rights(false, false, false, false), 0L, null, 0L, 0L, List.of(), 0, 0, 0, List.of(),
                List.of(), List.of());
        assertNull(OperatorPanelLayout.of(320, 240, SIX, sansDroit, francais()).name(), "aucun champ sans le droit");
    }

    @Test
    void unRegardeurSansDroitAuxCartesNeVoitPasLesBoutons() {
        List<Action> deCarte = List.of(Action.ISSUE, Action.REFUSE, Action.ISSUE_ANONYMOUS, Action.SUSPEND,
                Action.REACTIVATE, Action.REPLACE, Action.SET_MAIN);
        // Le maire : le tarif, pas les cartes ni la caisse.
        Layout maire = OperatorPanelLayout.of(320, 240, SIX,
                leContenuLePlusLong(new OperatorView.Rights(false, true, false, false)), francais());
        List<Action> vus = new ArrayList<>();
        boolean champ = false;
        for (Pane p : maire.panes()) {
            for (Row r : p.rows()) {
                r.buttons().forEach(b -> vus.add(b.action()));
                champ |= r.field() != null;
            }
        }
        for (Action a : deCarte) {
            assertFalse(vus.contains(a), "le maire ne doit pas voir " + a);
        }
        assertFalse(vus.contains(Action.OPEN_TILL), "le maire n'ouvre pas la caisse");
        assertTrue(vus.contains(Action.SET_TARIFF) && champ, "le maire règle le tarif");
        // Témoin : le fondateur voit chaque geste de carte.
        Layout fondateur = OperatorPanelLayout.of(320, 240, SIX, leContenuLePlusLong(FONDATEUR), francais());
        List<Action> tous = new ArrayList<>();
        for (Pane p : fondateur.panes()) {
            for (Row r : p.rows()) {
                r.buttons().forEach(b -> tous.add(b.action()));
            }
        }
        for (Action a : deCarte) {
            assertTrue(tous.contains(a), "le fondateur doit voir " + a);
        }
        assertTrue(tous.contains(Action.OPEN_TILL) && tous.contains(Action.PAGE), "caisse et pages du fondateur");
        // Un ingénieur outillé : ni tarif, ni cartes, ni caisse ; aucun champ.
        Layout ingenieur = OperatorPanelLayout.of(320, 240, SIX,
                leContenuLePlusLong(new OperatorView.Rights(false, false, false, false)), francais());
        for (Pane p : ingenieur.panes()) {
            for (Row r : p.rows()) {
                assertTrue(r.field() == null, "aucun champ pour l'ingénieur");
                for (Button b : r.buttons()) {
                    assertEquals(Action.PAGE, b.action(), "l'ingénieur ne voit que les pages : " + b.action());
                }
            }
        }
    }

    /**
     * Revue de la tâche 9 (mineur 2) : le dernier retour du serveur se lit dans le panneau, sur deux lignes au plus,
     * jamais sous 0,75, sans chevaucher la liste, même à 320 × 240 avec le message le plus long.
     */
    @Test
    void laLigneDEtatMontreLeDernierRetour() {
        JsonObject fr = fr();
        List<String> messages = new ArrayList<>();
        for (String cle : fr.keySet()) {
            if (cle.startsWith("message.oneforallsurvival.operator_panel.")) {
                messages.add(String.format(fr.get(cle).getAsString(), NOM_24, "1 rubis 3 émeraudes 9 or 5 argent 7 cuivres"));
            }
        }
        assertTrue(messages.size() >= 15, "messages du panneau lus : " + messages.size());
        for (int[] taille : TAILLES) {
            for (String m : messages) {
                String ou = taille[0] + "x" + taille[1] + " « " + m + " » : ";
                Layout l = OperatorPanelLayout.of(taille[0], taille[1], SIX, leContenuLePlusLong(FONDATEUR), francais(),
                        m);
                assertFalse(l.status().isEmpty(), ou + "aucune ligne d'état");
                assertTrue(l.status().size() <= 2, ou + "plus de deux lignes");
                StringBuilder lu = new StringBuilder();
                for (Text t : l.status()) {
                    dedans(l.frame(), t.area(), ou + "ligne d'état");
                    assertTrue(t.scale() >= OperatorPanelLayout.MIN_SCALE, ou + "trop petit : " + t);
                    assertTrue(t.width() * t.scale() <= t.room() + 0.001f, ou + "plus large que sa place : " + t);
                    for (Pane p : l.panes()) {
                        assertFalse(p.list().intersects(t.area()), ou + "la ligne d'état chevauche la liste");
                    }
                    lu.append(lu.length() == 0 ? "" : " ").append(t.shown());
                }
                if (taille[0] >= 427) {
                    assertEquals(m, lu.toString(), ou + "message coupé");
                }
            }
            Layout vide = OperatorPanelLayout.of(taille[0], taille[1], SIX, leContenuLePlusLong(FONDATEUR), francais(),
                    "");
            assertTrue(vide.status().isEmpty(), "sans message, aucune ligne d'état");
        }
    }

    /** Revue de la tâche 9 : le panneau ouvert depuis le téléphone ne se ferme pas à 8 blocs de (0, 0, 0). */
    @Test
    void lePanneauDuTelephoneNeSeFermePasAuLoin() {
        OperatorView bloc = leContenuLePlusLong(FONDATEUR);
        OperatorView telephone = new OperatorView(OperatorView.Source.PHONE, BlockPos.ZERO, 3, bloc.operatorId(),
                bloc.name(), bloc.rights(), 0L, null, 0L, 0L, List.of(), 0, 1, 0, List.of(), List.of(), List.of());
        assertTrue(OperatorPanelLayout.closesAtDistance(bloc, 65.0), "bloc : au-delà de 8 blocs, fermé");
        assertFalse(OperatorPanelLayout.closesAtDistance(bloc, 64.0), "bloc : à 8 blocs, ouvert");
        assertFalse(OperatorPanelLayout.closesAtDistance(telephone, 1.0e12), "téléphone : jamais fermé par la distance");
    }

    /** Revue de la tâche 10 (mineur 3) : « 1 antenne » et « 0 antenne » au singulier, « 2 antennes » au pluriel. */
    @Test
    void leRelaisAccordeLeNombreDAntennes() {
        OperatorView base = leContenuLePlusLong(FONDATEUR);
        OperatorView v = new OperatorView(base.source(), base.pos(), base.session(), base.operatorId(), base.name(),
                base.rights(), 0L, null, 0L, 0L, List.of(), 0, 1, 0, List.of(), List.of(),
                List.of(new PhoneNetwork.RelayState("a", "Zero", 0, 0), new PhoneNetwork.RelayState("b", "Une", 1, 1),
                        new PhoneNetwork.RelayState("c", "Deux", 2, 1)));
        Layout l = OperatorPanelLayout.of(640, 360, SIX, v, francais());
        List<String> lignes = new ArrayList<>();
        for (Row r : l.panes().get(OperatorPanelLayout.Tab.NETWORK.ordinal()).rows()) {
            if (r.id().startsWith("relay-") && !r.id().equals("relay-header")) {
                lignes.add(r.texts().get(1).full());
            }
        }
        assertEquals(3, lignes.size(), "trois lignes de relais : " + lignes);
        assertTrue(lignes.get(0).startsWith("0 antenne,") && lignes.get(1).startsWith("1 antenne,")
                && lignes.get(2).startsWith("2 antennes,"), "accord : " + lignes);
    }

    /**
     * Revue de la tâche 10 (mineur 1) : un panneau retiré sans Échap ni fermeture par le serveur prévient le serveur ;
     * celui du téléphone ferme aussi la session du téléphone ; jamais deux fois.
     */
    @Test
    void unPanneauRetireSansEchapPrevientLeServeur() {
        assertEquals(OperatorPanelLayout.Removal.PANEL_AND_PHONE, OperatorPanelLayout.onRemoval(true, false, false));
        assertEquals(OperatorPanelLayout.Removal.PANEL, OperatorPanelLayout.onRemoval(false, false, false));
        assertEquals(OperatorPanelLayout.Removal.NONE, OperatorPanelLayout.onRemoval(true, true, false),
                "sortie par Échap : déjà dit");
        assertEquals(OperatorPanelLayout.Removal.NONE, OperatorPanelLayout.onRemoval(true, false, true),
                "fermé par le serveur : rien à dire");
        assertEquals(OperatorPanelLayout.Removal.NONE, OperatorPanelLayout.onRemoval(false, true, false));
    }

    @Test
    void audelaDeLaReceptionEnRouge() {
        Layout l = OperatorPanelLayout.of(320, 240, SIX, leContenuLePlusLong(FONDATEUR), francais());
        Pane reseau = l.panes().get(OperatorPanelLayout.Tab.NETWORK.ordinal());
        int vues = 0;
        for (Row r : reseau.rows()) {
            if (r.id().startsWith("antenna-")) {
                int i = Integer.parseInt(r.id().substring("antenna-".length()));
                assertEquals(i % 2 == 1, r.alert(), "antenne " + i + " : rouge seulement au-delà de la réception");
                vues++;
            } else {
                assertFalse(r.alert(), "seules les antennes passent en rouge : " + r.id());
            }
        }
        assertEquals(OperatorView.MAX_LIST, vues, "une ligne par antenne");
    }
}
