package dev.oneforallsurvival.phone.net.client;

import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.phone.net.OperatorActionPacket.Action;
import dev.oneforallsurvival.phone.net.OperatorView;
import dev.oneforallsurvival.phone.net.PhoneNetwork;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimNumbers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

/**
 * La mise en page du panneau de l'opérateur (lot T2, tâche 9), sans Minecraft (testable) : un cadre centré, de
 * {@value #FRAME_MAX_W} × {@value #FRAME_MAX_H} au plus, qui tient dans la plus petite interface (320 × 240) ; le titre,
 * trois onglets et, dessous, une liste qui défile.
 *
 * <ul>
 *   <li><b>Réseau</b> : une ligne par antenne (où, état et consommation par tic, FE d'aujourd'hui et d'hier, en rouge
 *       au-delà de ce qu'elle peut recevoir ; « Principale » ou le bouton qui la rend principale), puis le relais.
 *   <li><b>Cartes</b> : les demandes (« Émettre », « Refuser »), « SIM sans nom », les cartes de la page (« Suspendre »
 *       ou « Réactiver », « Remplacer ») et les pages.
 *   <li><b>Tarif et caisse</b> : tarif du jour et de demain, plafond, champ du tarif de demain, caisse.
 * </ul>
 *
 * Une antenne à fonder ne montre que le champ du nom (si le regardeur a le droit de nommer). Un bouton n'existe que si
 * {@link OperatorView#rights()} le permet : la mise en page ne décide rien d'autre. Chaque texte est mesuré par
 * {@code textWidth} : entier s'il tient, sinon rétréci jusqu'à {@value #MIN_SCALE}, sinon coupé par « … » (le texte
 * complet reste dans {@link Text#full()} pour la bulle). Les positions sont celles d'un défilement nul.
 */
public final class OperatorPanelLayout {

    public static final int FRAME_MAX_W = 360;
    public static final int FRAME_MAX_H = 300;
    public static final int PAD = 6;
    public static final int TEXT_H = 9;
    public static final int LINE = 10;
    public static final int TITLE_Y = 6;
    public static final int TABS_Y = 19;
    public static final int TAB_H = 16;
    public static final int LIST_Y = 40;
    public static final int BOTTOM = 6;
    public static final int SCROLL_W = 4;
    public static final int BUTTON_W = 62;
    public static final int BUTTON_H = 14;
    public static final int GAP = 2;
    public static final int FIELD_W = 80;
    /** Plancher de l'échelle d'un texte, comme au repli du téléphone (tâche 8) : en dessous, il est coupé par « … ». */
    public static final float MIN_SCALE = 0.75f;
    public static final String ELLIPSIS = "…";

    static final String KEY = "screen.oneforallsurvival.operator_panel.";

    public enum Tab { NETWORK, CARDS, TARIFF }

    /** Les textes traduits (le jeu : {@code I18n} et {@code CoinMath} ; les essais : {@code fr_fr.json}). */
    public interface Texts {
        /** Le texte de cette clé, avec ses arguments. */
        String t(String key, Object... args);

        /** Un montant en cuivres, en coupures. */
        String coins(long amount);

        /** Un nombre, groupé par milliers. */
        String number(long value);
    }

    // ---------------------------------------------------------------------------------------------
    // Ce que la mise en page rend
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

    /**
     * Un texte posé à ({@code x}, {@code y}) dans une place de {@code room} pixels : {@code shown} y tient à l'échelle
     * {@code scale} ({@code width} est sa largeur à l'échelle 1).
     */
    public record Text(int x, int y, int room, String shown, String full, int width, float scale) {
        public boolean cut() {
            return !shown.equals(full);
        }

        /** La place réservée au texte. */
        public Rect area() {
            return new Rect(x, y, room, TEXT_H);
        }

        /** Le rectangle du texte dessiné. */
        public Rect drawn() {
            return new Rect(x, y, (int) Math.ceil(width * scale), TEXT_H);
        }
    }

    /** Un bouton : le geste qu'il envoie ({@code value} : la page pour {@link Action#PAGE}), sa place, son libellé. */
    public record Button(Action action, String arg, long value, Rect box, Text label) {}

    /**
     * Une ligne de la liste : ses textes, ses boutons, un champ de saisie (le tarif de demain) et {@code alert} (en
     * rouge).
     */
    public record Row(String id, Rect box, List<Text> texts, List<Button> buttons, @Nullable Rect field,
            boolean alert) {}

    /** Un onglet : la place de sa liste, ses lignes, et jusqu'où elle défile. */
    public record Pane(Tab tab, Rect list, List<Row> rows, int scrollMax) {}

    /** Le nom d'une antenne à fonder : libellé, champ, bouton « Fonder ». */
    public record NameForm(Text label, Rect field, Rect button, Text buttonLabel) {}

    /**
     * Le panneau : cadre, titre, onglets (vides pour une antenne à fonder), champ du nom, les trois volets et la ligne
     * d'état (le dernier retour du serveur, deux lignes au plus, vide sans message).
     */
    public record Layout(Rect frame, Text title, List<Rect> tabs, List<Text> tabLabels, @Nullable NameForm name,
            List<Pane> panes, List<Text> status) {}

    /** La place de la ligne d'état, en bas du cadre (réservée même vide : la liste ne saute pas). */
    public static final int STATUS_H = 2 * LINE;

    private OperatorPanelLayout() {}

    // ---------------------------------------------------------------------------------------------

    /**
     * La mise en page de {@code view} dans une interface de {@code guiWidth} × {@code guiHeight}, textes mesurés par
     * {@code textWidth} et traduits par {@code texts}.
     */
    public static Layout of(int guiWidth, int guiHeight, ToIntFunction<String> textWidth, OperatorView view,
            Texts texts) {
        return of(guiWidth, guiHeight, textWidth, view, texts, "");
    }

    /**
     * Le panneau ferme-t-il à cette distance (au carré) de l'antenne ? Seulement pour la source {@code BLOCK} : celui du
     * téléphone ({@code pos} = (0, 0, 0)) ne se ferme que par le serveur ou Échap (revue de la tâche 9).
     */
    public static boolean closesAtDistance(OperatorView view, double distanceSqr) {
        return view.source() == OperatorView.Source.BLOCK && distanceSqr > MAX_DISTANCE_SQR;
    }

    static final double MAX_DISTANCE_SQR = 64.0;

    /** Ce que le panneau dit au serveur quand son écran est retiré. */
    public enum Removal {
        /** Rien : sorti par Échap (déjà dit) ou fermé par le serveur. */
        NONE,
        /** Le {@code CLOSE} du panneau (un autre écran l'a remplacé). */
        PANEL,
        /** Le {@code CLOSE} du panneau et celui de la session du téléphone (panneau ouvert depuis le repli). */
        PANEL_AND_PHONE
    }

    /**
     * Revue de la tâche 10 (mineur 1) : un panneau retiré sans passer par sa sortie ({@code leaving} : Échap, qui a déjà
     * prévenu) ni par une fermeture du serveur prévient le serveur ; celui ouvert depuis le repli ({@code fromPhone})
     * ferme aussi la session du téléphone, que le repli caché n'a pas fermée. Jamais deux fois.
     */
    public static Removal onRemoval(boolean fromPhone, boolean leaving, boolean closedByServer) {
        if (leaving || closedByServer) {
            return Removal.NONE;
        }
        return fromPhone ? Removal.PANEL_AND_PHONE : Removal.PANEL;
    }

    /** La même mise en page, avec {@code status}, le dernier retour du serveur, sur la ligne d'état. */
    public static Layout of(int guiWidth, int guiHeight, ToIntFunction<String> textWidth, OperatorView view,
            Texts texts, String status) {
        int w = Math.max(0, Math.min(FRAME_MAX_W, guiWidth - 8));
        int h = Math.max(0, Math.min(FRAME_MAX_H, guiHeight - 8));
        Rect frame = new Rect(Math.max(0, (guiWidth - w) / 2), Math.max(0, (guiHeight - h) / 2), w, h);
        int x0 = frame.x() + PAD;
        int inner = w - 2 * PAD;
        boolean founded = !view.operatorId().isEmpty();
        String titleText = founded ? view.name()
                : texts.t(KEY + (view.rights().name() ? "unfounded" : "no_operator"));
        Text title = line(x0, frame.y() + TITLE_Y, inner, titleText, textWidth);
        Rect statusArea = new Rect(x0, frame.y() + h - BOTTOM - STATUS_H, inner, STATUS_H);
        List<Text> statusLines = wrap(statusArea, status, textWidth);
        if (!founded) {
            NameForm form = null;
            if (view.rights().name()) {
                Text label = line(x0, frame.y() + LIST_Y, inner, texts.t(KEY + "name_label", 24), textWidth);
                int y = frame.y() + LIST_Y + LINE + 2;
                Rect button = new Rect(x0 + inner - 80, y, 80, TAB_H);
                Rect field = new Rect(x0, y, inner - 80 - 4, TAB_H);
                form = new NameForm(label, field, button, centered(button, texts.t(KEY + "found"), textWidth));
            }
            return new Layout(frame, title, List.of(), List.of(), form, List.of(), statusLines);
        }
        List<Rect> tabs = new ArrayList<>();
        List<Text> tabLabels = new ArrayList<>();
        int tabW = (inner - 2 * GAP) / 3;
        for (Tab tab : Tab.values()) {
            Rect r = new Rect(x0 + tab.ordinal() * (tabW + GAP), frame.y() + TABS_Y, tabW, TAB_H);
            tabs.add(r);
            tabLabels.add(centered(r, texts.t(KEY + "tab." + tab.name().toLowerCase(Locale.ROOT)), textWidth));
        }
        Rect list = new Rect(x0, frame.y() + LIST_Y, inner, Math.max(0, h - LIST_Y - BOTTOM - STATUS_H - GAP));
        List<Pane> panes = List.of(network(list, view, texts, textWidth), cards(list, view, texts, textWidth),
                tariff(list, view, texts, textWidth));
        return new Layout(frame, title, List.copyOf(tabs), List.copyOf(tabLabels), null, panes, statusLines);
    }

    /** Pour poser des lignes l'une sous l'autre dans une liste. */
    private static final class Rows {
        final Rect list;
        final int x;
        final int w;
        int y;
        final List<Row> out = new ArrayList<>();

        Rows(Rect list) {
            this.list = list;
            this.x = list.x();
            this.w = list.w() - SCROLL_W;
            this.y = list.y();
        }

        Rect next(int height) {
            Rect r = new Rect(x, y, w, height);
            y += height + GAP;
            return r;
        }

        Pane pane(Tab tab) {
            int bottom = out.isEmpty() ? list.y() : out.get(out.size() - 1).box().y() + out.get(out.size() - 1).box().h();
            return new Pane(tab, list, List.copyOf(out), Math.max(0, bottom - list.y() - list.h()));
        }
    }

    /** Une ligne d'un seul texte, sur toute la largeur. */
    private static void single(Rows rows, String id, String text, ToIntFunction<String> textWidth) {
        Rect box = rows.next(LINE + 1);
        rows.out.add(new Row(id, box, List.of(line(box.x() + 2, box.y() + 1, box.w() - 4, text, textWidth)), List.of(),
                null, false));
    }

    // ---------------------------------------------------------------------------------------------
    // Réseau
    // ---------------------------------------------------------------------------------------------

    private static Pane network(Rect list, OperatorView view, Texts texts, ToIntFunction<String> textWidth) {
        Rows rows = new Rows(list);
        if (view.antennas().isEmpty()) {
            single(rows, "antennas-none", texts.t(KEY + "no_antennas"), textWidth);
        } else {
            single(rows, "load-legend", texts.t(KEY + "load_legend"), textWidth);
        }
        for (int i = 0; i < view.antennas().size(); i++) {
            OperatorView.AntennaLine a = view.antennas().get(i);
            Rect box = rows.next(BUTTON_H + 1 + 2 * LINE + 1);
            int tx = box.x() + 2;
            int full = box.w() - 4;
            int right = box.x() + box.w() - BUTTON_W - 1;
            List<Button> buttons = new ArrayList<>();
            List<Text> lines = new ArrayList<>();
            String where = texts.t(KEY + "antenna_where", dimension(a.dimension()), a.pos().getX(), a.pos().getY(),
                    a.pos().getZ());
            lines.add(line(tx, box.y() + 3, right - 4 - tx, where, textWidth));
            if (a.main()) {
                lines.add(line(right, box.y() + 3, BUTTON_W, texts.t(KEY + "main"), textWidth));
            } else if (view.rights().cards()) {
                Rect b = new Rect(right, box.y(), BUTTON_W, BUTTON_H);
                buttons.add(new Button(Action.SET_MAIN, a.id().toString(), 0L, b,
                        centered(b, texts.t(KEY + "set_main"), textWidth)));
            }
            String state = texts.t(KEY + "state." + a.state().getSerializedName());
            lines.add(line(tx, box.y() + BUTTON_H + 1, full, texts.t(KEY + "antenna_load", state,
                    texts.number(a.load().total()), texts.number(a.load().standby()),
                    texts.number(a.load().clients()), texts.number(a.load().traffic())), textWidth));
            lines.add(line(tx, box.y() + BUTTON_H + 1 + LINE, full, texts.t(KEY + "antenna_fe",
                    texts.number(a.feToday()), texts.number(a.feYesterday())), textWidth));
            rows.out.add(new Row("antenna-" + i, box, List.copyOf(lines), List.copyOf(buttons), null,
                    a.overReceive()));
        }
        single(rows, "relay-header", texts.t(KEY + "relay_header"), textWidth);
        for (int i = 0; i < view.relay().size(); i++) {
            PhoneNetwork.RelayState s = view.relay().get(i);
            Rect box = rows.next(2 * LINE + 1);
            String status = texts.t(KEY + "relay." + relayStatus(s));
            rows.out.add(new Row("relay-" + i, box, List.of(
                    line(box.x() + 2, box.y() + 1, box.w() - 4, s.name(), textWidth),
                    line(box.x() + 2, box.y() + 1 + LINE, box.w() - 4,
                            texts.t(KEY + (s.antennas() <= 1 ? "relay_line_one" : "relay_line"), s.antennas(),
                                    s.inService(), status), textWidth)),
                    List.of(), null, false));
        }
        return rows.pane(Tab.NETWORK);
    }

    /** L'état d'un opérateur au relais : joignable (une antenne en service), hors tension, ou sans antenne. */
    public static String relayStatus(PhoneNetwork.RelayState s) {
        return s.inService() > 0 ? "reachable" : s.antennas() > 0 ? "down" : "none";
    }

    /** Une dimension sans l'espace de noms du jeu : « overworld », « the_nether » ; celle d'un mod en entier. */
    static String dimension(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    // ---------------------------------------------------------------------------------------------
    // Cartes
    // ---------------------------------------------------------------------------------------------

    private static Pane cards(Rect list, OperatorView view, Texts texts, ToIntFunction<String> textWidth) {
        Rows rows = new Rows(list);
        boolean may = view.rights().cards();
        single(rows, "requests-header", texts.t(KEY + "requests_header", view.requests().size()), textWidth);
        if (view.requests().isEmpty()) {
            single(rows, "requests-none", texts.t(KEY + "no_requests"), textWidth);
        }
        for (int i = 0; i < view.requests().size(); i++) {
            OperatorView.RequestLine r = view.requests().get(i);
            Rect box = rows.next(BUTTON_H + 2);
            List<Button> buttons = new ArrayList<>();
            int textRight = box.x() + box.w() - 2;
            if (may) {
                buttons.add(rowButton(box, 1, Action.ISSUE, r.id().toString(), texts.t(KEY + "issue"), textWidth));
                buttons.add(rowButton(box, 0, Action.REFUSE, r.id().toString(), texts.t(KEY + "refuse"), textWidth));
                textRight = buttons.get(0).box().x() - 4;
            }
            rows.out.add(new Row("request-" + i, box, List.of(line(box.x() + 2, box.y() + 4, textRight - box.x() - 2,
                    r.playerName(), textWidth)), List.copyOf(buttons), null, false));
        }
        if (may) {
            Rect box = rows.next(BUTTON_H + 2);
            // Le texte entier et 4 px de marge de chaque côté (revue de la tâche 12, M1), jamais plus large que la ligne.
            String label = texts.t(KEY + "issue_anonymous");
            int w = Math.min(box.w() - 2, Math.max(2 * BUTTON_W + GAP, textWidth.applyAsInt(label) + 8));
            Rect b = new Rect(box.x() + 1, box.y() + 1, w, BUTTON_H);
            rows.out.add(new Row("anonymous", box, List.of(), List.of(new Button(Action.ISSUE_ANONYMOUS, "", 0L, b,
                    centered(b, label, textWidth))), null, false));
        }
        single(rows, "cards-header", texts.t(KEY + "cards_header", view.cardTotal()), textWidth);
        if (view.cards().isEmpty()) {
            single(rows, "cards-none", texts.t(KEY + "no_cards"), textWidth);
        }
        for (int i = 0; i < view.cards().size(); i++) {
            OperatorView.CardLine c = view.cards().get(i);
            Rect box = rows.next(2 * LINE + 2);
            List<Button> buttons = new ArrayList<>();
            int textRight = box.x() + box.w() - 2;
            if (may && c.state() != SimCard.State.DEACTIVATED) {
                boolean active = c.state() == SimCard.State.ACTIVE;
                buttons.add(rowButton(box, 1, active ? Action.SUSPEND : Action.REACTIVATE, c.simId().toString(),
                        texts.t(KEY + (active ? "suspend" : "reactivate")), textWidth));
                buttons.add(rowButton(box, 0, Action.REPLACE, c.simId().toString(), texts.t(KEY + "replace"),
                        textWidth));
                textRight = buttons.get(0).box().x() - 4;
            }
            int room = textRight - box.x() - 2;
            String state = texts.t(KEY + "card." + c.state().name().toLowerCase(Locale.ROOT));
            String holder = c.holderName().isEmpty() ? texts.t(KEY + "anonymous") : c.holderName();
            rows.out.add(new Row("card-" + i, box, List.of(
                    line(box.x() + 2, box.y() + 1, room, texts.t(KEY + "card_line", SimNumbers.format(c.number()),
                            state), textWidth),
                    line(box.x() + 2, box.y() + 1 + LINE, room, holder, textWidth)), List.copyOf(buttons), null,
                    false));
        }
        if (view.cardPages() > 1) {
            Rect box = rows.next(BUTTON_H + 2);
            List<Button> buttons = new ArrayList<>();
            Rect prev = new Rect(box.x() + 1, box.y() + 1, 24, BUTTON_H);
            Rect next = new Rect(box.x() + box.w() - 25, box.y() + 1, 24, BUTTON_H);
            if (view.cardPage() > 0) {
                buttons.add(new Button(Action.PAGE, "", view.cardPage() - 1L, prev, centered(prev, "<", textWidth)));
            }
            if (view.cardPage() + 1 < view.cardPages()) {
                buttons.add(new Button(Action.PAGE, "", view.cardPage() + 1L, next, centered(next, ">", textWidth)));
            }
            Text page = line(prev.x() + prev.w() + 4, box.y() + 4, next.x() - 4 - (prev.x() + prev.w() + 4),
                    texts.t(KEY + "page", view.cardPage() + 1, view.cardPages()), textWidth);
            rows.out.add(new Row("pages", box, List.of(page), List.copyOf(buttons), null, false));
        }
        return rows.pane(Tab.CARDS);
    }

    /** Un bouton d'une ligne, rangé de droite à gauche ({@code slot} 0 : le plus à droite). */
    private static Button rowButton(Rect box, int slot, Action action, String arg, String label,
            ToIntFunction<String> textWidth) {
        Rect b = new Rect(box.x() + box.w() - 1 - (slot + 1) * BUTTON_W - slot * GAP, box.y() + (box.h() - BUTTON_H) / 2,
                BUTTON_W, BUTTON_H);
        return new Button(action, arg, 0L, b, centered(b, label, textWidth));
    }

    // ---------------------------------------------------------------------------------------------
    // Tarif et caisse
    // ---------------------------------------------------------------------------------------------

    private static Pane tariff(Rect list, OperatorView view, Texts texts, ToIntFunction<String> textWidth) {
        Rows rows = new Rows(list);
        single(rows, "tariff", texts.t(KEY + "tariff_today", texts.coins(view.tariff())), textWidth);
        if (view.nextTariff() != null) {
            single(rows, "tariff-next", texts.t(KEY + "tariff_next", texts.coins(view.nextTariff())), textWidth);
        }
        single(rows, "cap", texts.t(KEY + "cap", texts.coins(view.cap())), textWidth);
        if (view.rights().tariff()) {
            Rect box = rows.next(LINE + BUTTON_H + 3);
            Text label = line(box.x() + 2, box.y() + 1, box.w() - 4, texts.t(KEY + "tariff_field"), textWidth);
            Rect field = new Rect(box.x() + 2, box.y() + LINE + 2, FIELD_W, BUTTON_H);
            Rect b = new Rect(field.x() + field.w() + 4, field.y(), BUTTON_W, BUTTON_H);
            rows.out.add(new Row("tariff-field", box, List.of(label), List.of(new Button(Action.SET_TARIFF, "", 0L, b,
                    centered(b, texts.t(KEY + "set_tariff"), textWidth))), field, false));
        }
        Rect box = rows.next(BUTTON_H + 1 + LINE + 1);
        List<Button> buttons = new ArrayList<>();
        int labelRoom = box.w() - 4;
        if (view.rights().till()) {
            Rect b = new Rect(box.x() + box.w() - 1 - BUTTON_W, box.y(), BUTTON_W, BUTTON_H);
            buttons.add(new Button(Action.OPEN_TILL, "", 0L, b, centered(b, texts.t(KEY + "open_till"), textWidth)));
            labelRoom = b.x() - 4 - box.x() - 2;
        }
        rows.out.add(new Row("till", box, List.of(
                line(box.x() + 2, box.y() + 3, labelRoom, texts.t(KEY + "till"), textWidth),
                line(box.x() + 2, box.y() + BUTTON_H + 1, box.w() - 4, texts.coins(view.till()), textWidth)),
                List.copyOf(buttons), null, false));
        return rows.pane(Tab.TARIFF);
    }

    // ---------------------------------------------------------------------------------------------
    // Textes
    // ---------------------------------------------------------------------------------------------

    /**
     * Un texte dans {@code room} pixels : à l'échelle 1 s'il tient, sinon rétréci jusqu'à {@value #MIN_SCALE}, sinon
     * coupé par « … » à ce plancher.
     */
    static Text line(int x, int y, int room, String full, ToIntFunction<String> textWidth) {
        String text = full == null ? "" : full;
        int room0 = Math.max(0, room);
        int width = textWidth.applyAsInt(text);
        if (width <= room0) {
            return new Text(x, y, room0, text, text, width, 1f);
        }
        float scale = room0 / (float) width;
        if (scale >= MIN_SCALE) {
            return new Text(x, y, room0, text, text, width, scale);
        }
        String shown = cut(text, (int) Math.floor(room0 / MIN_SCALE), textWidth);
        return new Text(x, y, room0, shown, text, textWidth.applyAsInt(shown), MIN_SCALE);
    }

    /**
     * Un message sur deux lignes au plus dans {@code area} : mots rangés en entier à la plus grande échelle de 1 à
     * {@value #MIN_SCALE} qui fait tenir le tout ; sinon, au plancher, la seconde ligne est coupée par « … ».
     */
    static List<Text> wrap(Rect area, @Nullable String message, ToIntFunction<String> textWidth) {
        String text = message == null ? "" : message.strip();
        if (text.isEmpty()) {
            return List.of();
        }
        String[] words = text.split("\s+");
        for (float scale = 1f; scale >= MIN_SCALE - 0.0001f; scale -= 0.05f) {
            int room = (int) Math.floor(area.w() / scale);
            List<String> lines = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (String w : words) {
                String next = cur.length() == 0 ? w : cur + " " + w;
                if (textWidth.applyAsInt(next) <= room || cur.length() == 0) {
                    cur.setLength(0);
                    cur.append(next);
                } else {
                    lines.add(cur.toString());
                    cur.setLength(0);
                    cur.append(w);
                }
            }
            lines.add(cur.toString());
            boolean fits = lines.size() <= 2 && lines.stream().allMatch(l -> textWidth.applyAsInt(l) <= room);
            float s = Math.max(MIN_SCALE, scale);
            if (fits) {
                List<Text> out = new ArrayList<>();
                for (int i = 0; i < lines.size(); i++) {
                    String l = lines.get(i);
                    out.add(new Text(area.x(), area.y() + i * LINE, area.w(), l, l, textWidth.applyAsInt(l), s));
                }
                return List.copyOf(out);
            }
        }
        // Au plancher : la première ligne pleine, le reste coupé par « … » sur la seconde.
        int room = (int) Math.floor(area.w() / MIN_SCALE);
        StringBuilder first = new StringBuilder();
        int i = 0;
        while (i < words.length) {
            String next = first.length() == 0 ? words[i] : first + " " + words[i];
            if (first.length() > 0 && textWidth.applyAsInt(next) > room) {
                break;
            }
            first.setLength(0);
            first.append(next);
            i++;
        }
        String head = cut(first.toString(), room, textWidth);
        List<Text> out = new ArrayList<>();
        out.add(new Text(area.x(), area.y(), area.w(), head, head, textWidth.applyAsInt(head), MIN_SCALE));
        if (i < words.length) {
            String rest = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
            String shown = cut(rest, room, textWidth);
            out.add(new Text(area.x(), area.y() + LINE, area.w(), shown, rest, textWidth.applyAsInt(shown), MIN_SCALE));
        }
        return List.copyOf(out);
    }

    /** Un libellé centré dans un bouton (2 px de marge de chaque côté), selon la même règle. */
    static Text centered(Rect button, String label, ToIntFunction<String> textWidth) {
        int room = Math.max(1, button.w() - 4);
        Text t = line(button.x() + 2, button.y() + (button.h() - 8) / 2 + 1, room, label, textWidth);
        int drawn = (int) Math.ceil(t.width() * t.scale());
        return new Text(button.x() + 2 + Math.max(0, (room - drawn) / 2), t.y(), room - Math.max(0, (room - drawn) / 2),
                t.shown(), t.full(), t.width(), t.scale());
    }

    /** Le texte coupé par « … » s'il dépasse {@code room} pixels (vide si même « … » ne tient pas). */
    static String cut(String text, int room, ToIntFunction<String> textWidth) {
        if (textWidth.applyAsInt(text) <= room) {
            return text;
        }
        int end = text.length();
        while (end > 0) {
            end = text.offsetByCodePoints(end, -1);
            String shown = text.substring(0, end).stripTrailing() + ELLIPSIS;
            if (textWidth.applyAsInt(shown) <= room) {
                return shown;
            }
        }
        return textWidth.applyAsInt(ELLIPSIS) <= room ? ELLIPSIS : "";
    }

    /** L'état d'une antenne, pour les essais : la clé de langue de son libellé. */
    static String stateKey(MachineState state) {
        return KEY + "state." + state.getSerializedName();
    }
}
