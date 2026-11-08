package dev.oneforallsurvival.client.debug;

import dev.oneforallsurvival.client.debug.DebugLines.Line;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Passage d'une ligne du F3 à son affichage en carte : libellé traduit, valeurs mises en avant, axes colorés.
 * Le texte vient toujours de Minecraft : on le découpe sans le recalculer, et une ligne de format inattendu
 * s'affiche telle quelle. Logique pure (les traductions et les noms arrivent par {@link Labels} et {@link Names}).
 */
public final class DebugFormat {

    /** Préfixe des clés de langue du F3. */
    public static final String PREFIX = "gui.oneforallsurvival.f3.";

    /** Rôle d'un morceau de texte, associé à une couleur au dessin. */
    public enum Tone { LABEL, VALUE, X, Y, Z }

    public record Segment(String text, Tone tone) {}

    /** Une ligne de carte. */
    public record Row(String key, List<Segment> segments) {
        /** Le texte à plat, pour la vue détaillée. */
        public String text() {
            StringBuilder out = new StringBuilder();
            for (Segment s : segments) {
                out.append(s.text());
            }
            return out.toString();
        }
    }

    /** Traduit une clé de langue complète. */
    @FunctionalInterface
    public interface Labels {
        String get(String key);
    }

    /** Nom d'affichage de l'identifiant d'une cible, ou {@code null} s'il n'y en a pas. */
    @FunctionalInterface
    public interface Names {
        @Nullable String of(String lineKey, String id);
    }

    /** Mémoire relevée sur la ligne {@code Mem:}. */
    public record Memory(int percent, long used, long max) {}

    /** Un point du monde. */
    public record Point(double x, double y, double z) {}

    public static final String SEPARATOR = " · ";

    private static final Pattern CODES = Pattern.compile("§.");
    private static final Pattern XYZ = Pattern.compile("^XYZ: (\\S+) / (\\S+) / (\\S+)$");
    private static final Pattern BLOCK = Pattern.compile("^Block: (-?\\d+ -?\\d+ -?\\d+)( \\[.*)?$");
    private static final Pattern CHUNK = Pattern.compile("^Chunk: (-?\\d+ -?\\d+ -?\\d+)( \\[.*)?$");
    private static final Pattern CHUNK_RELATIVE = Pattern.compile("^Chunk-relative: (.+)$");
    private static final Pattern FACING = Pattern.compile("^Facing: (\\S+) \\(.*\\) \\((\\S+) / (\\S+)\\)$");
    private static final Pattern DIMENSION = Pattern.compile("^(\\S+) FC: \\d+$");
    private static final Pattern BIOME = Pattern.compile("^Biome: (.+)$");
    private static final Pattern CLIENT_LIGHT = Pattern.compile("^Client Light: (\\d+) \\((\\d+) sky, (\\d+) block\\)$");
    private static final Pattern SERVER_LIGHT = Pattern.compile("^Server Light: \\((\\d+) sky, (\\d+) block\\)$");
    private static final Pattern DIFFICULTY = Pattern.compile("^Local Difficulty: (\\S+) // \\S+ \\(Day (\\d+)\\)$");
    private static final Pattern TARGET_BLOCK = Pattern.compile("^Targeted Block: (.+)$");
    private static final Pattern TARGET_FLUID = Pattern.compile("^Targeted Fluid: (.+)$");
    private static final Pattern TARGET_BLOCK_POS = Pattern.compile("^Targeted Block: (-?\\d+), (-?\\d+), (-?\\d+)$");
    private static final Pattern PROPERTY = Pattern.compile("^([^:]+): (.+)$");
    private static final Pattern MEMORY = Pattern.compile("^Mem:\\s*(\\d+)% (\\d+)/(\\d+)MB$");
    private static final Pattern FPS = Pattern.compile("^(\\d+) fps\\b.*");
    /** Libellé sans mise en forme, de longueur raisonnable : une ligne d'un mod se lit comme les autres. */
    private static final Pattern LABELLED = Pattern.compile("^([^:§]{1,32}): (.+)$");
    private static final Set<String> DIRECTIONS = Set.of("north", "south", "east", "west", "up", "down");
    /** Propriétés d'état montrées sur la carte Cible ; toutes restent dans la vue détaillée. */
    private static final int TARGET_PROPERTIES = 3;
    private static final long TICKS_PER_DAY = 24000;
    private static final long TICKS_PER_HOUR = 1000;
    /** Le tick 0 du jour est 6 h du matin. */
    private static final long DAWN_TICKS = 6000;

    private DebugFormat() {}

    /** La clé de langue complète d'un suffixe. */
    public static String key(String suffix) {
        return PREFIX + suffix;
    }

    public static List<Row> rows(List<Line> lines, Labels labels, Names names) {
        List<Row> out = new ArrayList<>(lines.size());
        for (Line line : lines) {
            out.add(row(line, labels, names));
        }
        return out;
    }

    /** L'affichage d'une ligne en vue normale. */
    public static Row row(Line line, Labels labels, Names names) {
        Row row = known(line.key(), plain(line.text()), labels, names);
        return row != null ? row : raw(line);
    }

    /** L'affichage d'une ligne de format connu, {@code null} sinon. */
    @Nullable
    private static Row known(String k, String text, Labels l, Names names) {
        Matcher m;
        switch (k) {
            case "xyz":
                if ((m = XYZ.matcher(text)).matches()) {
                    return new Row(k, List.of(
                            new Segment("X " + oneDecimal(m.group(1)), Tone.X), separator(),
                            new Segment("Y " + oneDecimal(m.group(2)), Tone.Y), separator(),
                            new Segment("Z " + oneDecimal(m.group(3)), Tone.Z)));
                }
                return null;
            case "block":
                return (m = BLOCK.matcher(text)).matches() ? labelled(k, l, "label.block", m.group(1)) : null;
            case "chunk":
                return (m = CHUNK.matcher(text)).matches() ? labelled(k, l, "label.chunk", m.group(1)) : null;
            case "chunkrel":
                return (m = CHUNK_RELATIVE.matcher(text)).matches() ? labelled(k, l, "label.chunk_relative", m.group(1)) : null;
            case "facing":
                if ((m = FACING.matcher(text)).matches()) {
                    String direction = m.group(1);
                    String shown = DIRECTIONS.contains(direction) ? l.get(key("direction." + direction)) : direction;
                    return new Row(k, List.of(label(l, "label.facing"), new Segment(" " + shown, Tone.VALUE),
                            new Segment(" (" + m.group(2) + "° / " + m.group(3) + "°)", Tone.LABEL)));
                }
                return null;
            case "dimension":
                return (m = DIMENSION.matcher(text)).matches() ? labelled(k, l, "label.dimension", m.group(1)) : null;
            case "biome":
                return (m = BIOME.matcher(text)).matches() ? labelled(k, l, "label.biome", m.group(1)) : null;
            case "light.client":
                return (m = CLIENT_LIGHT.matcher(text)).matches()
                        ? light(k, l, "label.light", m.group(1), m.group(2), m.group(3)) : null;
            case "light.server":
                return (m = SERVER_LIGHT.matcher(text)).matches()
                        ? light(k, l, "label.light_server", null, m.group(1), m.group(2)) : null;
            case "difficulty":
                // Le jour figure avec l'heure, sur sa propre ligne.
                return (m = DIFFICULTY.matcher(text)).matches() ? labelled(k, l, "label.difficulty", m.group(1)) : null;
            case "target.block.pos":
                return (m = TARGET_BLOCK.matcher(text)).matches() ? labelled(k, l, "label.target_block", m.group(1)) : null;
            case "target.fluid.pos":
                return (m = TARGET_FLUID.matcher(text)).matches() ? labelled(k, l, "label.target_fluid", m.group(1)) : null;
            case "target.entity.header":
                return new Row(k, List.of(label(l, "label.target_entity")));
            case "target.block", "target.fluid", "target.entity":
                String name = names.of(k, text);
                return name == null || name.isEmpty()
                        ? new Row(k, List.of(new Segment(text, Tone.VALUE)))
                        : new Row(k, List.of(new Segment(name, Tone.VALUE), new Segment(" " + text, Tone.LABEL)));
            default:
                return null;
        }
    }

    /**
     * La carte Cible : les lignes de cible, chaque identifiant de bloc ou de fluide suivi de ses premières propriétés
     * d'état (lues dans les lignes de la vue détaillée, où elles restent), puis la distance. Rien sans ligne de cible.
     *
     * @param distance la ligne de distance, {@code null} si elle est inconnue
     */
    public static List<Row> targetRows(List<Line> target, List<Line> detail, Labels labels, Names names, @Nullable Row distance) {
        if (target.isEmpty()) {
            return List.of();
        }
        List<Row> out = new ArrayList<>(target.size() + TARGET_PROPERTIES + 1);
        int room = TARGET_PROPERTIES;
        for (Line line : target) {
            out.add(row(line, labels, names));
            if (!line.key().equals("target.block") && !line.key().equals("target.fluid")) {
                continue;
            }
            String prefix = line.key() + ".prop.";
            for (Line candidate : detail) {
                if (room > 0 && candidate.key().startsWith(prefix)) {
                    Matcher m = PROPERTY.matcher(plain(candidate.text()));
                    if (m.matches()) {
                        out.add(new Row(candidate.key(), List.of(new Segment(m.group(1), Tone.LABEL), new Segment(" " + m.group(2), Tone.VALUE))));
                        room--;
                    }
                }
            }
        }
        if (distance != null) {
            out.add(distance);
        }
        return out;
    }

    /** « Jour 14, 06:12 ». Le numéro du jour est celui de vanilla : il change à 6 h, au tick 0 du jour. */
    public static Row dayTimeRow(long dayTime, Labels l) {
        long day = Math.floorDiv(dayTime, TICKS_PER_DAY);
        long clock = Math.floorMod(dayTime + DAWN_TICKS, TICKS_PER_DAY);
        long hours = clock / TICKS_PER_HOUR;
        long minutes = clock % TICKS_PER_HOUR * 60 / TICKS_PER_HOUR;
        return new Row("world.time", List.of(label(l, "label.day"),
                new Segment(String.format(Locale.ROOT, " %d, %02d:%02d", day, hours, minutes), Tone.VALUE)));
    }

    /** La météo : l'orage prime sur la pluie. */
    public static Row weatherRow(boolean raining, boolean thundering, Labels l) {
        String value = thundering ? "value.thunder" : raining ? "value.rain" : "value.clear";
        return new Row("world.weather", List.of(label(l, "label.weather"), new Segment(" " + l.get(key(value)), Tone.VALUE)));
    }

    public static Row distanceRow(double blocks, Labels l) {
        return new Row("target.distance", List.of(label(l, "label.distance"),
                new Segment(" " + SmoothLines.format(blocks, 1) + " " + l.get(key("unit.blocks")), Tone.VALUE)));
    }

    /**
     * Le bas de la carte Performances. Seul : « Serveur 20.0 TPS » ou « Ping 32 ms » ; les deux : « Ping 32 ms · 20.0 TPS »,
     * car la carte est trop étroite pour deux libellés.
     *
     * @return {@code null} si l'on ne connaît ni l'un ni l'autre
     */
    @Nullable
    public static Row footerRow(@Nullable Double tps, @Nullable Integer pingMillis, Labels l) {
        if (tps == null && pingMillis == null) {
            return null;
        }
        List<Segment> segments = new ArrayList<>();
        if (pingMillis != null) {
            segments.add(label(l, "label.ping"));
            segments.add(new Segment(" " + pingMillis + " ms", Tone.VALUE));
        }
        if (tps != null) {
            if (pingMillis == null) {
                segments.add(label(l, "label.server"));
                segments.add(new Segment(" ", Tone.VALUE));
            } else {
                segments.add(new Segment(SEPARATOR, Tone.LABEL));
            }
            segments.add(new Segment(SmoothLines.format(tps, 1) + " TPS", Tone.VALUE));
        }
        return new Row("footer", segments);
    }

    public static double distance(Point a, Point b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Le centre du bloc d'une ligne « Targeted Block: x, y, z », {@code null} si le format est inattendu. */
    @Nullable
    public static Point blockCenter(String text) {
        Matcher m = TARGET_BLOCK_POS.matcher(plain(text));
        return m.matches()
                ? new Point(Integer.parseInt(m.group(1)) + 0.5, Integer.parseInt(m.group(2)) + 0.5, Integer.parseInt(m.group(3)) + 0.5)
                : null;
    }

    /** Une ligne sans format connu : affichée telle quelle, mise en forme du mod comprise. */
    private static Row raw(Line line) {
        Matcher m = LABELLED.matcher(line.text());
        if (m.matches()) {
            return new Row(line.key(), List.of(new Segment(m.group(1) + ":", Tone.LABEL), new Segment(" " + m.group(2), Tone.VALUE)));
        }
        return new Row(line.key(), List.of(new Segment(line.text(), Tone.VALUE)));
    }

    private static Row labelled(String key, Labels l, String label, String value) {
        return new Row(key, List.of(label(l, label), new Segment(" " + value, Tone.VALUE)));
    }

    private static Row light(String key, Labels l, String label, @Nullable String main, String sky, String block) {
        String detail = " (" + l.get(key("label.sky")) + " " + sky + ", " + l.get(key("label.block_light")) + " " + block + ")";
        return main == null
                ? new Row(key, List.of(label(l, label), new Segment(detail, Tone.VALUE)))
                : new Row(key, List.of(label(l, label), new Segment(" " + main, Tone.VALUE), new Segment(detail, Tone.LABEL)));
    }

    private static Segment label(Labels l, String suffix) {
        return new Segment(l.get(key(suffix)), Tone.LABEL);
    }

    private static Segment separator() {
        return new Segment(SEPARATOR, Tone.LABEL);
    }

    private static String oneDecimal(String number) {
        try {
            return SmoothLines.format(Double.parseDouble(number), 1);
        } catch (NumberFormatException e) {
            return number;
        }
    }

    /** Le texte sans ses codes de mise en forme. */
    public static String plain(String text) {
        return text.indexOf('§') < 0 ? text : CODES.matcher(text).replaceAll("");
    }

    /** Les images par seconde au début de la ligne {@code fps}, -1 si le format est inattendu. */
    public static int fps(String text) {
        Matcher m = FPS.matcher(plain(text));
        return m.matches() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** La mémoire de la ligne {@code mem}, {@code null} si le format est inattendu. */
    @Nullable
    public static Memory memory(String text) {
        Matcher m = MEMORY.matcher(plain(text));
        return m.matches()
                ? new Memory(Integer.parseInt(m.group(1)), Long.parseLong(m.group(2)), Long.parseLong(m.group(3)))
                : null;
    }

    /**
     * Découpe un texte trop large, aux espaces si possible, sans jamais couper un code de mise en forme ; la
     * couleur en cours est reprise au début de la ligne suivante. Rien du texte n'est perdu.
     *
     * @param maxWidth largeur maximale (au moins 1)
     * @param width    mesure d'un texte (qui ignore les codes de mise en forme, comme {@code Font.width})
     */
    public static List<String> wrap(String text, int maxWidth, ToIntFunction<String> width) {
        if (width.applyAsInt(text) <= maxWidth) {
            return List.of(text);
        }
        List<String> out = new ArrayList<>();
        String rest = text;
        String carry = "";
        while (!rest.isEmpty()) {
            if (width.applyAsInt(carry + rest) <= maxWidth) {
                out.add(carry + rest);
                break;
            }
            int cut = fit(rest, carry, maxWidth, width);
            int space = rest.lastIndexOf(' ', cut);
            // Couper à un espace seulement si la tête montre quelque chose : « §a mot » ne donne pas une ligne vide.
            if (space > 0 && !plain(rest.substring(0, space)).isBlank()) {
                cut = space;
            }
            String head = rest.substring(0, cut);
            out.add(carry + head);
            carry = colour(carry + head);
            rest = rest.substring(cut).stripLeading();
        }
        return out;
    }

    /** Le début de {@code text} qui tient dans {@code maxWidth}, sans couper un code de mise en forme. */
    public static String head(String text, int maxWidth, ToIntFunction<String> width) {
        return text.isEmpty() ? text : text.substring(0, fit(text, "", maxWidth, width));
    }

    /** La plus longue tête de {@code rest} qui tient ; au moins un caractère, pour toujours avancer. */
    private static int fit(String rest, String carry, int maxWidth, ToIntFunction<String> width) {
        int lo = 1;
        int hi = rest.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (width.applyAsInt(carry + rest.substring(0, mid)) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        if (rest.charAt(lo - 1) == '§') {
            lo = lo > 1 ? lo - 1 : Math.min(rest.length(), 2);
        }
        return lo;
    }

    /** Le dernier code de couleur actif à la fin de {@code text}, vide si aucun. */
    private static String colour(String text) {
        String active = "";
        for (int i = text.indexOf('§'); i >= 0 && i + 1 < text.length(); i = text.indexOf('§', i + 2)) {
            char code = Character.toLowerCase(text.charAt(i + 1));
            if (code == 'r') {
                active = "";
            } else if ("0123456789abcdef".indexOf(code) >= 0) {
                active = "§" + code;
            }
        }
        return active;
    }
}
