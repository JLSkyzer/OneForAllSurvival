package dev.oneforallsurvival.client.title;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.oneforallsurvival.client.title.TitleWidgets.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class TitleWidgetsTest {
    @Test
    void nosBoutonsSontLesNotresMemeAvecUneCleDeMinecraft() {
        assertEquals(Kind.OURS, TitleWidgets.classify("gui.oneforallsurvival.title.solo", true));
        assertEquals(Kind.OURS, TitleWidgets.classify("menu.options", true));
        assertEquals(Kind.OURS, TitleWidgets.classify(null, true));
    }

    @Test
    void lesBoutonsPrincipauxDeMinecraftSontRetires() {
        for (String key : List.of("menu.singleplayer", "menu.multiplayer", "menu.online", "menu.options",
                "menu.quit", "menu.playdemo", "menu.resetdemo")) {
            assertEquals(Kind.VANILLA_REMOVED, TitleWidgets.classify(key, false), key);
        }
    }

    @Test
    void lesPetitsBoutonsEtLaMentionDeMojangSontGardes() {
        for (String key : List.of("fml.menu.mods", "narrator.button.language", "narrator.button.accessibility",
                TitleWidgets.COPYRIGHT_KEY)) {
            assertEquals(Kind.VANILLA_KEPT, TitleWidgets.classify(key, false), key);
        }
    }

    @Test
    void lesBoutonsDesAutresModsSontEtrangers() {
        // Create 6.0.8 (OpenCreateMenuButton) : libellé CommonComponents.EMPTY, donc aucune clé de traduction.
        assertEquals(Kind.FOREIGN, TitleWidgets.classify(null, false));
        // Clé quelconque d'un autre mod, et nos propres clés sur un widget qui n'est pas le nôtre.
        assertEquals(Kind.FOREIGN, TitleWidgets.classify("gui.somemod.title_button", false));
        assertEquals(Kind.FOREIGN, TitleWidgets.classify("gui.oneforallsurvival.title.join", false));
        assertEquals(Kind.FOREIGN, TitleWidgets.classify("", false));
    }

    @Test
    void laRangeeCommenceParLesModsLaLangueEtLAccessibilite() {
        // Ordre d'ajout typique : Forge, Minecraft, puis Create et un autre mod.
        List<String> added = new ArrayList<>(List.of("narrator.button.accessibility", "gui.othermod.button",
                "fml.menu.mods", "narrator.button.language"));
        added.add(1, null); // Create
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < added.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt(
                i -> TitleWidgets.rowRank(added.get(i), TitleWidgets.classify(added.get(i), false))));
        List<String> sorted = new ArrayList<>();
        for (int i : order) {
            sorted.add(added.get(i));
        }
        List<String> expected = new ArrayList<>(List.of("fml.menu.mods", "narrator.button.language",
                "narrator.button.accessibility"));
        expected.add(null); // les étrangers gardent leur ordre d'ajout
        expected.add("gui.othermod.button");
        assertEquals(expected, sorted);
    }
}
