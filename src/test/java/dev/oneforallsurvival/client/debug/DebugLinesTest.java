package dev.oneforallsurvival.client.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.client.debug.DebugLines.Classified;
import dev.oneforallsurvival.client.debug.DebugLines.Line;
import dev.oneforallsurvival.client.debug.DebugLines.Panel;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Les lignes de l'échantillon reprennent les formats relevés par javap sur
 * {@code DebugScreenOverlay} (jar mappé forge-1.20.1-47.4.28, noms officiels) : constantes de
 * format et recettes de concaténation ({@code \u0001} = valeur). Valeurs numériques inventées,
 * formats réels. Provenance de chaque format : voir les commentaires de {@link #gauche} et
 * {@link #droite}.
 */
class DebugLinesTest {

    private static final String SECTION = "§";

    /**
     * Texte de gauche en solo, hors mode réduit : {@code getGameInformation()} + {@code ""} + les deux
     * lignes ajoutées par {@code DebugScreenOverlay.getLeft()} (recette « Debug: Pie [shift]: ... »).
     */
    private static List<String> gauche(int fps, double x) {
        return List.of(
            // recette « Minecraft \1 (\1/\1\1) », getClientModName() = "forge"
            "Minecraft 1.20.1 (1.20.1/forge)",
            // Minecraft.fpsString, format « %d fps T: %s%s%s%s B: %d%s »
            fps + " fps T: 120 vsync fancy-clouds B: 2",
            // format « Integrated server @ %.0f ms ticks, %.0f tx, %.0f rx »
            "Integrated server @ 1 ms ticks, 0 tx, 0 rx",
            // LevelRenderer.getChunkStatistics, format « C: %d/%d %sD: %d, %s »
            "C: 412/12000 D: 12, pC: 000, pU: 00, aB: 12",
            // LevelRenderer.getEntityStatistics, recette « E: \1/\1, B: \1, SD: \1 »
            "E: 12/80, B: 0, SD: 12",
            // recette « P: \1. T: \1 » (particules et entités)
            "P: 134. T: 80",
            // ClientLevel.gatherChunkSourceStats, recette « Chunks[C] W: \1 E: \1 »
            "Chunks[C] W: 441, 306 E: 80,3,0,0,0,0",
            // ServerLevel.gatherChunkSourceStats, recette « Chunks[S] W: \1 E: \1 »
            "Chunks[S] W: 2209 E: 36,130,4,4,0,0,0",
            // recette « \1 FC: \1 » (dimension et chunks forcés)
            "minecraft:overworld FC: 0",
            "",
            // format « XYZ: %.3f / %.5f / %.3f »
            String.format(java.util.Locale.ROOT, "XYZ: %.3f / 64.00000 / -5.678", x),
            // format « Block: %d %d %d [%d %d %d] »
            "Block: 12 64 -6 [12 0 10]",
            // format « Chunk: %d %d %d [%d %d in r.%d.%d.mca] »
            "Chunk: 0 4 0 [0 0 in r.0.0.mca]",
            // format « Facing: %s (%s) (%.1f / %.1f) », texte « Towards negative Z » relevé
            "Facing: north (Towards negative Z) (-1.2 / 3.4)",
            // recette « Client Light: \1 (\1 sky, \1 block) »
            "Client Light: 15 (15 sky, 0 block)",
            // construit par StringBuilder : « CH » puis « S: 64 O: 64 M: 63 ML: 63 » (SW, S, OW, O, M, ML)
            "CH S: 64 O: 64 M: 63 ML: 63",
            "SH S: 64 O: 64 M: 63 ML: 63",
            // recette « Biome: \1 »
            "Biome: minecraft:plains",
            // format « Local Difficulty: %.2f // %.2f (Day %d) »
            "Local Difficulty: 2.25 // 0.00 (Day 0)",
            // constante « Blending: Old »
            "Blending: Old",
            // recette « SC: \1, \1 »
            "SC: 71, M: 14, A: 5",
            // recette « Shader: \1 » (PostChain.getName)
            "Shader: minecraft:shaders/post/creeper.json",
            // SoundEngine : « Sounds: %d/%d + %d/%d » puis format « (Mood %d%%) »
            "Sounds: 0/247 + 0/8 (Mood 0%)",
            "",
            // ForgeDebugScreenOverlay.getLeft : recette « Debug: Pie [shift]: \1\1 [alt]: \1 »
            "Debug: Pie [shift]: hidden FPS + TPS [alt]: hidden",
            // constante « For help: press F3 + Q »
            "For help: press F3 + Q");
    }

    /**
     * Texte de droite : {@code getSystemInformation()}, avec bloc, fluide puis entité visés. Le soulignement
     * est un code de mise en forme (ChatFormatting.UNDERLINE), les propriétés booléennes sont colorées.
     */
    private static List<String> droite(int memPct) {
        return List.of(
            // format « Java: %s %dbit »
            "Java: 17.0.8 64bit",
            // format « Mem: % 2d%% %03d/%03dMB »
            "Mem: " + memPct + "% 1234/4096MB",
            // format « Allocation rate: %03dMB /s »
            "Allocation rate: 123MB /s",
            // format « Allocated: % 2d%% %03dMB »
            "Allocated: 60% 2457MB",
            "",
            // format « CPU: %s »
            "CPU: 8x AMD Ryzen 7 5700X",
            "",
            // format « Display: %dx%d (%s) », puis GlUtil.getRenderer() et GlUtil.getOpenGLVersion()
            "Display: 1920x1080 (NVIDIA Corporation)",
            "NVIDIA GeForce RTX 3070/PCIe/SSE2",
            "4.6.0 NVIDIA 535.98",
            "",
            // recette « \1Targeted Block: \1, \1, \1 » (\1 = UNDERLINE), puis id de registre, propriétés, tags
            SECTION + "nTargeted Block: 12, 63, -6",
            "minecraft:grass_block",
            // getPropertyValueString : nom + « : » + valeur, booléens colorés (vert/rouge)
            SECTION + "csnowy: " + SECTION + "cfalse",
            // recette « #\1 » pour chaque étiquette
            "#minecraft:dirt",
            "#minecraft:mineable/shovel",
            "",
            // recette « \1Targeted Fluid: \1, \1, \1 »
            SECTION + "nTargeted Fluid: 12, 62, -6",
            "minecraft:water",
            "level: 8",
            "#minecraft:water",
            "",
            // recette « \1Targeted Entity », puis id, puis étiquettes ajoutées par le patch Forge de
            // DebugScreenOverlay (patches/.../DebugScreenOverlay.java.patch : list.add("#" + tag))
            SECTION + "nTargeted Entity",
            "minecraft:cow",
            "#minecraft:can_breathe_under_water");
    }

    /** Texte du mode réduit : branche {@code showOnlyReducedInfo()} de {@code getGameInformation()} (9 entrées). */
    private static List<String> gaucheReduit() {
        return List.of(
            "Minecraft 1.20.1 (1.20.1/forge)",
            "60 fps T: 120 vsync fancy-clouds B: 2",
            "\"forge\" server, 0 tx, 0 rx",
            "C: 412/12000 D: 12, pC: 000, pU: 00, aB: 12",
            "E: 12/80, B: 0, SD: 12",
            "P: 134. T: 80",
            "Chunks[C] W: 441, 306 E: 80,3,0,0,0,0",
            "",
            // format « Chunk-relative: %d %d %d »
            "Chunk-relative: 12 4 10",
            "",
            "Debug: Pie [shift]: hidden FPS [alt]: hidden",
            "For help: press F3 + Q");
    }

    /** Droite en mode réduit : {@code getSystemInformation()} s'arrête après la ligne OpenGL. */
    private static List<String> droiteReduit() {
        return droite(45).subList(0, 10);
    }

    private static Panel panelOf(Classified c, String key) {
        for (Panel p : Panel.values()) {
            for (Line l : c.lines(p)) {
                if (l.key().equals(key)) {
                    return p;
                }
            }
        }
        return null;
    }

    private static int total(Classified c) {
        int n = 0;
        for (Panel p : Panel.values()) {
            n += c.lines(p).size();
        }
        return n;
    }

    private static long nonVides(List<String> l) {
        return l.stream().filter(s -> !s.isBlank()).count();
    }

    @Test
    void chaqueLigneVanillaASonPanneau() {
        Classified c = DebugLines.classify(gauche(60, 12.345), droite(30), false);

        // Position
        assertEquals(Panel.POSITION, panelOf(c, "xyz"));
        assertEquals(Panel.POSITION, panelOf(c, "block"));
        assertEquals(Panel.POSITION, panelOf(c, "chunk"));
        assertEquals(Panel.POSITION, panelOf(c, "facing"));
        // Monde
        assertEquals(Panel.WORLD, panelOf(c, "biome"));
        assertEquals(Panel.WORLD, panelOf(c, "light.client"));
        assertEquals(Panel.WORLD, panelOf(c, "difficulty"));
        assertEquals(Panel.WORLD, panelOf(c, "dimension"));
        // Performances
        assertEquals(Panel.PERFORMANCE, panelOf(c, "fps"));
        assertEquals(Panel.PERFORMANCE, panelOf(c, "server"));
        assertEquals(Panel.PERFORMANCE, panelOf(c, "mem"));
        assertEquals(Panel.PERFORMANCE, panelOf(c, "alloc.rate"));
        assertEquals(Panel.PERFORMANCE, panelOf(c, "alloc"));
        // Cible
        assertEquals(Panel.TARGET, panelOf(c, "target.block.pos"));
        assertEquals(Panel.TARGET, panelOf(c, "target.block"));
        assertEquals(Panel.TARGET, panelOf(c, "target.fluid.pos"));
        assertEquals(Panel.TARGET, panelOf(c, "target.fluid"));
        assertEquals(Panel.TARGET, panelOf(c, "target.entity.header"));
        assertEquals(Panel.TARGET, panelOf(c, "target.entity"));
        // Vue détaillée seulement
        for (String k : List.of("version", "render.chunks", "render.entities", "particles", "chunks.client",
            "chunks.server", "heightmap.client", "heightmap.server", "blending", "spawn", "shader", "sounds",
            "hint.charts", "hint.help", "java", "cpu", "display", "gpu", "opengl",
            "target.block.prop.snowy", "target.block.tag0", "target.block.tag1", "target.fluid.prop.level",
            "target.fluid.tag0", "target.entity.tag0")) {
            assertEquals(Panel.DETAIL_ONLY, panelOf(c, k), k);
        }
        // Rien de vanilla ne tombe dans Mods
        assertTrue(c.lines(Panel.MODS).isEmpty(), c.lines(Panel.MODS).toString());
    }

    @Test
    void uneLigneInconnueVaAuPanneauMods() {
        List<String> g = new ArrayList<>(gauche(60, 1));
        g.add("Create: stress 1024 su");
        g.add("Ligne sans libelle 42");
        List<String> d = new ArrayList<>(droite(30));
        d.add("JEI: 1234 ingredients");
        // un format vanilla inattendu (version future) ne doit pas disparaitre non plus
        d.add(SECTION + "6Quelque chose: de nouveau");
        Classified c = DebugLines.classify(g, d, false);

        List<String> mods = c.lines(Panel.MODS).stream().map(Line::text).collect(Collectors.toList());
        assertEquals(List.of("Create: stress 1024 su", "Ligne sans libelle 42", "JEI: 1234 ingredients",
            SECTION + "6Quelque chose: de nouveau"), mods);
    }

    @Test
    void uneLigneModApresLaCibleResteDansMods() {
        List<String> d = new ArrayList<>(droite(30));
        d.add("Journey: x 10 z 20");
        Classified c = DebugLines.classify(gauche(60, 1), d, false);
        assertEquals(1, c.lines(Panel.MODS).size());
        assertEquals("Journey: x 10 z 20", c.lines(Panel.MODS).get(0).text());
        assertEquals(Panel.DETAIL_ONLY, panelOf(c, "target.entity.tag0"));
    }

    @Test
    void toutesLesLignesSontQuelquePart() {
        List<String> g = new ArrayList<>(gauche(60, 1));
        g.add("Mod inconnu 7");
        List<String> d = new ArrayList<>(droite(30));
        d.add("Autre: chose");
        Classified c = DebugLines.classify(g, d, false);
        assertEquals(nonVides(g) + nonVides(d), total(c));
        // le texte est conserve tel quel, codes de mise en forme compris
        assertTrue(c.lines(Panel.DETAIL_ONLY).stream().anyMatch(l -> l.text().equals(SECTION + "csnowy: " + SECTION + "cfalse")));
        // aucune cle dupliquee dans l'ensemble
        List<String> keys = new ArrayList<>();
        for (Panel p : Panel.values()) {
            c.lines(p).forEach(l -> keys.add(l.key()));
        }
        assertEquals(keys.size(), keys.stream().distinct().count());
    }

    @Test
    void enModeReduitRienNeSitueLeJoueur() {
        Classified c = DebugLines.classify(gaucheReduit(), droiteReduit(), true);
        assertSansPosition(c);
        // et rien n'est perdu de ce que vanilla affiche encore
        assertEquals(nonVides(gaucheReduit()) + nonVides(droiteReduit()), total(c));
        assertTrue(c.lines(Panel.TARGET).isEmpty());
        assertTrue(c.lines(Panel.POSITION).stream().allMatch(l -> l.key().equals("chunkrel")));

        // meme si un relevé complet arrive en mode réduit (gamerule changee en cours de route),
        // les lignes qui situent le joueur sont retirees
        Classified plein = DebugLines.classify(gauche(60, 1), droite(30), true);
        assertSansPosition(plein);
        assertTrue(plein.lines(Panel.TARGET).isEmpty());
        assertEquals(Panel.PERFORMANCE, panelOf(plein, "fps"));
    }

    private static void assertSansPosition(Classified c) {
        List<String> interdits = List.of("XYZ:", "Block:", "Chunk: ", "Facing:", "Client Light:", "Server Light:",
            "Biome:", "Local Difficulty:", "Blending:", "SC:", "CH ", "SH ", "Targeted", "FC:", "Waiting for chunk");
        for (Panel p : Panel.values()) {
            for (Line l : c.lines(p)) {
                for (String pref : interdits) {
                    assertFalse(l.text().replace(SECTION + "n", "").contains(pref), l.text());
                }
            }
        }
        assertTrue(c.lines(Panel.WORLD).isEmpty());
    }

    @Test
    void lesClesSontStables() {
        Classified a = DebugLines.classify(gauche(60, 12.345), droite(30), false);
        Classified b = DebugLines.classify(gauche(143, -987.654), droite(71), false);
        for (Panel p : Panel.values()) {
            List<String> ka = a.lines(p).stream().map(Line::key).collect(Collectors.toList());
            List<String> kb = b.lines(p).stream().map(Line::key).collect(Collectors.toList());
            assertEquals(ka, kb, p.name());
        }
        // et les textes ont bien change : seul l'identifiant est stable
        assertFalse(a.lines(Panel.PERFORMANCE).get(0).text().equals(b.lines(Panel.PERFORMANCE).get(0).text()));
    }

    @Test
    void lesClesDesModsNeDependentPasDesChiffres() {
        Classified a = DebugLines.classify(List.of("Create: stress 1024 su", "Bruit 12"), List.of(), false);
        Classified b = DebugLines.classify(List.of("Create: stress 8 su", "Bruit 99"), List.of(), false);
        assertEquals(a.lines(Panel.MODS).stream().map(Line::key).collect(Collectors.toList()),
            b.lines(Panel.MODS).stream().map(Line::key).collect(Collectors.toList()));
    }

    @Test
    void desLignesVidesOuNullesNeFontRien() {
        List<String> g = new ArrayList<>();
        g.add("");
        g.add(null);
        Classified c = DebugLines.classify(g, List.of(), false);
        assertEquals(0, total(c));
    }
}
