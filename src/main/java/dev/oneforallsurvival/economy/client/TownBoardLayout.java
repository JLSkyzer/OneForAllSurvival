package dev.oneforallsurvival.economy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * La mise en page du texte du tableau de la mairie (3 blocs de large, 2 de haut), sans rien de graphique (testable). Le
 * parchemin offre {@link #WIDTH} x {@link #HEIGHT} pixels de texte (un bloc en compte 64) ; sous {@link #MIN_SCALE} le
 * texte ne se lit plus à quatre blocs. La mise en page cherche, dans l'ordre :
 * <ol>
 * <li>tout le texte, avec ses sections facultatives (le titre, les annonces du maire), à la plus grande échelle (de 1 à
 * {@link #MIN_SCALE}) qui le fait tenir ;</li>
 * <li>sans les sections facultatives ;</li>
 * <li>puis en gardant de moins en moins de coupures de chaque montant (les plus grandes d'abord, suivies de « … »), le
 * chiffre exact se lisant au chat (clic droit).</li>
 * </ol>
 * Une section est une tête (« Solde : », « Salaires dus : 99 », « Aucun salaire dû ») suivie de coupures (« 9999 rubis
 * »). La tête ne se coupe jamais (sauf si elle est à elle seule plus large que le panneau : alors mot à mot), une coupure
 * jamais ; la section prend le moins de lignes possible, et ses coupures se répartissent de façon équilibrée sur ces
 * lignes (somme des carrés de la place perdue la plus petite), ce qui évite un mot seul en bout de section. Une ligne
 * plus large que le panneau n'est jamais acceptée.
 */
final class TownBoardLayout {

    /** Largeur utile du parchemin en pixels de texte (43 px de texture sur 48 : le cadre de bois et une marge). */
    static final float WIDTH = 172.0F;
    /** Hauteur utile du parchemin en pixels de texte (21 px de texture sur les 26 du panneau). */
    static final float HEIGHT = 84.0F;
    /** Hauteur d'une ligne de texte, en pixels de texte non rétrécis. */
    static final float LINE_HEIGHT = 9.0F;
    /** Part de la hauteur d'une ligne, sous la dernière, qui ne se voit pas. */
    private static final float LEADING = 2.0F;
    /** L'échelle la plus grande (un pixel de texte = 1/64 de bloc). */
    static final float MAX_SCALE = 1.0F;
    /** L'échelle la plus petite qui reste lisible à quatre blocs. */
    static final float MIN_SCALE = 0.75F;
    private static final float STEP = 0.05F;

    /**
     * Une section : {@code head} (une phrase d'un bloc), puis {@code amount} (des coupures, jamais coupées, entre
     * parenthèses si {@code parentheses}) ; une section facultative disparaît quand la place manque.
     */
    record Section(Component head, List<Component> amount, int color, boolean optional, boolean parentheses) {}

    /** Une ligne rangée : son texte et sa couleur. */
    record Line(Component text, int color) {}

    /** La mise en page : l'échelle, les lignes, et vrai si des coupures ont dû être laissées de côté. */
    record Result(float scale, List<Line> lines, boolean shortened) {
        /** Hauteur occupée par les lignes, en pixels de texte de la face (sans l'interligne sous la dernière). */
        float height() {
            return (lines.size() * LINE_HEIGHT - LEADING) * scale;
        }
    }

    private TownBoardLayout() {}

    static Result layout(List<Section> sections, ToIntFunction<Component> width) {
        int most = 1;
        for (Section section : sections) {
            most = Math.max(most, section.amount().size());
        }
        for (int keep = most; keep >= 1; keep--) {
            for (boolean withOptional : new boolean[] {true, false}) {
                List<Section> used = withOptional ? sections : sections.stream().filter(s -> !s.optional()).toList();
                if (used.isEmpty()) {
                    continue;
                }
                for (float scale = MAX_SCALE; scale >= MIN_SCALE - 1.0E-4F; scale -= STEP) {
                    List<Line> lines = pack(used, keep, WIDTH / scale, width);
                    if (lines != null && (lines.size() * LINE_HEIGHT - LEADING) * scale <= HEIGHT + 1.0E-3F) {
                        return new Result(scale, lines, keep < most);
                    }
                }
            }
        }
        // Rien ne tient (ne devrait pas arriver avec les textes du jeu) : à l'échelle la plus petite, ce qui entre.
        List<Line> lines = new ArrayList<>();
        for (Section section : sections.stream().filter(s -> !s.optional()).toList()) {
            lines.add(new Line(section.head(), section.color()));
        }
        int fit = Math.max(1, (int) ((HEIGHT / MIN_SCALE + LEADING) / LINE_HEIGHT));
        return new Result(MIN_SCALE, lines.subList(0, Math.min(fit, lines.size())), true);
    }

    /**
     * Range les sections sur des lignes de {@code limit} pixels au plus ; nul si un morceau seul est plus large (la tête
     * se range alors mot à mot, mais un mot ou une coupure trop large ne tient pas à cette échelle).
     */
    @Nullable
    private static List<Line> pack(List<Section> sections, int keep, float limit, ToIntFunction<Component> width) {
        List<Line> lines = new ArrayList<>();
        for (Section section : sections) {
            List<Component> tokens = new ArrayList<>();
            if (width.applyAsInt(section.head()) <= limit) {
                tokens.add(section.head());
            } else if (section.optional()) {
                return null; // une section facultative trop large s'efface plutôt que de se couper
            } else {
                tokens.addAll(words(section.head()));
            }
            tokens.addAll(kept(section, keep));
            List<Component> packed = balance(tokens, limit, width);
            if (packed == null) {
                return null;
            }
            for (Component text : packed) {
                lines.add(new Line(text, section.color()));
            }
        }
        return lines;
    }

    /** Les coupures gardées : « … » et les parenthèses restent collés aux coupures, jamais seuls en bout de ligne. */
    private static List<Component> kept(Section section, int keep) {
        List<Component> amount = section.amount();
        List<Component> kept = new ArrayList<>(amount.size() > keep ? amount.subList(0, keep) : amount);
        if (!kept.isEmpty()) {
            if (amount.size() > keep) {
                kept.set(kept.size() - 1, Component.empty().append(kept.get(kept.size() - 1)).append(" …"));
            }
            if (section.parentheses()) {
                kept.set(0, Component.literal("(").append(kept.get(0)));
                kept.set(kept.size() - 1, Component.empty().append(kept.get(kept.size() - 1)).append(")"));
            }
        }
        return kept;
    }

    /**
     * Les morceaux sur le moins de lignes possible, coupés là où les lignes sont les plus égales (somme des carrés de la
     * place perdue, la dernière ligne comprise) ; nul si un morceau seul dépasse {@code limit}.
     */
    @Nullable
    static List<Component> balance(List<Component> tokens, float limit, ToIntFunction<Component> width) {
        if (tokens.isEmpty()) {
            return List.of();
        }
        for (Component token : tokens) {
            if (width.applyAsInt(token) > limit) {
                return null;
            }
        }
        int n = tokens.size();
        // Le moins de lignes possible : celui du rangement glouton.
        int needed = 1;
        Component current = tokens.get(0);
        for (int i = 1; i < n; i++) {
            Component joined = join(current, tokens.get(i));
            if (width.applyAsInt(joined) > limit) {
                needed++;
                current = tokens.get(i);
            } else {
                current = joined;
            }
        }
        // Parmi les coupures en {@code needed} lignes, la plus équilibrée : programmation dynamique sur (morceaux rangés,
        // lignes faites), en O(n² x lignes). cost[k][i] : le coût le plus petit des i premiers morceaux sur k lignes.
        double[][] cost = new double[needed + 1][n + 1];
        int[][] from = new int[needed + 1][n + 1];
        for (double[] row : cost) {
            java.util.Arrays.fill(row, Double.MAX_VALUE);
        }
        cost[0][0] = 0.0D;
        for (int k = 1; k <= needed; k++) {
            for (int i = k; i <= n; i++) {
                for (int j = i - 1; j >= k - 1; j--) {
                    if (cost[k - 1][j] == Double.MAX_VALUE) {
                        continue;
                    }
                    int w = width.applyAsInt(line(tokens, j, i));
                    if (w > limit) {
                        break; // plus de morceaux sur la ligne ne la rendent pas plus courte
                    }
                    double c = cost[k - 1][j] + (limit - w) * (double) (limit - w);
                    if (c < cost[k][i]) {
                        cost[k][i] = c;
                        from[k][i] = j;
                    }
                }
            }
        }
        List<Component> out = new ArrayList<>();
        int end = n;
        for (int k = needed; k >= 1; k--) {
            int start = from[k][end];
            out.add(0, line(tokens, start, end));
            end = start;
        }
        return out;
    }

    private static Component line(List<Component> tokens, int from, int to) {
        MutableComponent out = Component.empty().append(tokens.get(from));
        for (int i = from + 1; i < to; i++) {
            out = join(out, tokens.get(i));
        }
        return out;
    }

    private static MutableComponent join(Component a, Component b) {
        return Component.empty().append(a).append(" ").append(b);
    }

    /** Les mots d'une phrase ; un signe seul (« : ») reste collé au mot qui le précède. */
    static List<Component> words(Component head) {
        List<String> out = new ArrayList<>();
        for (String word : head.getString().split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty() && word.length() == 1 && !Character.isLetterOrDigit(word.charAt(0))) {
                out.set(out.size() - 1, out.get(out.size() - 1) + " " + word);
            } else {
                out.add(word);
            }
        }
        return out.stream().<Component>map(Component::literal).toList();
    }
}
