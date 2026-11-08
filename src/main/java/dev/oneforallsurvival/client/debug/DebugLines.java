package dev.oneforallsurvival.client.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Classement des lignes du F3 (vanilla 1.20.1 et autres mods) en panneaux.
 *
 * <p>Logique pure : aucune classe de Minecraft, pour que JUnit la teste sans le jeu. Les formats
 * reconnus sont ceux de {@code DebugScreenOverlay.getGameInformation/getSystemInformation} et de
 * {@code ForgeGui.renderHUDText} (1.20.1, Forge 47.4.28), relevés par javap. Règles :
 * <ul>
 *   <li>une ligne vide est ignorée (elle ne porte aucune information) ;</li>
 *   <li>une ligne non reconnue va au panneau {@link Panel#MODS}, jamais perdue ;</li>
 *   <li>en mode réduit, les lignes qui situent le joueur sont retirées, comme le fait vanilla.</li>
 * </ul>
 * La clé de chaque ligne ne dépend pas des chiffres : deux relevés successifs donnent les mêmes
 * clés, ce qui permet d'animer un chiffre d'une image à l'autre sans clignotement.
 */
public final class DebugLines {

    private DebugLines() {}

    /** Panneaux de destination. */
    public enum Panel { POSITION, WORLD, PERFORMANCE, TARGET, MODS, DETAIL_ONLY }

    /** Une ligne classée : {@code key} stable d'un relevé à l'autre, {@code text} tel que reçu (codes de mise en forme compris). */
    public record Line(String key, String text) {}

    /** Résultat : toutes les valeurs de {@link Panel} sont présentes, éventuellement avec une liste vide. */
    public record Classified(Map<Panel, List<Line>> panels) {
        public List<Line> lines(Panel panel) {
            return panels.getOrDefault(panel, List.of());
        }
    }

    /** Codes de mise en forme (le caractère section suivi d'un caractère). */
    private static final Pattern FORMAT_CODES = Pattern.compile("§.");

    // Formats relevés dans DebugScreenOverlay / LevelRenderer / Minecraft (voir DebugLinesTest).
    private static final Pattern FPS = Pattern.compile("^\\d+ fps T: .*");
    private static final Pattern SERVER = Pattern.compile("^(Integrated server @ .*|\".*\" server, .*)");
    private static final Pattern CHUNK_RENDER = Pattern.compile("^C: \\d+/\\d+ .*");
    private static final Pattern ENTITY_RENDER = Pattern.compile("^E: \\d+/\\d+, B: \\d+, SD: \\d+$");
    private static final Pattern PARTICLES = Pattern.compile("^P: .*\\. T: \\d+$");
    private static final Pattern DIMENSION = Pattern.compile("^\\S+:\\S+ FC: \\d+$");
    private static final Pattern HEIGHTMAP = Pattern.compile("^(CH|SH)( [A-Z]+: (-?\\d+|\\?\\?))+$");
    /** Propriété d'état d'un bloc ou fluide visé : « nom: valeur » en minuscules, sans espace. */
    private static final Pattern PROPERTY = Pattern.compile("^[a-z][a-z0-9_]*: \\S+$");
    /** Identifiant de registre « espace:chemin » (bloc, fluide ou entité visés). */
    private static final Pattern REGISTRY_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    /** Section du texte de droite en cours de lecture (cible). */
    private enum Section { NONE, BLOCK, FLUID, ENTITY }

    /** Règle trouvée pour une ligne : panneau, clé, et si la ligne situe le joueur. */
    private record Rule(Panel panel, String key, boolean situating) {}

    /**
     * @param left    lignes de gauche (jeu), telles que Forge les remet à l'événement
     * @param right   lignes de droite (système), idem
     * @param reduced vrai si {@code Minecraft.showOnlyReducedInfo()} : on ne montre rien qui situe le joueur
     */
    public static Classified classify(List<String> left, List<String> right, boolean reduced) {
        Map<Panel, List<Line>> out = new EnumMap<>(Panel.class);
        for (Panel p : Panel.values()) {
            out.put(p, new ArrayList<>());
        }
        Map<String, Integer> seen = new HashMap<>();

        for (String raw : left) {
            if (strip(raw).isBlank()) {
                continue;
            }
            put(out, seen, leftRule(strip(raw)), raw, reduced);
        }

        Section section = Section.NONE;
        int afterDisplay = 0; // lignes sans préfixe qui suivent « Display: » : GPU puis version OpenGL
        int tagCount = 0;
        for (String raw : right) {
            String s = strip(raw);
            if (s.isBlank()) {
                section = Section.NONE;
                continue;
            }
            Rule r;
            if (s.startsWith("Targeted Block: ")) {
                section = Section.BLOCK;
                tagCount = 0;
                r = new Rule(Panel.TARGET, "target.block.pos", true);
            } else if (s.startsWith("Targeted Fluid: ")) {
                section = Section.FLUID;
                tagCount = 0;
                r = new Rule(Panel.TARGET, "target.fluid.pos", true);
            } else if (s.equals("Targeted Entity")) {
                section = Section.ENTITY;
                tagCount = 0;
                r = new Rule(Panel.TARGET, "target.entity.header", true);
            } else if (section != Section.NONE) {
                r = sectionRule(section, s, tagCount);
                if (s.startsWith("#")) {
                    tagCount++;
                }
            } else {
                r = rightRule(s);
                if (r == null && afterDisplay > 0) {
                    r = new Rule(Panel.DETAIL_ONLY, afterDisplay == 2 ? "gpu" : "opengl", false);
                }
                if (r == null) {
                    r = mod(s);
                }
            }
            if (s.startsWith("Display: ")) {
                afterDisplay = 2;
            } else if (afterDisplay > 0) {
                afterDisplay--;
            }
            put(out, seen, r, raw, reduced);
        }

        Map<Panel, List<Line>> frozen = new EnumMap<>(Panel.class);
        out.forEach((p, l) -> frozen.put(p, List.copyOf(l)));
        return new Classified(Collections.unmodifiableMap(frozen));
    }

    private static void put(Map<Panel, List<Line>> out, Map<String, Integer> seen, Rule r, String raw, boolean reduced) {
        if (reduced && r.situating()) {
            return;
        }
        int n = seen.merge(r.key(), 1, Integer::sum);
        String key = n == 1 ? r.key() : r.key() + "#" + n;
        out.get(r.panel()).add(new Line(key, raw));
    }

    private static String strip(String raw) {
        return raw == null ? "" : FORMAT_CODES.matcher(raw).replaceAll("");
    }

    /** Règle d'une ligne de gauche non vide, sans codes de mise en forme. */
    private static Rule leftRule(String s) {
        if (s.startsWith("Minecraft ")) return new Rule(Panel.DETAIL_ONLY, "version", false);
        if (FPS.matcher(s).matches()) return new Rule(Panel.PERFORMANCE, "fps", false);
        if (SERVER.matcher(s).matches()) return new Rule(Panel.PERFORMANCE, "server", false);
        if (CHUNK_RENDER.matcher(s).matches()) return new Rule(Panel.DETAIL_ONLY, "render.chunks", false);
        if (ENTITY_RENDER.matcher(s).matches()) return new Rule(Panel.DETAIL_ONLY, "render.entities", false);
        if (PARTICLES.matcher(s).matches()) return new Rule(Panel.DETAIL_ONLY, "particles", false);
        if (s.startsWith("Chunks[C]")) return new Rule(Panel.DETAIL_ONLY, "chunks.client", false);
        if (s.startsWith("Chunks[S]")) return new Rule(Panel.DETAIL_ONLY, "chunks.server", false);
        if (DIMENSION.matcher(s).matches()) return new Rule(Panel.WORLD, "dimension", true);
        if (s.startsWith("Chunk-relative: ")) return new Rule(Panel.POSITION, "chunkrel", false);
        if (s.startsWith("XYZ: ")) return new Rule(Panel.POSITION, "xyz", true);
        if (s.startsWith("Block: ")) return new Rule(Panel.POSITION, "block", true);
        if (s.startsWith("Chunk: ")) return new Rule(Panel.POSITION, "chunk", true);
        if (s.startsWith("Facing: ")) return new Rule(Panel.POSITION, "facing", true);
        if (s.startsWith("Waiting for chunk")) return new Rule(Panel.DETAIL_ONLY, "waiting", true);
        if (s.startsWith("Client Light: ")) return new Rule(Panel.WORLD, "light.client", true);
        if (s.startsWith("Server Light: ")) return new Rule(Panel.WORLD, "light.server", true);
        if (HEIGHTMAP.matcher(s).matches()) {
            return new Rule(Panel.DETAIL_ONLY, s.startsWith("CH") ? "heightmap.client" : "heightmap.server", true);
        }
        if (s.startsWith("Biome: ")) return new Rule(Panel.WORLD, "biome", true);
        if (s.startsWith("Local Difficulty: ")) return new Rule(Panel.WORLD, "difficulty", true);
        if (s.startsWith("Blending: ")) return new Rule(Panel.DETAIL_ONLY, "blending", true);
        if (s.startsWith("SC: ")) return new Rule(Panel.DETAIL_ONLY, "spawn", true);
        if (s.startsWith("Shader: ")) return new Rule(Panel.DETAIL_ONLY, "shader", false);
        if (s.startsWith("Sounds: ")) return new Rule(Panel.DETAIL_ONLY, "sounds", false);
        if (s.startsWith("Debug: Pie")) return new Rule(Panel.DETAIL_ONLY, "hint.charts", false);
        if (s.startsWith("For help: press F3")) return new Rule(Panel.DETAIL_ONLY, "hint.help", false);
        return mod(s);
    }

    /** Règle d'une ligne de droite hors section « cible » ; {@code null} si elle n'est pas reconnue. */
    private static Rule rightRule(String s) {
        if (s.startsWith("Java: ")) return new Rule(Panel.DETAIL_ONLY, "java", false);
        if (s.startsWith("Mem: ")) return new Rule(Panel.PERFORMANCE, "mem", false);
        if (s.startsWith("Allocation rate: ")) return new Rule(Panel.PERFORMANCE, "alloc.rate", false);
        if (s.startsWith("Allocated: ")) return new Rule(Panel.PERFORMANCE, "alloc", false);
        if (s.startsWith("CPU: ")) return new Rule(Panel.DETAIL_ONLY, "cpu", false);
        if (s.startsWith("Display: ")) return new Rule(Panel.DETAIL_ONLY, "display", false);
        return null;
    }

    /** Une ligne située sous « Targeted Block/Fluid/Entity » : identifiant, propriété d'état ou étiquette. */
    private static Rule sectionRule(Section section, String s, int tagCount) {
        String base = switch (section) {
            case BLOCK -> "target.block";
            case FLUID -> "target.fluid";
            default -> "target.entity";
        };
        if (s.startsWith("#")) {
            return new Rule(Panel.DETAIL_ONLY, base + ".tag" + tagCount, true);
        }
        if (section != Section.ENTITY && PROPERTY.matcher(s).matches()) {
            return new Rule(Panel.DETAIL_ONLY, base + ".prop." + s.substring(0, s.indexOf(':')), true);
        }
        if (REGISTRY_ID.matcher(s).matches() || s.startsWith("[unregistered ")) {
            return new Rule(Panel.TARGET, base, true);
        }
        // Ni identifiant ni propriété : probablement une ligne ajoutée par un autre mod.
        return mod(s);
    }

    /** Ligne inconnue : panneau Mods, clé tirée du libellé (avant « : » ou avant le premier chiffre). */
    private static Rule mod(String s) {
        int colon = s.indexOf(':');
        String label = colon > 0 && colon <= 32 ? s.substring(0, colon) : s.replaceFirst("[0-9].*$", "");
        String slug = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", "_").replaceAll("^_+|_+$", "");
        return new Rule(Panel.MODS, "mod." + (slug.isEmpty() ? "line" : slug), false);
    }
}
