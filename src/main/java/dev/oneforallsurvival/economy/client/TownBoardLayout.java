package dev.oneforallsurvival.economy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * La mise en page du texte du tableau de la mairie, sans rien de graphique (testable). Le panneau n'offre que
 * {@link #WIDTH} x {@link #HEIGHT} pixels de texte (un bloc en compte 64) : un texte plus petit que
 * {@link #MIN_SCALE} ne se lit plus à quatre blocs. La mise en page cherche donc, dans l'ordre :
 * <ol>
 * <li>tout le texte, avec son titre, à la plus grande échelle (de 1 à {@link #MIN_SCALE}) qui le fait tenir ;</li>
 * <li>sans les sections facultatives (le titre) ;</li>
 * <li>puis en gardant de moins en moins de coupures de chaque montant (les plus grandes d'abord, suivies de « … »), le
 * chiffre exact se lisant au chat (clic droit).</li>
 * </ol>
 * Les mots de la phrase qui précède un montant et les coupures (« 9 rubis ») sont des morceaux que la mise en page range
 * sur les lignes sans jamais en couper un ; une phrase sans montant (le titre, « Aucun salaire dû ») reste d'un bloc tant
 * qu'elle tient à l'échelle minimale (le titre s'efface plutôt que de se couper), et une ligne plus large que le panneau
 * n'est jamais acceptée.
 */
final class TownBoardLayout {

    /** Largeur utile du panneau en pixels de texte (14/16 de bloc : le cadre de bois en prend 4 de chaque côté). */
    static final float WIDTH = 56.0F;
    /** Hauteur utile du panneau, en pixels de texte (le panneau en compte 40, le cadre 4 en haut et en bas). */
    static final float HEIGHT = 32.0F;
    /** Hauteur d'une ligne de texte, en pixels de texte non rétrécis, et part de cette hauteur sous la dernière ligne qui ne se voit pas. */
    static final float LINE_HEIGHT = 9.0F;
    private static final float LEADING = 2.0F;
    /** L'échelle la plus petite qui reste lisible à quatre blocs. */
    static final float MIN_SCALE = 0.7F;
    private static final float STEP = 0.05F;

    /**
     * Une section : {@code head} (une phrase, rangée mot à mot), puis {@code amount} (des coupures, jamais coupées, entre
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
        // D'abord les phrases d'un bloc (« Aucun salaire dû ») ; si l'une d'elles ne tient pas à l'échelle minimale, ses mots.
        for (boolean splitPhrases : new boolean[] {false, true}) {
            for (int keep = most; keep >= 1; keep--) {
                for (boolean withOptional : new boolean[] {true, false}) {
                    List<Section> used = withOptional ? sections : sections.stream().filter(s -> !s.optional()).toList();
                    if (used.isEmpty()) {
                        continue;
                    }
                    for (float scale = 1.0F; scale >= MIN_SCALE - 1.0E-4F; scale -= STEP) {
                        List<Line> lines = pack(used, keep, splitPhrases, WIDTH / scale, width);
                        if ((lines.size() * LINE_HEIGHT - LEADING) * scale <= HEIGHT + 1.0E-3F
                                && fits(lines, scale, width)) {
                            return new Result(scale, lines, keep < most);
                        }
                    }
                }
            }
        }
        // Rien ne tient (ne devrait pas arriver) : à l'échelle la plus petite, ce qui entre dans le panneau.
        List<Line> lines = pack(sections.stream().filter(s -> !s.optional()).toList(), 1, true, WIDTH / MIN_SCALE, width);
        int fit = Math.max(1, (int) ((HEIGHT / MIN_SCALE + LEADING) / LINE_HEIGHT));
        return new Result(MIN_SCALE, lines.subList(0, Math.min(fit, lines.size())), true);
    }

    /** Aucune ligne ne dépasse le panneau à cette échelle. */
    private static boolean fits(List<Line> lines, float scale, ToIntFunction<Component> width) {
        for (Line line : lines) {
            if (width.applyAsInt(line.text()) * scale > WIDTH + 1.0E-3F) {
                return false;
            }
        }
        return true;
    }

    /** Range les sections sur des lignes de {@code limit} pixels au plus (un morceau seul plus large garde sa ligne). */
    private static List<Line> pack(List<Section> sections, int keep, boolean splitPhrases, float limit,
            ToIntFunction<Component> width) {
        List<Line> lines = new ArrayList<>();
        for (Section section : sections) {
            // Une phrase sans montant (le titre, « Aucun salaire dû ») reste d'un bloc tant que {@code splitPhrases} est faux
            // (et le titre toujours : il disparaît plutôt que de se couper) ; celle qui précède un montant se range mot à mot.
            boolean block = section.amount().isEmpty() && (!splitPhrases || section.optional());
            List<Component> tokens = new ArrayList<>(block ? List.of(section.head()) : words(section.head()));
            List<Component> amount = section.amount();
            List<Component> kept = new ArrayList<>(amount.size() > keep ? amount.subList(0, keep) : amount);
            if (!kept.isEmpty()) {
                // Le signe « … » et les parenthèses restent collés aux coupures : jamais seuls au bout d'une ligne.
                if (amount.size() > keep) {
                    kept.set(kept.size() - 1, Component.empty().append(kept.get(kept.size() - 1)).append(" …"));
                }
                if (section.parentheses()) {
                    kept.set(0, Component.literal("(").append(kept.get(0)));
                    kept.set(kept.size() - 1, Component.empty().append(kept.get(kept.size() - 1)).append(")"));
                }
            }
            tokens.addAll(kept);
            MutableComponent current = null;
            for (Component token : tokens) {
                if (current == null) {
                    current = Component.empty().append(token);
                    continue;
                }
                MutableComponent joined = Component.empty().append(current).append(" ").append(token);
                if (width.applyAsInt(joined) > limit) {
                    lines.add(new Line(current, section.color()));
                    current = Component.empty().append(token);
                } else {
                    current = joined;
                }
            }
            if (current != null) {
                lines.add(new Line(current, section.color()));
            }
        }
        return lines;
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
