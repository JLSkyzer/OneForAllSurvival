package dev.oneforallsurvival.economy.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.economy.client.TownBoardLayout.Line;
import dev.oneforallsurvival.economy.client.TownBoardLayout.Result;
import dev.oneforallsurvival.economy.client.TownBoardLayout.Section;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** La mise en page du tableau : tout tient, rien n'est coupé, rien ne descend sous le seuil de lecture. */
class TownBoardLayoutTest {

    /** Cinq pixels par caractère : la police de Minecraft en moyenne (le pire cas se lit en jeu, sur capture). */
    private static int width(Component text) {
        return text.getString().length() * 5;
    }

    private static List<Component> coins(String... texts) {
        List<Component> out = new ArrayList<>();
        for (String text : texts) {
            out.add(Component.literal(text));
        }
        return out;
    }

    private static Section section(int color, boolean optional, String head, String... amount) {
        return new Section(Component.literal(head), coins(amount), color, optional, false);
    }

    private static List<Section> pire() {
        return List.of(section(1, true, "Tresor de la ville"),
                section(2, false, "Solde :", "9999 rubis", "9 emeraudes", "9 or", "9 argent", "9 cuivres"),
                section(3, false, "Salaires dus : 99", "9999 rubis", "9 emeraudes", "9 or", "9 argent", "9 cuivres"));
    }

    private static void tient(Result result) {
        assertTrue(result.scale() >= TownBoardLayout.MIN_SCALE - 1.0E-4F, "échelle " + result.scale());
        for (Line line : result.lines()) {
            assertTrue(width(line.text()) <= TownBoardLayout.WIDTH / result.scale() + 1.0E-3F,
                    "ligne trop large : " + line.text().getString());
        }
        assertTrue(result.height() <= TownBoardLayout.HEIGHT + 1.0E-3F, "hauteur " + result.height());
    }

    @Test
    void unTexteCourtGardeLEchelleUnEtSonTitre() {
        Result result = TownBoardLayout.layout(List.of(section(1, true, "Tres"), section(2, false, "So", "5 or"),
                section(3, false, "Aucun")), TownBoardLayoutTest::width);
        assertEquals(1.0F, result.scale(), 1.0E-4F);
        assertEquals(3, result.lines().size());
        assertFalse(result.shortened());
    }

    @Test
    void lePireCasTientAuSeuilDeLectureSansCouperUnMorceau() {
        Result result = TownBoardLayout.layout(pire(), TownBoardLayoutTest::width);
        tient(result);
        // Chaque ligne est faite de morceaux entiers : aucune coupure n'est rompue au milieu.
        for (Line line : result.lines()) {
            for (String morceau : List.of("9999", "rubis", "emeraudes", "argent", "cuivres")) {
                String texte = line.text().getString();
                if (texte.contains(morceau) && morceau.equals("9999")) {
                    assertTrue(texte.contains("9999 rubis"), "« 9999 rubis » rompu : " + texte);
                }
            }
        }
    }

    @Test
    void sansPlaceLeTitreSEffaceAvantLesCoupures() {
        List<Section> sections = List.of(section(1, true, "Tresor de la ville"),
                section(2, false, "Solde :", "9 rubis", "9 emeraudes"), section(3, false, "Aucun salaire du"));
        Result result = TownBoardLayout.layout(sections, TownBoardLayoutTest::width);
        tient(result);
        // Un solde de deux coupures et « Aucun salaire dû » tiennent sans titre à l'échelle 0,7, et gardent tout.
        assertFalse(result.shortened(), "aucune coupure perdue : " + result.lines());
    }

    @Test
    void sinonLesPlusPetitesCoupuresSontLaisseesDeCote() {
        Result result = TownBoardLayout.layout(pire(), width -> width.getString().length() * 9);
        tient(result);
        assertTrue(result.shortened(), "des coupures sont laissées de côté");
        assertTrue(result.lines().stream().anyMatch(l -> l.text().getString().contains("…")), "le signe « … » le dit");
        assertTrue(result.lines().stream().anyMatch(l -> l.text().getString().contains("9999 rubis")),
                "la plus grande coupure reste");
    }

    @Test
    void lesParenthesesEtLePointsDeSuspensionRestentColles() {
        Section due = new Section(Component.literal("Dus : 3"), coins("9999 rubis", "9 emeraudes", "9 or"), 1, false, true);
        Result entier = TownBoardLayout.layout(List.of(due), TownBoardLayoutTest::width);
        String texte = String.join("|", entier.lines().stream().map(l -> l.text().getString()).toList());
        assertTrue(texte.contains("(9999 rubis") && texte.contains("9 or)"), texte);
        Section longue = new Section(Component.literal("Dus : 3 a b"), coins("9999 rubis", "9 emeraudes", "9 or"), 1, false, true);
        Result court = TownBoardLayout.layout(List.of(longue), width -> width.getString().length() * 30);
        String coupe = String.join("|", court.lines().stream().map(l -> l.text().getString()).toList());
        assertTrue(court.shortened() && coupe.contains("rubis …)"), coupe);
    }

    @Test
    void uneLonguePhraseSansMontantSeCoupeEnMotsPlutotQueDeDeborder() {
        Result result = TownBoardLayout.layout(List.of(section(1, false, "Aucun salaire du pour personne ici")),
                TownBoardLayoutTest::width);
        tient(result);
        assertTrue(result.lines().size() >= 2, "coupée en mots : " + result.lines());
    }

    @Test
    void chaqueSectionGardeSaCouleur() {
        Result result = TownBoardLayout.layout(List.of(section(7, false, "a"), section(8, false, "b", "c")),
                TownBoardLayoutTest::width);
        assertEquals(List.of(7, 8), result.lines().stream().map(Line::color).toList());
    }

    @Test
    void unSigneSeulResteColleAuMotPrecedent() {
        assertEquals(List.of("Salaires", "dus :", "99"),
                TownBoardLayout.words(Component.literal("Salaires dus : 99")).stream().map(Component::getString).toList());
    }
}
