package dev.oneforallsurvival.economy.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.economy.client.TownBoardLayout.Line;
import dev.oneforallsurvival.economy.client.TownBoardLayout.Result;
import dev.oneforallsurvival.economy.client.TownBoardLayout.Section;
import dev.oneforallsurvival.economy.town.TownFinances;
import dev.oneforallsurvival.economy.town.TownFinances.Parts;
import dev.oneforallsurvival.economy.town.TownFinances.Snapshot;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * La mise en page du tableau de 3 x 2 sur les vrais textes du jeu (langues livrées, coupures de {@code CoinMath}) et les
 * vraies largeurs de la police ({@link PoliceVanilla}) : tout tient, rien n'est coupé, aucun mot seul sur sa ligne.
 */
class TownBoardLayoutTest {

    private static final Language AVANT = Language.getInstance();

    @AfterAll
    static void rendreLaLangue() {
        Language.inject(AVANT);
    }

    /** Les textes d'une langue livrée, comme le client les lit. */
    private static void langue(String code) {
        Map<String, String> textes = new HashMap<>();
        try (InputStream in = TownBoardLayoutTest.class.getResourceAsStream("/assets/oneforallsurvival/lang/" + code + ".json")) {
            Language.loadFromJson(in, textes::put);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return textes.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return textes.containsKey(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return sink -> true;
            }
        });
    }

    /** Les sections du rendu ({@code TownBoardRenderer}) pour ces comptes. */
    private static List<Section> sections(@Nullable Snapshot snapshot) {
        Parts balance = TownFinances.balanceParts(snapshot);
        Parts due = TownFinances.owedParts(snapshot);
        return List.of(new Section(TownFinances.titleLine(), List.of(), 1, true, false),
                new Section(balance.head(), balance.amount(), 2, false, false),
                new Section(due.head(), due.amount(), 3, false, true));
    }

    private static Result layout(@Nullable Snapshot snapshot) {
        return TownBoardLayout.layout(sections(snapshot), PoliceVanilla::width);
    }

    private static List<String> textes(Result result) {
        return result.lines().stream().map(l -> l.text().getString()).toList();
    }

    /** Dans le parchemin, lisible à quatre blocs, et aucun mot seul sur une ligne. */
    private static void tientEtSeLit(Result result) {
        assertTrue(result.scale() >= TownBoardLayout.MIN_SCALE - 1.0E-4F, "échelle " + result.scale());
        for (Line line : result.lines()) {
            String texte = line.text().getString();
            assertTrue(PoliceVanilla.width(texte) * result.scale() <= TownBoardLayout.WIDTH + 1.0E-3F,
                    "ligne trop large (" + PoliceVanilla.width(texte) + " px) : " + texte);
            assertTrue(texte.trim().split(" +").length >= 2, "mot seul sur une ligne : « " + texte + " » dans " + textes(result));
        }
        assertTrue(result.height() <= TownBoardLayout.HEIGHT + 1.0E-3F, "hauteur " + result.height());
    }

    @Test
    void laTableDeLaPoliceRedonneDesLargeursConnues() {
        // Relevées à la main sur ascii.png : « Solde : » = S6 o6 l3 d6 e6, espace 4, deux-points 2.
        assertEquals(33, PoliceVanilla.width("Solde :"));
        assertEquals(8, PoliceVanilla.width("…"));
        assertEquals(8, PoliceVanilla.width("()"));
    }

    @Test
    void lePireCasTientEnEntierAEchelleUne() {
        langue("fr_fr");
        Result result = layout(new Snapshot(99_999_999L, 99_999_999L, 99));
        tientEtSeLit(result);
        assertEquals(1.0F, result.scale(), 1.0E-4F, "le pire cas se lit à pleine taille");
        assertFalse(result.shortened(), "aucune coupure laissée de côté : " + textes(result));
        assertEquals(List.of(
                "Trésor de la ville",
                "Solde : 9999 rubis 9 émeraudes",
                "9 or 9 argent 9 cuivres",
                "Salaires dus : 99",
                "(9999 rubis 9 émeraudes",
                "9 or 9 argent 9 cuivres)"), textes(result));
    }

    @Test
    void videEtSansDetteTiennentSurTroisLignes() {
        langue("fr_fr");
        Result vide = layout(new Snapshot(0L, 0L, 0));
        tientEtSeLit(vide);
        assertEquals(List.of("Trésor de la ville", "Solde : 0 cuivre", "Aucun salaire dû"), textes(vide));
        Result sansDette = layout(new Snapshot(99_999_999L, 0L, 0));
        tientEtSeLit(sansDette);
        assertEquals(List.of("Trésor de la ville", "Solde : 9999 rubis 9 émeraudes", "9 or 9 argent 9 cuivres",
                "Aucun salaire dû"), textes(sansDette));
        Result rienRecu = layout(null);
        tientEtSeLit(rienRecu);
        assertEquals(List.of("Trésor de la ville", "Solde : …", "Salaires dus : …"), textes(rienRecu));
    }

    @Test
    void aucunMotSeulQuelsQueSoientLesComptes() {
        for (String code : List.of("fr_fr", "en_us")) {
            langue(code);
            long[] montants = {0L, 1L, 9L, 10L, 99L, 101L, 999L, 1_000L, 1_111L, 10_000L, 11_111L, 99_999L, 123_456L,
                    1_000_001L, 9_999_999L, 10_101_010L, 99_999_999L};
            for (long solde : montants) {
                for (long du : montants) {
                    for (int n : new int[] {0, 1, 9, 10, 99}) {
                        Result result = layout(new Snapshot(solde, n == 0 ? 0L : Math.max(du, 1L), n));
                        tientEtSeLit(result);
                        assertFalse(result.shortened(), code + " " + solde + "/" + du + "/" + n + " : " + textes(result));
                        assertTrue(textes(result).get(0).equals(TownFinances.titleLine().getString()),
                                "le titre reste : " + textes(result));
                    }
                }
            }
        }
    }

    @Test
    void lesAnnoncesDuMaireSEffacentAvantLesComptes() {
        langue("fr_fr");
        List<Section> avec = new ArrayList<>(sections(new Snapshot(99_999_999L, 99_999_999L, 99)));
        for (int i = 0; i < 6; i++) {
            avec.add(new Section(Component.literal("Annonce numéro " + i + " du maire"), List.of(), 4, true, false));
        }
        Result result = TownBoardLayout.layout(avec, PoliceVanilla::width);
        tientEtSeLit(result);
        assertFalse(result.shortened(), "les comptes restent entiers : " + textes(result));
        assertTrue(textes(result).stream().anyMatch(t -> t.startsWith("Salaires dus : 99")), textes(result).toString());
    }

    @Test
    void sinonLesPlusPetitesCoupuresSontLaisseesDeCote() {
        // Un montant de trente coupures ne tient pas, même sans titre : on en garde moins, jamais on ne déborde.
        List<Component> trente = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            trente.add(Component.literal("9999 rubis"));
        }
        Result result = TownBoardLayout.layout(List.of(new Section(Component.literal("Titre du tableau"), List.of(), 1, true, false),
                new Section(Component.literal("Solde :"), trente, 2, false, false),
                new Section(Component.literal("Salaires dus : 99"), trente, 3, false, true)), PoliceVanilla::width);
        tientEtSeLit(result);
        assertTrue(result.shortened(), "des coupures sont laissées de côté");
        assertFalse(textes(result).contains("Titre du tableau"), "le titre s'efface d'abord : " + textes(result));
        assertTrue(textes(result).stream().anyMatch(t -> t.endsWith("rubis …)")), "le signe « … » le dit : " + textes(result));
    }

    @Test
    void unTitreTropLargeSEffacePlutotQueDeSeCouper() {
        Result result = TownBoardLayout.layout(List.of(
                new Section(Component.literal("Un titre beaucoup trop long pour tenir sur une seule ligne du tableau"),
                        List.of(), 1, true, false),
                new Section(Component.literal("Solde :"), List.of(Component.literal("5 or")), 2, false, false)),
                PoliceVanilla::width);
        assertEquals(List.of("Solde : 5 or"), textes(result));
    }

    @Test
    void lEquilibreEviteLeMotSeulEnFin() {
        // Glouton : « aaaa bbbb cccc dddd » puis « ee » seul ; équilibré : deux lignes à peu près égales.
        List<Component> morceaux = List.of(Component.literal("aaaa"), Component.literal("bbbb"), Component.literal("cccc"),
                Component.literal("dddd"), Component.literal("ee"));
        List<Component> lignes = TownBoardLayout.balance(morceaux, 110.0F, PoliceVanilla::width);
        assertEquals(List.of("aaaa bbbb", "cccc dddd ee"), lignes.stream().map(Component::getString).toList());
    }

    @Test
    void chaqueSectionGardeSaCouleur() {
        Result result = TownBoardLayout.layout(List.of(new Section(Component.literal("a b"), List.of(), 7, false, false),
                new Section(Component.literal("b"), List.of(Component.literal("c")), 8, false, false)), PoliceVanilla::width);
        assertEquals(List.of(7, 8), result.lines().stream().map(Line::color).toList());
    }
}
