package dev.oneforallsurvival.phone.client;

import dev.oneforallsurvival.client.web.WebScreen.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

/**
 * La mise en page de l'écran de repli du téléphone, en coordonnées d'interface, sans Minecraft (testable) : la zone
 * utile dans le cadre, le clavier du code et les lignes des Réglages, avec un libellé au-dessus de chacun des trois
 * champs de code. Tout tient dans la zone utile de l'interface de 320 × 240 à la plus grande.
 */
public final class PhoneFallbackLayout {

    /** Les lignes des Réglages, de haut en bas. */
    public static final List<String> SETTINGS_ROWS = List.of("sim", "airplane", "wallpaper", "old", "new", "confirm",
            "change", "lock", "erase", "back");
    /** Les lignes qui portent un libellé au-dessus d'elles (les champs de code). */
    public static final List<String> LABELLED = List.of("old", "new", "confirm");
    static final int KEY_GAP = 3;
    static final int ROW_GAP = 2;
    static final int MIN_ROW = 10;
    static final int MAX_ROW = 20;
    /** La hauteur des boutons de l'accueil. */
    static final int HOME_BUTTON = 20;
    /** Les lignes de texte au-dessus de « Retirer » dans la feuille « Carte SIM » : numéro, titulaire, message (deux). */
    public static final int SIM_TEXT_LINES = 4;
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

    /**
     * Le texte de l'écran du code, de haut en bas : titre, points ({@code dot} px de côté), puis {@code lines} lignes de
     * message (une ; {@link #LOCKOUT_LINES} pendant un blocage : « bloqué » et le décompte), espacées de 2 px.
     * {@code bottom} est le premier y libre sous le texte. {@code PhoneFallbackScreen.drawCode} dessine à ces positions.
     */
    public record CodeText(int titleY, int dotsY, int dot, int messageY, int lineStep, int bottom) {}

    /** Le côté des points du code : 1/20 de la largeur utile, de 3 à 6 px. */
    public static int codeDot(Rect inner) {
        return Math.max(3, Math.min(6, inner.width() / 20));
    }

    public static CodeText codeText(Rect inner, int lineHeight, int lines) {
        int dot = codeDot(inner);
        int titleY = inner.y() + 6;
        int dotsY = titleY + lineHeight + 6;
        int messageY = dotsY + dot + 6;
        int lineStep = lineHeight + 2;
        return new CodeText(titleY, dotsY, dot, messageY, lineStep, messageY + lines * lineStep);
    }

    /**
     * Le bouton « Fond suivant » du choix du code (téléphone neuf) : toute la largeur, centré dans l'espace entre le
     * texte du code ({@link #codeText} avec une ligne de message : un téléphone neuf ne peut pas être bloqué) et le
     * haut du clavier, de {@value #MIN_ROW} à {@value #MAX_ROW} px de haut.
     */
    public static Rect setupWallpaper(Rect inner, int lineHeight) {
        int text = codeText(inner, lineHeight, 1).bottom();
        int keypadTop = keypad(inner).get(0).y();
        int room = keypadTop - text - 2 * KEY_GAP;
        int h = clamp(room);
        int y = text + KEY_GAP + Math.max(0, (room - h) / 2);
        return new Rect(inner.x(), y, inner.width(), h);
    }

    /**
     * L'accueil : « Réglages » en bas et, au-dessus, le bouton de l'appli « Antenne » ({@code antenna}, nul s'il ne se
     * montre pas), dessiné grisé ({@code antennaDimmed}) quand le serveur refuserait de l'ouvrir.
     */
    public record Home(Rect settings, Rect phone, @Nullable Rect antenna, boolean antennaDimmed) {}

    /** L'accueil, de bas en haut : « Réglages », « Téléphone » (lot T3, tâche 7), puis l'appli « Antenne ». */
    public static Home home(Rect inner, boolean founderApp, boolean founderAppAvailable) {
        int y = inner.y() + inner.height() - HOME_BUTTON - 4;
        Rect settings = new Rect(inner.x(), y, inner.width(), HOME_BUTTON);
        Rect phone = new Rect(inner.x(), y - HOME_BUTTON - 4, inner.width(), HOME_BUTTON);
        if (!founderApp) {
            return new Home(settings, phone, null, false);
        }
        return new Home(settings, phone, new Rect(inner.x(), phone.y() - HOME_BUTTON - 4, inner.width(),
                HOME_BUTTON), !founderAppAvailable);
    }

    // ---------------------------------------------------------------------------------------------
    // Lot T3, tâche 7 : appels et urgences
    // ---------------------------------------------------------------------------------------------

    /**
     * L'écran d'appel : le titre (nom, numéro ou service), dessous une ligne d'état sur {@value #MAX_TEXT_LINES} lignes
     * au plus (« Appel entrant », la durée, la position d'une urgence, le message de fin) ; en bas, toute la largeur,
     * « Répondre » au-dessus de « Refuser » (appel entrant), ou « Silence », « Haut-parleur » et « Raccrocher »
     * (sortant, en cours ; « Raccrocher » à la place de « Refuser »). {@code subBottom} : le premier y libre sous la ligne d'état.
     */
    public record CallScreen(int titleY, int subY, int subBottom, Rect decline, Rect answer, Rect mute, Rect speaker,
            Rect hangup) {}

    public static CallScreen callScreen(Rect inner, int lineHeight) {
        int titleY = inner.y() + 8;
        int subY = titleY + lineHeight + 4;
        int subBottom = subY + MAX_TEXT_LINES * (lineHeight + 1);
        int bottom = inner.y() + inner.height() - 4;
        // Toute la largeur : « Haut-parleur » ne tient pas en demi-largeur de 320 × 240 à 480 × 270.
        Rect hangup = new Rect(inner.x(), bottom - HOME_BUTTON, inner.width(), HOME_BUTTON);
        Rect speaker = new Rect(inner.x(), hangup.y() - 4 - HOME_BUTTON, inner.width(), HOME_BUTTON);
        Rect mute = new Rect(inner.x(), speaker.y() - 4 - HOME_BUTTON, inner.width(), HOME_BUTTON);
        Rect decline = hangup;
        Rect answer = speaker;
        return new CallScreen(titleY, subY, subBottom, decline, answer, mute, speaker, hangup);
    }

    /**
     * L'appli « Téléphone » du repli : le numéro composé en haut, « Police 17 », « Secours 15 », « Retour »
     * et « Appeler » côte à côte, puis le clavier ({@link #keypad} : 1 à 9, Effacer, 0 ; « Appeler » ne tient pas dans
     * une touche à 320 × 240). {@code messageY} : une ligne pour le message d'un appel refusé, sous le numéro.
     */
    public record Dial(int numberY, int messageY, Rect police, Rect medical, Rect back, List<Rect> keys, Rect call) {}

    public static Dial dial(Rect inner, int lineHeight) {
        List<Rect> keys = keypad(inner);
        int numberY = inner.y() + 2;
        int messageY = numberY + lineHeight + 2;
        int top = messageY + lineHeight + 3;
        int rh = clamp((keys.get(0).y() - top - 4 * KEY_GAP) / 3);
        int half = (inner.width() - KEY_GAP) / 2;
        // « Secours 15 » ne tient pas en demi-largeur à 320 × 240 : les urgences ont chacune leur ligne.
        Rect police = new Rect(inner.x(), top, inner.width(), rh);
        Rect medical = new Rect(inner.x(), top + rh + KEY_GAP, inner.width(), rh);
        Rect back = new Rect(inner.x(), medical.y() + rh + KEY_GAP, half, rh);
        Rect call = new Rect(inner.x() + inner.width() - half, back.y(), half, rh);
        return new Dial(numberY, messageY, police, medical, back, keys, call);
    }

    /**
     * Le bouton « Urgence » de l'écran verrouillé : entre le texte du code (avec les lignes d'un blocage : il reste
     * touchable pendant un blocage) et le haut du clavier, toute la largeur.
     */
    public static Rect lockSos(Rect inner, int lineHeight) {
        int text = codeText(inner, lineHeight, LOCKOUT_LINES).bottom();
        int keypadTop = keypad(inner).get(0).y();
        int room = keypadTop - text - 2 * KEY_GAP;
        int h = clamp(room);
        int y = text + KEY_GAP + Math.max(0, (room - h) / 2);
        return new Rect(inner.x(), y, inner.width(), h);
    }

    /** Le panneau « Urgence » de l'écran verrouillé : « Police 17 », « Secours 15 » et « Annuler », l'un sous l'autre. */
    public record Sos(int titleY, Rect police, Rect medical, Rect cancel) {}

    public static Sos sos(Rect inner, int lineHeight) {
        int titleY = inner.y() + 8;
        int y = titleY + lineHeight + 8;
        Rect police = new Rect(inner.x(), y, inner.width(), HOME_BUTTON);
        Rect medical = new Rect(inner.x(), y + HOME_BUTTON + 4, inner.width(), HOME_BUTTON);
        Rect cancel = new Rect(inner.x(), inner.y() + inner.height() - HOME_BUTTON - 4, inner.width(), HOME_BUTTON);
        return new Sos(titleY, police, medical, cancel);
    }

    /** Les Réglages : deux lignes de texte, puis dix lignes (« Carte SIM » en tête) ; un libellé d'une ligne au-dessus de chaque champ de code. */
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

    /**
     * La feuille « Carte SIM » (lot T2) : {@value #SIM_TEXT_LINES} lignes de texte (la carte logée, le message du
     * dernier geste), « Retirer » dessous, « Retour » en bas,
     * et entre eux une liste qui défile (une ligne par carte de l'inventaire, « Demander une carte », puis un opérateur
     * par ligne). {@code rows} ne garde que les lignes entièrement visibles au défilement {@code scroll} (ramené entre 0
     * et {@code maxScroll}) ; {@code rowStep} est le pas d'une ligne (hauteur et écart). Quand la liste défile, une barre
     * fine ({@code track}, {@value #SIM_SCROLL_W} px) et son curseur ({@code thumb}) l'annoncent, dans la marge droite du
     * cadre, {@value #SIM_SCROLL_GAP} px après la liste (la marge fait 3 px au moins) : les lignes gardent toute la largeur
     * de la liste ; sinon les deux sont nuls (revue de la tâche 12, I2, et sa relecture).
     */
    public record SimSheet(int textTop, Rect remove, Rect list, Map<String, Rect> rows, int scroll, int maxScroll,
            int rowStep, Rect back, @Nullable Rect track, @Nullable Rect thumb) {}

    /** La largeur de la barre de défilement de la feuille « Carte SIM ». */
    public static final int SIM_SCROLL_W = 2;
    /** L'écart entre la liste et la barre de défilement, dans la marge du cadre. */
    public static final int SIM_SCROLL_GAP = 1;

    /** Les lignes de la feuille « Carte SIM », de {@value #MIN_ROW} à {@value #MAX_ROW} px : une ligne de texte et 5 px. */
    public static int simRow(int lineHeight) {
        return clamp(lineHeight + 5);
    }

    public static SimSheet simSheet(Rect inner, int lineHeight, List<String> ids, int scroll) {
        int rh = simRow(lineHeight);
        int step = rh + ROW_GAP;
        int text = SIM_TEXT_LINES * (lineHeight + 1);
        Rect remove = new Rect(inner.x(), inner.y() + text, inner.width(), rh);
        Rect back = new Rect(inner.x(), inner.y() + inner.height() - rh, inner.width(), rh);
        int listTop = remove.y() + rh + ROW_GAP;
        int listHeight = Math.max(0, back.y() - ROW_GAP - listTop);
        Rect list = new Rect(inner.x(), listTop, inner.width(), listHeight);
        int total = ids.isEmpty() ? 0 : ids.size() * step - ROW_GAP;
        int maxScroll = Math.max(0, total - listHeight);
        int shown = Math.max(0, Math.min(maxScroll, scroll));
        Rect track = null;
        Rect thumb = null;
        int rowWidth = inner.width();
        if (maxScroll > 0) {
            track = new Rect(inner.x() + inner.width() + SIM_SCROLL_GAP, listTop, SIM_SCROLL_W, listHeight);
            int thumbH = Math.min(listHeight, Math.max(6, listHeight * listHeight / (listHeight + maxScroll)));
            int thumbY = listTop + (int) ((long) (listHeight - thumbH) * shown / maxScroll);
            thumb = new Rect(track.x(), thumbY, SIM_SCROLL_W, thumbH);
        }
        Map<String, Rect> rows = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            int y = listTop + i * step - shown;
            if (y >= listTop && y + rh <= listTop + listHeight) {
                rows.put(ids.get(i), new Rect(inner.x(), y, rowWidth, rh));
            }
        }
        return new SimSheet(inner.y(), remove, list, Collections.unmodifiableMap(rows), shown, maxScroll, step, back,
                track, thumb);
    }

    /**
     * Le texte tel qu'il tient dans {@code room} px (largeurs mesurées par {@code width}, la police du jeu à l'écran) :
     * entier s'il tient, sinon coupé entre deux caractères et fini par « … » ; vide si même « … » ne tient pas.
     */
    public static String ellipsize(String text, int room, ToIntFunction<String> width) {
        if (text == null || text.isEmpty() || width.applyAsInt(text) <= room) {
            return text == null ? "" : text;
        }
        String dots = "…";
        if (width.applyAsInt(dots) > room) {
            return "";
        }
        int end = text.length();
        while (end > 0) {
            end = text.offsetByCodePoints(end, -1);
            String cut = text.substring(0, end).stripTrailing() + dots;
            if (width.applyAsInt(cut) <= room) {
                return cut;
            }
        }
        return dots;
    }

    /**
     * Plancher de l'échelle de tout texte de l'écran de repli : en dessous, il ne se lit plus sans effort. Un texte plus
     * long passe à la ligne ({@link #wrap}), ou, sur une seule ligne (barre d'état, bouton), est coupé par « … » (revue de
     * la tâche 8, tour 2).
     */
    public static final float MIN_TEXT_SCALE = 0.75f;
    /** La barre d'état suit le même plancher. */
    public static final float MIN_STATUS_SCALE = MIN_TEXT_SCALE;
    /** Un message ou un texte d'état tient sur deux lignes au plus. */
    public static final int MAX_TEXT_LINES = 2;

    /** Le texte de la barre d'état du repli et l'échelle à laquelle il se dessine ; {@code cut} : coupé par « … ». */
    public record StatusLine(String text, float scale, boolean cut) {}

    /**
     * Une ligne : {@code prefix} puis {@code carrier}, rétrécis pour tenir dans {@code room} px, jamais sous
     * {@link #MIN_STATUS_SCALE} ; s'il le faut encore, {@code carrier} est coupé par « … ».
     */
    public static StatusLine statusLine(String prefix, String carrier, int room, ToIntFunction<String> width) {
        String full = prefix + carrier;
        float scale = fit(width.applyAsInt(full), room, 1f);
        if (scale >= MIN_STATUS_SCALE) {
            return new StatusLine(full, scale, false);
        }
        int textRoom = (int) Math.floor(room / MIN_STATUS_SCALE) - width.applyAsInt(prefix);
        return new StatusLine(prefix + ellipsize(carrier, Math.max(0, textRoom), width), MIN_STATUS_SCALE, true);
    }

    /** La largeur des quatre barres de signal de la barre d'état, écart compris. */
    public static final int BARS_WIDTH = 14;

    /** La place du texte de la barre d'état, à gauche de la batterie (et des barres, qui se retirent de cette place). */
    public static int statusRoom(int innerWidth, int batteryWidth) {
        return innerWidth - batteryWidth - 4;
    }

    /** La barre d'état mise en page : sa ligne, et si les barres se dessinent. */
    public record Status(StatusLine line, boolean bars) {}

    /**
     * La barre d'état du repli, à gauche de la batterie : l'heure ({@code clock}, vide verrouillé) puis l'opérateur ou
     * l'état de la carte, et les barres ({@code barsWidth} px, 0 sans barres) à droite. Si le texte ne tient pas au
     * plancher, l'heure cède d'abord sa place (l'accueil la montre en grand), puis des barres toutes vides
     * ({@code barsOptional} : elles ne disent rien de plus que « Pas de service ») ; un nom d'opérateur trop long est
     * enfin coupé par « … ». {@code room} : la place sans les barres.
     */
    public static Status status(String clock, String carrier, int barsWidth, boolean barsOptional, int room,
            ToIntFunction<String> width) {
        boolean bars = barsWidth > 0;
        int withBars = room - barsWidth;
        StatusLine line = statusLine(clock, carrier, withBars, width);
        if (line.cut() && !clock.isEmpty()) {
            line = statusLine("", carrier, withBars, width);
        }
        if (line.cut() && bars && barsOptional) {
            bars = false;
            line = statusLine("", carrier, room, width);
        }
        return new Status(line, bars);
    }

    /** Un texte passé à la ligne : ses lignes, leur échelle commune, et s'il a fallu couper par « … ». */
    public record Wrapped(List<String> lines, float scale, boolean cut) {}

    /**
     * Le texte passé à la ligne entre les mots pour tenir dans {@code room} px, en {@code maxLines} lignes au plus :
     * à l'échelle 1 si c'est possible, sinon au plancher {@link #MIN_TEXT_SCALE} ; si cela ne suffit pas, la dernière
     * ligne est coupée par « … » (un mot plus long qu'une ligne aussi). Jamais rétréci sous le plancher.
     */
    public static Wrapped wrap(String text, int room, ToIntFunction<String> width, int maxLines) {
        String s = text == null ? "" : text.strip();
        for (float scale : new float[] {1f, MIN_TEXT_SCALE}) {
            int lineRoom = (int) Math.floor(room / scale);
            List<String> lines = words(s, lineRoom, width);
            if (lines.size() <= maxLines && lines.stream().allMatch(l -> width.applyAsInt(l) <= lineRoom)) {
                return new Wrapped(Collections.unmodifiableList(lines), scale, false);
            }
        }
        int lineRoom = (int) Math.floor(room / MIN_TEXT_SCALE);
        List<String> lines = words(s, lineRoom, width);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(maxLines - 1, lines.size()); i++) {
            String l = lines.get(i);
            out.add(width.applyAsInt(l) <= lineRoom ? l : ellipsize(l, lineRoom, width));
        }
        String rest = String.join(" ", lines.subList(Math.min(maxLines - 1, lines.size()), lines.size()));
        out.add(ellipsize(rest, lineRoom, width));
        return new Wrapped(Collections.unmodifiableList(out), MIN_TEXT_SCALE, true);
    }

    /** Les lignes d'un passage à la ligne glouton entre les mots ; un mot trop long reste seul sur sa ligne. */
    private static List<String> words(String text, int lineRoom, ToIntFunction<String> width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" +")) {
            if (word.isEmpty()) {
                continue;
            }
            String tried = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && width.applyAsInt(tried) > lineRoom) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(tried);
            }
        }
        if (line.length() > 0 || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** Les lignes de message d'un blocage : « Téléphone bloqué », puis le décompte sur deux lignes au plus. */
    public static final int LOCKOUT_LINES = 1 + MAX_TEXT_LINES;

    /** L'espace insécable, qui colle les mots d'une durée le temps du passage à la ligne. */
    private static final char NO_BREAK = 160;

    /**
     * Le décompte d'un blocage ({@code sentence}, qui contient {@code duration}) passé à la ligne par {@link #wrap},
     * sans jamais couper la durée en deux (« 59 min » en bout de ligne et « 59 s » dessous se lit mal).
     */
    public static Wrapped retryLines(String sentence, String duration, int room, ToIntFunction<String> width) {
        String glued = sentence.replace(duration, duration.replace(' ', NO_BREAK));
        Wrapped wrapped = wrap(glued, room, s -> width.applyAsInt(s.replace(NO_BREAK, ' ')), MAX_TEXT_LINES);
        return new Wrapped(wrapped.lines().stream().map(l -> l.replace(NO_BREAK, ' ')).toList(), wrapped.scale(),
                wrapped.cut());
    }

    /** Le libellé d'un bouton de cette largeur et son échelle : rétréci jusqu'au plancher, puis coupé par « … ». */
    public static StatusLine button(String text, int buttonWidth, ToIntFunction<String> width) {
        return statusLine("", text, textRoom(buttonWidth), width);
    }

    /**
     * Une ligne de la feuille « Carte SIM » qui porte une donnée libre (une carte {@code sim-choice-*}, un opérateur
     * {@code request-<id>}) : coupée par « … » si elle ne tient pas. Un texte fixe, lui, est rétréci par son bouton.
     */
    public static boolean cutWithEllipsis(String id) {
        return id.startsWith("sim-choice-") || (id.startsWith("request-") && !id.equals("request-none"));
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
