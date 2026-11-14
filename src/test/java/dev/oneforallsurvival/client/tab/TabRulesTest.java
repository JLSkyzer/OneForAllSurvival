package dev.oneforallsurvival.client.tab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.client.debug.DebugTheme;
import dev.oneforallsurvival.client.tab.TabRules.Job;
import dev.oneforallsurvival.client.tab.TabRules.Segment;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TabRulesTest {

    private static final VanillaWidths W = VanillaWidths.INSTANCE;

    @Test
    void leJourChangeAMinuit() {
        long[][] cas = {{0, 1}, {1000, 1}, {6000, 1}, {12000, 1}, {17999, 1}, {18000, 2}, {23999, 2}, {24000, 2}, {324667, 14}};
        String[] heures = {"06:00", "07:00", "12:00", "18:00", "23:59", "00:00", "05:59", "06:00", "18:40"};
        for (int i = 0; i < cas.length; i++) {
            assertEquals(cas[i][1], TabRules.day(cas[i][0]), "jour à t=" + cas[i][0]);
            assertEquals(heures[i], TabRules.clock(cas[i][0]), "heure à t=" + cas[i][0]);
        }
    }

    @Test
    void unTempsNegatifResteCoherent() {
        assertEquals(0, TabRules.day(-6001));
        assertEquals("05:59", TabRules.clock(-1));
    }

    @Test
    void lePingSuitLesSeuilsDeVanilla() {
        int[][] cas = {{-1, 0}, {0, 0}, {1, 5}, {149, 5}, {150, 4}, {299, 4}, {300, 3}, {599, 3}, {600, 2}, {999, 2}, {1000, 1}};
        for (int[] c : cas) {
            assertEquals(c[1], TabRules.pingBars(c[0]), "barres à " + c[0] + " ms");
        }
        assertEquals(DebugTheme.LABEL, TabRules.pingColor(0));
        assertEquals(DebugTheme.GOOD, TabRules.pingColor(149));
        assertEquals(DebugTheme.AMBER, TabRules.pingColor(150));
        assertEquals(DebugTheme.AMBER, TabRules.pingColor(599));
        assertEquals(DebugTheme.BAD, TabRules.pingColor(600));
        assertEquals("…", TabRules.pingText(0));
        assertEquals("42", TabRules.pingText(42));
        assertEquals("999+", TabRules.pingText(1000));
    }

    @Test
    void leRangVientDuNomDEquipe() {
        assertEquals(0, TabRules.rank("ofas_medecin"));
        assertEquals(1, TabRules.rank("ofas_ingenieur"));
        assertEquals(2, TabRules.rank("ofas_policier"));
        assertEquals(3, TabRules.rank("ofas_agriculteur"));
        assertEquals(4, TabRules.rank("ofas_pecheur"));
        assertEquals(5, TabRules.rank("ofas_citoyen"));
        assertEquals(6, TabRules.rank(null));
        assertEquals(6, TabRules.rank("rouge"));
    }

    @Test
    void leMetierSeLitDansLePrefixe() {
        Job medecin = TabRules.job("ofas_medecin", List.of(new Segment("[Médecin] ", 0xFF5555)));
        assertEquals("Médecin", medecin.label());
        assertEquals(0xFF5555, medecin.rgb());
        assertEquals(0, medecin.rank());
        assertFalse(medecin.mayor());

        Job maire = TabRules.job("ofas.maire", List.of(new Segment("[Maire] ", 0xFF55FF), new Segment("[Ingénieur] ", 0xFFAA00)));
        assertTrue(maire.mayor());
        assertEquals("Ingénieur", maire.label());
        assertEquals(0xFFAA00, maire.rgb());

        // Libellé renommé par commande : lu tel quel, le rang reste celui de l'équipe.
        Job renomme = TabRules.job("ofas_medecin", List.of(new Segment("[Soigneuse] ", 0x55FFFF)));
        assertEquals("Soigneuse", renomme.label());
        assertEquals(0, renomme.rank());

        assertNull(TabRules.job(null, List.of()).label());
        assertNull(TabRules.job("rouge", List.of(new Segment("[Rouge] ", 0xFF0000))).label());
        assertEquals(TabRules.RANK_NONE, TabRules.job("rouge", List.of()).rank());
    }

    @Test
    void lesCouleursSombresSontEclaircies() {
        int[][] table = {
                {0x000000, 0xFF666666}, {0x0000AA, 0xFF6666CC}, {0x00AA00, 0xFF00AA00}, {0x00AAAA, 0xFF00AAAA},
                {0xAA0000, 0xFFCC6666}, {0xAA00AA, 0xFFCC66CC}, {0xFFAA00, 0xFFFFAA00}, {0xAAAAAA, 0xFFAAAAAA},
                {0x555555, 0xFF555555}, {0x5555FF, 0xFF5555FF}, {0x55FF55, 0xFF55FF55}, {0x55FFFF, 0xFF55FFFF},
                {0xFF5555, 0xFFFF5555}, {0xFF55FF, 0xFFFF55FF}, {0xFFFF55, 0xFFFFFF55}, {0xFFFFFF, 0xFFFFFFFF}};
        for (int[] c : table) {
            assertEquals(c[1], TabRules.displayColor(c[0]), Integer.toHexString(c[0]));
        }
    }

    @Test
    void unSpectateurEstAttenue() {
        assertEquals(0x73FFFFFF, DebugTheme.fade(0xFFFFFFFF, 0.45));
        assertEquals(0x10FFFFFF, DebugTheme.fade(0x24FFFFFF, 0.45));
    }

    @Test
    void unLibelleTropLongEstTronque() {
        assertEquals("Responsable du…", TabRules.truncate("Responsable du port de pêche", TabRules.JOB_MAX_W, W::width));
        assertEquals("Gérant de casino", TabRules.truncate("Gérant de casino", TabRules.JOB_MAX_W, W::width));
        assertEquals(86, W.width("Gérant de casino"));
        assertTrue(W.width(TabRules.truncate("WWWWWWWWWWWWWWWWWWWW", 96, W::width)) <= 96);
        assertNull(TabRules.truncate(null, 96, W::width));
    }

    @Test
    void lesTextesSuiventLeNombre() {
        assertEquals("survivant sur l'île", TabRules.survivorsText(0));
        assertEquals("survivant sur l'île", TabRules.survivorsText(1));
        assertEquals("survivants sur l'île", TabRules.survivorsText(2));
        assertEquals("et 1 autre", TabRules.othersText(1));
        assertEquals("et 35 autres", TabRules.othersText(35));
    }

    private record P(String name, String team, String label, boolean spectator) {
        Job job() {
            return team == null ? Job.NONE : TabRules.job(team, List.of(new Segment("[" + label + "] ", 0xFFFFFF)));
        }
    }

    @Test
    void leTriSuitLeMetierPuisLePseudo() {
        List<P> joueurs = new ArrayList<>(List.of(
                new P("Admin_Fantome", "ofas_citoyen", "Citoyen", true),
                new P("xX_Coco_Xx", "ofas_citoyen", "Citoyen", false),
                new P("Capitaine_Marin", "ofas_port", "Responsable du port de pêche", false),
                new P("sans_equipe", null, null, false),
                new P("Mia_Survie", "ofas_citoyen", "Citoyen", false),
                new P("Pecheur_Noe", "ofas_pecheur", "Pêcheur", false),
                new P("Lucky_Vince", "ofas_casino", "gérant de casino", false),
                new P("Lila_RP", "ofas_medecin", "Médecin", false),
                new P("Agent_Kovak", "ofas_policier", "Policier", false),
                new P("Docteur_Ash", "ofas_medecin", "Médecin", false),
                new P("Bricoleur_Jo", "ofas_ingenieur", "Ingénieur", false),
                new P("Nina_Potager", "ofas_agriculteur", "Agriculteur", false)));
        joueurs.sort(TabRules.order(P::spectator, P::job, P::name));
        assertEquals(List.of("Docteur_Ash", "Lila_RP", "Bricoleur_Jo", "Agent_Kovak", "Nina_Potager", "Lucky_Vince",
                "Pecheur_Noe", "Capitaine_Marin", "Mia_Survie", "xX_Coco_Xx", "sans_equipe", "Admin_Fantome"),
                joueurs.stream().map(P::name).toList());
    }
}
