package dev.oneforallsurvival.economy.quest.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

/**
 * La mise en page de l'écran des quêtes, sans Minecraft : un cadre de {@value #FRAME_W} × {@value #FRAME_H} pixels
 * d'interface, centré, qui tient dans la plus petite interface automatique (320 × 240). Tout texte dont la longueur
 * dépend du contenu est mesuré par {@code textWidth} et coupé par « … » s'il ne tient pas (le texte complet reste dans
 * {@link Text#full()}, pour la bulle) ; une récompense trop longue perd ses plus petites coupures, jamais une coupure
 * à moitié.
 *
 * <pre>
 *   [Quêtes][Événements]                 [Réclamer l'attente (8)]   rangée des onglets
 *   640 / 1 000 cuivres cette semaine                                plafond
 *   Aujourd'hui                              1 relance possible      section (10)
 *   [icône] titre de la quête                          [Réclamer]   ligne (20) : titre,
 *           ▬▬▬ 87 / 128               2 or 5 argent                barre, progression, récompense
 *   …
 *   Journalières dans 5 h 12 — hebdomadaires lundi                   bas
 * </pre>
 *
 * La liste (sections et lignes) a une hauteur fixe ; ce qui ne tient pas défile ({@link Layout#scrollMax()}), les
 * positions rendues sont celles d'un défilement nul.
 */
public final class QuestScreenLayout {

    public static final int FRAME_W = 300;
    public static final int FRAME_H = 226;
    public static final int PAD = 8;
    public static final int INNER = FRAME_W - 2 * PAD;
    public static final int TEXT_H = 9;
    public static final int TAB_Y = 5;
    public static final int TAB_H = 13;
    public static final int CAP_Y = 21;
    public static final int LIST_Y = 32;
    public static final int LIST_H = 180;
    public static final int FOOTER_Y = 215;
    public static final int HEADER_H = 10;
    public static final int ROW_H = 20;
    public static final int ICON = 16;
    public static final int BUTTON_W = 54;
    public static final int BUTTON_H = 14;
    public static final int BAR_W = 36;
    public static final int BAR_H = 5;
    /** Largeur que la récompense peut prendre au plus, pour laisser la progression lisible. */
    public static final int REWARD_MAX = 120;
    public static final String ELLIPSIS = "…";

    private QuestScreenLayout() {}

    // ---------------------------------------------------------------------------------------------
    // Ce que l'écran montre (textes déjà traduits)
    // ---------------------------------------------------------------------------------------------

    /**
     * Une ligne de quête. {@code rewardParts} : les coupures de la récompense, de la plus grande à la plus petite ;
     * {@code button} : le libellé du bouton (« Réclamer », « Relancer »), ou nul ; {@code status} : un texte à la place
     * du bouton (« Réclamée »), ou nul.
     */
    public record RowView(String title, List<String> rewardParts, String progress, @Nullable String button,
            @Nullable String status) {}

    public record SectionView(String header, String headerRight, List<RowView> rows, @Nullable String empty) {}

    /**
     * L'écran : onglets (le second, « Événements », nul s'il n'y en a pas), plafond, bouton de l'attente (nul sans
     * attente), sections de l'onglet montré, bas.
     */
    public record View(String tabQuests, @Nullable String tabEvents, String cap, @Nullable String pendingButton,
            List<SectionView> sections, String footer) {}

    // ---------------------------------------------------------------------------------------------
    // Ce que la mise en page rend (coordonnées de l'interface)
    // ---------------------------------------------------------------------------------------------

    public record Rect(int x, int y, int w, int h) {
        public boolean contains(Rect o) {
            return o.x >= x && o.y >= y && o.x + o.w <= x + w && o.y + o.h <= y + h;
        }

        public boolean intersects(Rect o) {
            return o.x < x + w && x < o.x + o.w && o.y < y + h && y < o.y + o.h;
        }

        public boolean inside(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** Un texte posé à ({@code x}, {@code y}) dans une place de {@code room} pixels ; {@code shown} y tient. */
    public record Text(int x, int y, int room, String shown, String full, int width) {
        public boolean cut() {
            return !shown.equals(full);
        }

        /** Le rectangle du texte montré. */
        public Rect box() {
            return new Rect(x, y, width, TEXT_H);
        }

        /** La place réservée au texte. */
        public Rect area() {
            return new Rect(x, y, room, TEXT_H);
        }
    }

    public record RowLayout(Rect box, Rect icon, Text title, Rect bar, Text progress, Text reward,
            @Nullable Rect button, @Nullable Text buttonLabel, @Nullable Text status) {}

    public record SectionLayout(Text header, Text headerRight, List<RowLayout> rows, @Nullable Text empty) {}

    public record Layout(Rect frame, List<Rect> tabs, List<Text> tabLabels, Text cap, @Nullable Rect pendingButton,
            @Nullable Text pendingLabel, Rect list, List<SectionLayout> sections, int contentHeight, int scrollMax,
            Text footer) {}

    // ---------------------------------------------------------------------------------------------

    public static Layout of(int guiWidth, int guiHeight, ToIntFunction<String> textWidth, View view) {
        int left = (guiWidth - FRAME_W) / 2;
        int top = Math.max(0, (guiHeight - FRAME_H) / 2);
        Rect frame = new Rect(left, top, FRAME_W, FRAME_H);
        int x0 = left + PAD;

        // Rangée des onglets : à gauche les onglets, à droite le bouton de l'attente ; l'attente cède la place aux onglets.
        List<Rect> tabs = new ArrayList<>();
        List<Text> tabLabels = new ArrayList<>();
        List<String> names = view.tabEvents() == null ? List.of(view.tabQuests())
                : List.of(view.tabQuests(), view.tabEvents());
        int tx = x0;
        int tabRoom = (view.pendingButton() == null ? INNER : INNER * 2 / 3) / names.size() - 3;
        for (String name : names) {
            String shown = cut(name, tabRoom - 10, textWidth);
            int w = textWidth.applyAsInt(shown) + 10;
            tabs.add(new Rect(tx, top + TAB_Y, w, TAB_H));
            tabLabels.add(text(tx + 5, top + TAB_Y + 3, w - 10, shown, name, textWidth));
            tx += w + 3;
        }
        Rect pendingButton = null;
        Text pendingLabel = null;
        if (view.pendingButton() != null) {
            int room = x0 + INNER - tx - 4;
            String shown = cut(view.pendingButton(), room - 10, textWidth);
            int w = Math.min(room, textWidth.applyAsInt(shown) + 10);
            pendingButton = new Rect(x0 + INNER - w, top + TAB_Y, w, TAB_H);
            pendingLabel = text(pendingButton.x() + 5, top + TAB_Y + 3, w - 10, shown, view.pendingButton(), textWidth);
        }

        Text cap = text(x0, top + CAP_Y, INNER, cut(view.cap(), INNER, textWidth), view.cap(), textWidth);
        Rect list = new Rect(x0, top + LIST_Y, INNER, LIST_H);

        List<SectionLayout> sections = new ArrayList<>();
        int y = list.y();
        for (SectionView section : view.sections()) {
            String right = section.headerRight();
            int rightW = Math.min(textWidth.applyAsInt(right), INNER / 2);
            String rightShown = cut(right, rightW, textWidth);
            int rightWidth = textWidth.applyAsInt(rightShown);
            Text headerRight = text(x0 + INNER - rightWidth, y + 2, rightWidth, rightShown, right, textWidth);
            int headerRoom = INNER - rightWidth - (rightWidth > 0 ? 6 : 0);
            Text header = text(x0, y + 2, headerRoom, cut(section.header(), headerRoom, textWidth), section.header(),
                    textWidth);
            y += HEADER_H;
            List<RowLayout> rows = new ArrayList<>();
            for (RowView row : section.rows()) {
                rows.add(row(x0, y, row, textWidth));
                y += ROW_H;
            }
            Text empty = null;
            if (section.rows().isEmpty() && section.empty() != null) {
                empty = text(x0 + 4, y + 1, INNER - 4, cut(section.empty(), INNER - 4, textWidth), section.empty(),
                        textWidth);
                y += HEADER_H;
            }
            sections.add(new SectionLayout(header, headerRight, rows, empty));
        }
        int contentHeight = y - list.y();
        Text footer = text(x0, top + FOOTER_Y, INNER, cut(view.footer(), INNER, textWidth), view.footer(), textWidth);
        return new Layout(frame, List.copyOf(tabs), List.copyOf(tabLabels), cap, pendingButton, pendingLabel, list,
                List.copyOf(sections), contentHeight, Math.max(0, contentHeight - LIST_H), footer);
    }

    /** Une ligne de 20 pixels : icône, titre (toute la largeur), barre, progression et récompense, bouton à droite. */
    private static RowLayout row(int x0, int y, RowView row, ToIntFunction<String> textWidth) {
        Rect box = new Rect(x0, y, INNER, ROW_H);
        Rect icon = new Rect(x0 + 2, y + 2, ICON, ICON);
        int textX = x0 + 4 + ICON + 2;
        int buttonX = x0 + INNER - BUTTON_W;
        int middle = buttonX - 4 - textX;
        Text title = text(textX, y + 2, middle, cut(row.title(), middle, textWidth), row.title(), textWidth);

        Rect bar = new Rect(textX, y + 13, BAR_W, BAR_H);
        int progressX = textX + BAR_W + 4;
        int progressRoom = Math.max(0, middle - BAR_W - 4) / 2;
        String progressShown = cut(row.progress(), progressRoom, textWidth);
        int progressWidth = textWidth.applyAsInt(progressShown);
        Text progress = text(progressX, y + 11, progressWidth, progressShown, row.progress(), textWidth);

        int rewardRoom = Math.min(REWARD_MAX, textX + middle - (progressX + progressWidth + 6));
        String rewardFull = String.join(" ", row.rewardParts());
        String rewardShown = reward(row.rewardParts(), rewardRoom, textWidth);
        int rewardWidth = textWidth.applyAsInt(rewardShown);
        Text reward = text(textX + middle - rewardWidth, y + 11, rewardWidth, rewardShown, rewardFull, textWidth);

        Rect button = null;
        Text buttonLabel = null;
        Text status = null;
        if (row.button() != null) {
            button = new Rect(buttonX, y + 3, BUTTON_W, BUTTON_H);
            String shown = cut(row.button(), BUTTON_W - 6, textWidth);
            int w = textWidth.applyAsInt(shown);
            buttonLabel = text(buttonX + (BUTTON_W - w) / 2, y + 6, w, shown, row.button(), textWidth);
        } else if (row.status() != null) {
            String shown = cut(row.status(), BUTTON_W, textWidth);
            int w = textWidth.applyAsInt(shown);
            status = text(buttonX + (BUTTON_W - w) / 2, y + 6, w, shown, row.status(), textWidth);
        }
        return new RowLayout(box, icon, title, bar, progress, reward, button, buttonLabel, status);
    }

    private static Text text(int x, int y, int room, String shown, String full, ToIntFunction<String> textWidth) {
        return new Text(x, y, room, shown, full, textWidth.applyAsInt(shown));
    }

    /** Le texte coupé par « … » s'il dépasse {@code room} pixels (vide si même « … » ne tient pas). */
    public static String cut(String text, int room, ToIntFunction<String> textWidth) {
        if (textWidth.applyAsInt(text) <= room) {
            return text;
        }
        for (int n = text.length() - 1; n > 0; n--) {
            String shown = text.substring(0, n).stripTrailing() + ELLIPSIS;
            if (textWidth.applyAsInt(shown) <= room) {
                return shown;
            }
        }
        return textWidth.applyAsInt(ELLIPSIS) <= room ? ELLIPSIS : "";
    }

    /** La récompense entière si elle tient, sinon ses plus grandes coupures suivies de « … », jamais une coupure coupée. */
    static String reward(List<String> parts, int room, ToIntFunction<String> textWidth) {
        String full = String.join(" ", parts);
        if (textWidth.applyAsInt(full) <= room) {
            return full;
        }
        for (int k = parts.size() - 1; k >= 1; k--) {
            String shown = String.join(" ", parts.subList(0, k)) + " " + ELLIPSIS;
            if (textWidth.applyAsInt(shown) <= room) {
                return shown;
            }
        }
        return parts.isEmpty() ? "" : cut(parts.get(0), room, textWidth);
    }
}
