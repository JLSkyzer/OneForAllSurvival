package dev.oneforallsurvival.grid.supplier.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

/**
 * La mise en page du panneau du poste de fournisseur, sans Minecraft : un cadre de {@value #FRAME_W} × {@value #FRAME_H}
 * pixels d'interface, centré, qui tient dans la plus petite interface automatique (320 × 240). Tout texte dont la
 * longueur dépend du contenu est mesuré par {@code textWidth} et coupé par « … » s'il ne tient pas (le texte complet
 * reste dans {@link Text#full()}, pour la bulle) ; un montant trop long perd ses plus petites coupures, jamais une
 * coupure à moitié.
 *
 * <pre>
 *   Nom du fournisseur                                   [Caisse]     titre (un poste neuf : « Nouveau poste »)
 *   ───────────────────────────────────────────────────────────────
 *   Prix : 5 cuivres → demain 8 cuivres             [____] [OK]       champs du tarif (du lendemain)
 *   Abonnement : 2 cuivres par jour                 [____] [OK]
 *   Plafond : 20 cuivres les 100 000 FE · taxe 0 %
 *                  Sortie        Compteurs     Écart                  écart du poste, en rouge
 *   Aujourd'hui    1 234 kFE     1 200 kFE     34 kFE (2 %)           au-delà du seuil ({@link #gapAlert})
 *   Hier           …
 *   Caisse : 1 rubis 3 émeraudes …
 *   ───────────────────────────────────────────────────────────────
 *   Clients (12)
 *   abonné (joueur)                 Actif      [Couper] [Détacher]    une ligne (20) par client
 *   Aujourd'hui 12 kFE · hier 30 kFE   2 cuivres                      (impayés à droite)
 * </pre>
 *
 * Un poste neuf ne montre que le libellé, le champ du nom et le bouton « Fonder ». La liste a une hauteur fixe ; ce qui ne
 * tient pas défile ({@link Layout#scrollMax()}), les positions rendues sont celles d'un défilement nul.
 */
public final class PowerPanelLayout {

    public static final int FRAME_W = 312;
    public static final int FRAME_H = 236;
    public static final int PAD = 6;
    public static final int INNER = FRAME_W - 2 * PAD;
    public static final int TEXT_H = 9;

    public static final int TITLE_Y = 8;
    public static final int RULE_Y = 22;
    public static final int TILL_W = 64;
    public static final int BUTTON_H = 16;

    public static final int NAME_LABEL_Y = 30;
    public static final int NAME_FIELD_Y = 42;
    public static final int FOUND_W = 80;

    public static final int PRICE_Y = 26;
    public static final int SUBSCRIPTION_Y = 42;
    public static final int FIELD_W = 60;
    public static final int FIELD_H = 14;
    public static final int OK_W = 30;
    public static final int CAP_Y = 60;
    public static final int GAP_HEADER_Y = 72;
    public static final int GAP_TODAY_Y = 82;
    public static final int GAP_YESTERDAY_Y = 92;
    public static final int GAP_LABEL_W = 72;
    public static final int GAP_CELL_W = (INNER - GAP_LABEL_W) / 3;
    public static final int TILL_Y = 104;
    public static final int RULE2_Y = 114;
    public static final int LIST_HEADER_Y = 118;
    public static final int LIST_Y = 129;
    public static final int ROW_H = 20;
    /** Cinq lignes entières : aucune ligne à moitié visible en bas de la liste. */
    public static final int LIST_H = 5 * ROW_H;
    /** La place de la barre de défilement, à droite de la liste. */
    public static final int SCROLL_W = 6;

    public static final int ROW_BUTTON_W = 52;
    public static final int ROW_BUTTON_H = 16;
    public static final int STATE_MAX = 80;
    public static final int UNPAID_MAX = 96;

    /** L'écart ne passe en alerte qu'au-delà de ce nombre de FE (et du pourcentage réglé). */
    public static final long GAP_WARN_MIN_FE = 100_000L;
    public static final String ELLIPSIS = "…";

    private PowerPanelLayout() {}

    // ---------------------------------------------------------------------------------------------
    // Ce que l'écran montre (textes déjà traduits)
    // ---------------------------------------------------------------------------------------------

    /**
     * Une ligne de client : nom (« abonné (joueur) »), état, consommation, impayés (coupures de la plus grande à la plus
     * petite ; vide s'il n'y en a pas) et un ou deux libellés de boutons.
     */
    public record RowView(String name, String state, String usage, List<String> unpaidParts, List<String> buttons) {}

    /** Une ligne de l'écart : son libellé, les trois cellules (textes), et les FE pour le seuil. */
    public record GapView(String label, String out, String meters, String gap, long outFe, long metersFe) {}

    /**
     * Le panneau. {@code supplier} nul : poste neuf (seuls {@code title}, {@code nameLabel} et {@code foundButton}
     * comptent).
     */
    public record View(@Nullable String supplier, String title, String tillButton, String nameLabel, String foundButton,
            String priceLine, String subscriptionLine, String okButton, String capLine, List<String> gapHeaders,
            GapView today, GapView yesterday, int warnPercent, String tillLabel, List<String> tillParts,
            String listHeader, List<RowView> rows, String empty) {}

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

        public Rect shifted(int dy) {
            return new Rect(x, y + dy, w, h);
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

    /** Un champ de saisie avec son libellé à gauche et son bouton à droite. */
    public record Field(Text label, Rect box, Rect button, Text buttonLabel) {}

    public record GapLine(Text label, Text out, Text meters, Text gap, boolean alert) {}

    public record RowLayout(Rect box, Text name, Text state, Text usage, Text unpaid, List<Rect> buttons,
            List<Text> buttonLabels) {}

    public record Layout(Rect frame, Text title, @Nullable Rect tillButton, @Nullable Text tillLabel,
            @Nullable Text nameLabel, @Nullable Rect nameField, @Nullable Rect foundButton, @Nullable Text foundLabel,
            @Nullable Field price, @Nullable Field subscription, @Nullable Text cap, List<Text> gapHeaders,
            @Nullable GapLine today, @Nullable GapLine yesterday, @Nullable Text till, @Nullable Text listHeader,
            @Nullable Rect list, List<RowLayout> rows, @Nullable Text empty, int contentHeight, int scrollMax) {}

    // ---------------------------------------------------------------------------------------------

    public static Layout of(int guiWidth, int guiHeight, ToIntFunction<String> textWidth, View view) {
        int left = Math.max(0, (guiWidth - FRAME_W) / 2);
        int top = Math.max(0, (guiHeight - FRAME_H) / 2);
        Rect frame = new Rect(left, top, FRAME_W, FRAME_H);
        int x0 = left + PAD;
        boolean founded = view.supplier() != null;

        Rect tillButton = null;
        Text tillLabel = null;
        int titleRoom = INNER;
        if (founded) {
            tillButton = new Rect(x0 + INNER - TILL_W, top + TITLE_Y - 4, TILL_W, BUTTON_H);
            tillLabel = centered(tillButton, view.tillButton(), textWidth);
            titleRoom = INNER - TILL_W - 6;
        }
        Text title = fit(x0, top + TITLE_Y, titleRoom, view.title(), textWidth);

        if (!founded) {
            Text nameLabel = fit(x0, top + NAME_LABEL_Y, INNER, view.nameLabel(), textWidth);
            Rect nameField = new Rect(x0, top + NAME_FIELD_Y, INNER - FOUND_W - 4, BUTTON_H);
            Rect foundButton = new Rect(x0 + INNER - FOUND_W, top + NAME_FIELD_Y, FOUND_W, BUTTON_H);
            return new Layout(frame, title, null, null, nameLabel, nameField, foundButton,
                    centered(foundButton, view.foundButton(), textWidth), null, null, null, List.of(), null, null, null,
                    null, null, List.of(), null, 0, 0);
        }

        Field price = field(x0, top + PRICE_Y, view.priceLine(), view.okButton(), textWidth);
        Field subscription = field(x0, top + SUBSCRIPTION_Y, view.subscriptionLine(), view.okButton(), textWidth);
        Text cap = fit(x0, top + CAP_Y, INNER, view.capLine(), textWidth);

        List<Text> gapHeaders = new ArrayList<>();
        for (int i = 0; i < 3 && i < view.gapHeaders().size(); i++) {
            gapHeaders.add(fit(cellX(x0, i), top + GAP_HEADER_Y, GAP_CELL_W - 4, view.gapHeaders().get(i), textWidth));
        }
        GapLine today = gap(x0, top + GAP_TODAY_Y, view.today(), view.warnPercent(), textWidth);
        GapLine yesterday = gap(x0, top + GAP_YESTERDAY_Y, view.yesterday(), view.warnPercent(), textWidth);

        String tillFull = view.tillParts().isEmpty() ? view.tillLabel()
                : view.tillLabel() + " " + String.join(" ", view.tillParts());
        int tillPrefix = textWidth.applyAsInt(view.tillLabel() + " ");
        String tillShown = view.tillParts().isEmpty() ? cut(view.tillLabel(), INNER, textWidth)
                : view.tillLabel() + " " + parts(view.tillParts(), INNER - tillPrefix, textWidth);
        Text till = text(x0, top + TILL_Y, INNER, tillShown, tillFull, textWidth);

        Text listHeader = fit(x0, top + LIST_HEADER_Y, INNER, view.listHeader(), textWidth);
        Rect list = new Rect(x0, top + LIST_Y, INNER, LIST_H);
        List<RowLayout> rows = new ArrayList<>();
        int y = list.y();
        for (RowView row : view.rows()) {
            rows.add(row(x0, y, row, textWidth));
            y += ROW_H;
        }
        Text empty = null;
        if (view.rows().isEmpty()) {
            empty = fit(x0 + 2, list.y() + 4, INNER - 4, view.empty(), textWidth);
            y += ROW_H;
        }
        int contentHeight = y - list.y();
        return new Layout(frame, title, tillButton, tillLabel, null, null, null, null, price, subscription, cap,
                List.copyOf(gapHeaders), today, yesterday, till, listHeader, list, List.copyOf(rows), empty,
                contentHeight, Math.max(0, contentHeight - LIST_H));
    }

    private static int cellX(int x0, int column) {
        return x0 + GAP_LABEL_W + column * GAP_CELL_W;
    }

    /** Libellé à gauche, champ puis bouton à droite. */
    private static Field field(int x0, int y, String label, String ok, ToIntFunction<String> textWidth) {
        Rect button = new Rect(x0 + INNER - OK_W, y, OK_W, FIELD_H);
        Rect box = new Rect(button.x() - 4 - FIELD_W, y, FIELD_W, FIELD_H);
        Text text = fit(x0, y + 3, box.x() - 4 - x0, label, textWidth);
        return new Field(text, box, button, centered(button, ok, textWidth));
    }

    private static GapLine gap(int x0, int y, GapView view, int warnPercent, ToIntFunction<String> textWidth) {
        return new GapLine(fit(x0, y, GAP_LABEL_W - 4, view.label(), textWidth),
                fit(cellX(x0, 0), y, GAP_CELL_W - 4, view.out(), textWidth),
                fit(cellX(x0, 1), y, GAP_CELL_W - 4, view.meters(), textWidth),
                fit(cellX(x0, 2), y, GAP_CELL_W - 4, view.gap(), textWidth),
                gapAlert(view.outFe(), view.metersFe(), warnPercent));
    }

    /**
     * Une ligne de 20 pixels : en haut le nom (à gauche) et l'état (à droite) ; en bas la consommation (à gauche) et les
     * impayés (à droite) ; les boutons à droite de la ligne, le dernier collé au bord de la liste (avant la barre de
     * défilement).
     */
    private static RowLayout row(int x0, int y, RowView row, ToIntFunction<String> textWidth) {
        Rect box = new Rect(x0, y, INNER - SCROLL_W, ROW_H);
        List<Rect> buttons = new ArrayList<>();
        List<Text> labels = new ArrayList<>();
        int right = box.x() + box.w();
        int n = Math.min(2, row.buttons().size());
        for (int i = 0; i < n; i++) {
            // Le dernier bouton à droite ; avec un seul, il prend la place de droite.
            int slot = n - 1 - i;
            Rect b = new Rect(right - (slot + 1) * ROW_BUTTON_W - slot * 2, y + 2, ROW_BUTTON_W, ROW_BUTTON_H);
            buttons.add(b);
            labels.add(centered(b, row.buttons().get(i), textWidth));
        }
        int textX = x0 + 2;
        int textRight = right - 2 * ROW_BUTTON_W - 2 - 4;
        int textRoom = textRight - textX;

        String stateShown = cut(row.state(), Math.min(STATE_MAX, textRoom / 2), textWidth);
        int stateW = textWidth.applyAsInt(stateShown);
        Text state = text(textRight - stateW, y + 2, stateW, stateShown, row.state(), textWidth);
        int nameRoom = textRoom - stateW - (stateW > 0 ? 6 : 0);
        Text name = fit(textX, y + 2, nameRoom, row.name(), textWidth);

        String unpaidFull = String.join(" ", row.unpaidParts());
        String unpaidShown = parts(row.unpaidParts(), Math.min(UNPAID_MAX, textRoom / 2), textWidth);
        int unpaidW = textWidth.applyAsInt(unpaidShown);
        Text unpaid = text(textRight - unpaidW, y + 11, unpaidW, unpaidShown, unpaidFull, textWidth);
        int usageRoom = textRoom - unpaidW - (unpaidW > 0 ? 6 : 0);
        Text usage = fit(textX, y + 11, usageRoom, row.usage(), textWidth);
        return new RowLayout(box, name, state, usage, unpaid, List.copyOf(buttons), List.copyOf(labels));
    }

    /**
     * L'écart entre la sortie du poste et la somme des compteurs est-il en alerte ? Seulement au-delà des deux seuils :
     * plus de {@code percent} % de la sortie et plus de {@value #GAP_WARN_MIN_FE} FE (dans un sens ou dans l'autre :
     * des compteurs au-dessus de la sortie sont aussi anormaux). Sans débordement.
     */
    public static boolean gapAlert(long out, long meters, int percent) {
        long a = Math.max(0L, out);
        long b = Math.max(0L, meters);
        long gap = a >= b ? a - b : b - a;
        return gap > GAP_WARN_MIN_FE && (double) gap * 100.0D > (double) Math.max(0, percent) * (double) a;
    }

    private static Text centered(Rect button, String label, ToIntFunction<String> textWidth) {
        String shown = cut(label, button.w() - 6, textWidth);
        int w = textWidth.applyAsInt(shown);
        return text(button.x() + (button.w() - w) / 2, button.y() + (button.h() - 8) / 2 + 1, w, shown, label, textWidth);
    }

    private static Text fit(int x, int y, int room, String full, ToIntFunction<String> textWidth) {
        return text(x, y, Math.max(0, room), cut(full, Math.max(0, room), textWidth), full, textWidth);
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

    /** Le montant entier s'il tient, sinon ses plus grandes coupures suivies de « … », jamais une coupure coupée. */
    static String parts(List<String> parts, int room, ToIntFunction<String> textWidth) {
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
