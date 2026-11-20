package dev.oneforallsurvival.phone.client;

import dev.oneforallsurvival.client.web.WebScreen.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La mise en page de l'écran de repli du téléphone, en coordonnées d'interface, sans Minecraft (testable) : la zone
 * utile dans le cadre, le clavier du code et les lignes des Réglages, avec un libellé au-dessus de chacun des trois
 * champs de code. Tout tient dans la zone utile de l'interface de 320 × 240 à la plus grande.
 */
public final class PhoneFallbackLayout {

    /** Les lignes des Réglages, de haut en bas. */
    public static final List<String> SETTINGS_ROWS = List.of("airplane", "wallpaper", "old", "new", "confirm", "change",
            "lock", "erase", "back");
    /** Les lignes qui portent un libellé au-dessus d'elles (les champs de code). */
    public static final List<String> LABELLED = List.of("old", "new", "confirm");
    static final int KEY_GAP = 3;
    static final int ROW_GAP = 2;
    static final int MIN_ROW = 10;
    static final int MAX_ROW = 20;
    /** En dessous de cette échelle, le texte d'Effacer cède la place à une flèche (le libellé reste lu et en bulle). */
    static final float SYMBOL_BELOW = 0.75f;

    private PhoneFallbackLayout() {}

    /** Les Réglages : le haut des deux lignes de texte (propriétaire, message), les lignes et le haut des libellés. */
    public record Settings(int textTop, Map<String, Rect> rows, Map<String, Integer> labelTops) {}

    /** La zone utile : le cadre moins une marge de 6 % (3 px au moins) et la barre d'état en haut. */
    public static Rect inner(Rect frame, int lineHeight) {
        int pad = Math.max(3, Math.round(frame.width() * 0.06f));
        int status = statusHeight(lineHeight);
        return new Rect(frame.x() + pad, frame.y() + pad + status, frame.width() - 2 * pad,
                frame.height() - 2 * pad - status);
    }

    public static int statusHeight(int lineHeight) {
        return lineHeight + 4;
    }

    /**
     * Les onze touches du clavier, dans le bas de la zone : indices 0 à 8 pour 1 à 9 (trois colonnes), 9 pour Effacer,
     * 10 pour 0.
     */
    public static List<Rect> keypad(Rect inner) {
        int bw = (inner.width() - 2 * KEY_GAP) / 3;
        int bh = clamp((inner.height() / 2 - 3 * KEY_GAP) / 4);
        int top = inner.y() + inner.height() - 4 * bh - 3 * KEY_GAP;
        List<Rect> keys = new ArrayList<>(11);
        for (int i = 0; i < 9; i++) {
            keys.add(new Rect(inner.x() + (i % 3) * (bw + KEY_GAP), top + (i / 3) * (bh + KEY_GAP), bw, bh));
        }
        int last = top + 3 * (bh + KEY_GAP);
        keys.add(new Rect(inner.x(), last, bw, bh));
        keys.add(new Rect(inner.x() + bw + KEY_GAP, last, bw, bh));
        return Collections.unmodifiableList(keys);
    }

    /** Les Réglages : deux lignes de texte, puis neuf lignes ; un libellé d'une ligne au-dessus de chaque champ de code. */
    public static Settings settings(Rect inner, int lineHeight) {
        int text = 2 * (lineHeight + 1);
        int label = lineHeight + 1;
        int n = SETTINGS_ROWS.size();
        int rh = clamp((inner.height() - text - LABELLED.size() * label - (n - 1) * ROW_GAP) / n);
        Map<String, Rect> rows = new LinkedHashMap<>();
        Map<String, Integer> labels = new LinkedHashMap<>();
        int y = inner.y() + text;
        for (String name : SETTINGS_ROWS) {
            if (LABELLED.contains(name)) {
                labels.put(name, y);
                y += label;
            }
            rows.put(name, new Rect(inner.x(), y, inner.width(), rh));
            y += rh + ROW_GAP;
        }
        return new Settings(inner.y(), Collections.unmodifiableMap(rows), Collections.unmodifiableMap(labels));
    }

    /** L'échelle (au plus {@code max}) qui fait tenir un texte de {@code textWidth} dans {@code available}. */
    public static float fit(int textWidth, int available, float max) {
        if (textWidth <= 0 || available <= 0) {
            return max;
        }
        return Math.min(max, available / (float) textWidth);
    }

    /** Le texte d'un bouton se lit sur sa largeur moins 2 px de chaque côté. */
    public static int textRoom(int buttonWidth) {
        return Math.max(1, buttonWidth - 4);
    }

    /** Vrai si le libellé d'Effacer, rétréci pour tenir dans sa touche, deviendrait trop petit pour être lu. */
    public static boolean clearAsSymbol(int textWidth, int buttonWidth) {
        return fit(textWidth, textRoom(buttonWidth), 1f) < SYMBOL_BELOW;
    }

    private static int clamp(int rowHeight) {
        return Math.max(MIN_ROW, Math.min(MAX_ROW, rowHeight));
    }
}
