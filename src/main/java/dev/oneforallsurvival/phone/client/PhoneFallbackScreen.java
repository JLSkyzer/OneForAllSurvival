package dev.oneforallsurvival.phone.client;

import dev.oneforallsurvival.client.web.WebScreen.Rect;
import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneActionPacket.Action;
import dev.oneforallsurvival.phone.PhoneCode;
import dev.oneforallsurvival.phone.PhoneNetView;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import dev.oneforallsurvival.phone.PhoneWallpapers;
import dev.oneforallsurvival.phone.call.PhoneCallActionPacket;
import dev.oneforallsurvival.phone.net.PhoneSims;
import java.util.ArrayList;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Le téléphone en interface Minecraft classique, quand le navigateur du jeu ne sert pas : même cadre que la page, mêmes
 * gestes ({@link PhoneActionPacket}), mêmes états. Écran du code (clavier 0 à 9, Effacer, points ; au choix du code
 * d'un téléphone neuf, un bouton « Fond suivant » qui fait défiler les fonds avant le code, envoyés ensemble par
 * {@link Action#SETUP} ; pas d'animation de bienvenue ici), accueil (Réglages),
 * Réglages (carte SIM, mode avion, fond suivant, changement du code par trois champs de chiffres, verrouillage,
 * effacement). Lot T2 : barre d'état avec l'opérateur (ou l'état de la carte) et quatre barres, « Mode avion » à leur
 * place ; feuille « Carte SIM » (la carte logée, « Retirer », une ligne par carte de l'inventaire, « Demander une
 * carte » et sa liste, qui défile à la molette) ; bouton « Antenne » à l'accueil du fondateur, grisé quand le serveur
 * ne l'ouvrirait pas (il reste touchable : le serveur répond « Pas de réseau »). Mêmes gestes que la page
 * ({@code INSERT_SIM}, {@code REMOVE_SIM}, {@code REQUEST_SIM}, {@code OPEN_OPERATOR_APP}). Le serveur juge tout ;
 * l'écran ne garde un code saisi que le temps de l'envoyer et ne l'écrit nulle part. Échap ferme et prévient le serveur.
 */
public final class PhoneFallbackScreen extends Screen implements PhoneClient.Shown {

    private static final String KEY = "screen.oneforallsurvival.phone.";
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SOFT = 0xFFD8DCE4;
    private static final int RED = 0xFFFF6B6B;

    enum Page { CODE, HOME, SETTINGS, SIM, DIAL, SOS }

    /** Les chiffres au plus du clavier de l'appli « Téléphone » (comme {@code DIAL_MAX} de la page). */
    static final int DIAL_MAX = 15;

    private PhoneStatePacket state;
    /** Le dernier état d'appel reçu (lot T3). */
    @Nullable private dev.oneforallsurvival.phone.call.PhoneCallStatePacket call;
    /** Ce que l'écran d'appel montre (lot T3, tâche 7). */
    private final PhoneFallbackCall calls = new PhoneFallbackCall();
    /** Le mode d'appel montré par la dernière construction des boutons. */
    private PhoneFallbackCall.Mode shownCall = PhoneFallbackCall.Mode.NONE;
    /** Le numéro en cours de composition de l'appli « Téléphone » ; jamais écrit ailleurs qu'à l'écran. */
    private final StringBuilder dialed = new StringBuilder();
    private long receivedAt = Util.getMillis();
    private long lockoutEnd;
    private Page page;
    private final StringBuilder entry = new StringBuilder();
    @Nullable private String setupFirst;
    /** Le fond choisi au choix du code, envoyé avec lui ; affiché tout de suite. */
    private String setupWallpaper;
    private Component message = Component.empty();
    /** Le message vient d'un appel refusé (une urgence pendant un blocage) : dessiné même pendant le blocage. */
    private boolean messageFromCall;
    private boolean messageAlert;
    private boolean eraseArmed;
    private boolean byServer;
    private Rect frame = new Rect(0, 0, 0, 0);
    private Rect inner = new Rect(0, 0, 0, 0);
    @Nullable private PhoneFallbackLayout.Settings settingsLayout;
    private final Map<String, AbstractWidget> named = new LinkedHashMap<>();
    private EditBox oldBox;
    private EditBox newBox;
    private EditBox confirmBox;
    private boolean lockedOutShown;
    /** Feuille « Carte SIM » : la liste des demandes dépliée, et le défilement de la liste (px d'interface). */
    private boolean requestsOpen;
    private int simScroll;
    @Nullable private PhoneFallbackLayout.SimSheet simLayout;
    /** L'appli « Antenne » (panneau Minecraft) passe par-dessus : la session du téléphone reste ouverte. */
    private boolean toAntenna;

    public PhoneFallbackScreen(PhoneStatePacket state) {
        super(Component.translatable("item.oneforallsurvival.phone"));
        this.state = state;
        this.page = state.lock() == Lock.UNLOCKED ? Page.HOME : Page.CODE;
        this.setupWallpaper = shownWallpaper(state.wallpaper());
        noteLockout();
        // Face ID se joue à l'ouverture : l'issue arrive avec l'OPEN (déverrouillé par le visage, ou visage couvert).
        result(state);
    }

    // --- PhoneClient.Shown -----------------------------------------------------------------------------------------

    @Override
    public int session() {
        return state.session();
    }

    @Override
    public void show(PhoneStatePacket next) {
        Lock before = state.lock();
        // Un état qui ne change que la batterie ou l'heure ne reconstruit rien : le champ en cours garde le focus.
        boolean widgetsChange = next.lock() != before || next.airplane() != state.airplane()
                || !next.wallpaper().equals(state.wallpaper()) || next.lockoutSeconds() > 0 || lockedOutShown
                || next.last() != Result.NONE || !next.net().equals(state.net());
        state = next;
        receivedAt = Util.getMillis();
        noteLockout();
        if (next.lock() != before) {
            entry.setLength(0);
            setupFirst = null;
            eraseArmed = false;
            requestsOpen = false;
            simScroll = 0;
            message = Component.empty();
            page = next.lock() == Lock.UNLOCKED ? Page.HOME : Page.CODE;
            if (next.lock() == Lock.SETUP) {
                setupWallpaper = shownWallpaper(next.wallpaper());
            }
        }
        result(next);
        if (widgetsChange) {
            rebuild();
        }
    }

    @Override
    public void closedByServer() {
        byServer = true;
    }

    /**
     * Lot T3, tâche 7 : l'état d'appel. Un appel d'ici prend tout l'écran (entrant, sortant, en cours, puis la fin deux
     * secondes) ; un message hors appel (appel composé refusé) s'écrit comme l'issue d'un geste.
     */
    @Override
    public void showCall(dev.oneforallsurvival.phone.call.PhoneCallStatePacket next) {
        call = next;
        String text = next.message() == null ? "" : next.message().getString();
        String outside = calls.accept(next, text, Util.getMillis());
        if (!outside.isEmpty()) {
            alert(Component.literal(outside));
            messageFromCall = true;
        }
        rebuild();
    }

    /** Le dernier état d'appel reçu par cet écran (nul avant le premier). */
    @Nullable
    public dev.oneforallsurvival.phone.call.PhoneCallStatePacket call() {
        return call;
    }

    // --- Lu par un automate (tâche 11) -----------------------------------------------------------------------------

    /** Le dernier état montré. */
    public PhoneStatePacket state() {
        return state;
    }

    /** La page affichée. */
    public String page() {
        return page.name();
    }

    /**
     * Les commandes de l'écran par nom : {@code key-0} à {@code key-9}, {@code key-clear}, {@code setup-wallpaper}
     * (choix du code d'un téléphone neuf) ; {@code settings}, {@code app-antenna} (fondateur, grisée ou non) ;
     * {@code sim}, {@code airplane}, {@code wallpaper}, {@code old}, {@code new}, {@code confirm}, {@code change},
     * {@code lock}, {@code erase}, {@code back} ; feuille « Carte SIM » : {@code sim-remove},
     * {@code sim-choice-<case>}, {@code sim-request}, {@code request-<id>}, {@code back} (lignes visibles seulement).
     * Lot T3 : {@code app-phone} (accueil) ; appli « Téléphone » : {@code key-0} à {@code key-9}, {@code key-clear},
     * {@code dial-call}, {@code dial-17}, {@code dial-15}, {@code back} ; écran verrouillé : {@code lock-sos}, puis
     * {@code sos-17}, {@code sos-15}, {@code sos-cancel} ; appel : {@code call-decline}, {@code call-answer} ou
     * {@code call-mute}, {@code call-speaker}, {@code call-hangup}.
     */
    public Map<String, AbstractWidget> widgets() {
        return Collections.unmodifiableMap(named);
    }

    /** Le cadre de l'écran du téléphone, en coordonnées d'interface. */
    public Rect frameRect() {
        return frame;
    }

    // --- Screen ----------------------------------------------------------------------------------------------------

    /**
     * L'appli « Antenne » (lot T2, tâche 10) : la vue de source {@code PHONE} ouvre le panneau Minecraft de la tâche 9
     * par-dessus cet écran (mêmes boutons, mêmes droits) ; Échap y revient, sur le dernier état, sans fermer la session.
     */
    void openAntenna(dev.oneforallsurvival.phone.net.OperatorView view) {
        if (minecraft == null) {
            return;
        }
        toAntenna = true;
        minecraft.setScreen(new dev.oneforallsurvival.phone.net.client.OperatorPanelScreen(view, this));
    }

    /**
     * Le panneau de l'appli posé par-dessus est retiré par un autre écran que le retour d'Échap : la session du
     * téléphone, que cet écran caché n'a pas fermée, l'est maintenant (sauf si le serveur l'a déjà fermée).
     */
    public void closedUnderPanel() {
        toAntenna = false;
        if (!byServer) {
            byServer = true;
            PhoneClient.sendClose(state.session());
        }
    }

    @Override
    protected void init() {
        toAntenna = false;
        frame = PhoneFrameLayout.fit(width, height);
        inner = PhoneFallbackLayout.inner(frame, font.lineHeight);
        rebuild();
    }

    private void rebuild() {
        if (font == null) {
            return; // pas encore ouvert : init() le fera
        }
        String keptOld = oldBox == null ? "" : oldBox.getValue();
        String keptNew = newBox == null ? "" : newBox.getValue();
        String keptConfirm = confirmBox == null ? "" : confirmBox.getValue();
        clearWidgets();
        named.clear();
        oldBox = newBox = confirmBox = null;
        settingsLayout = null;
        simLayout = null;
        lockedOutShown = false;
        shownCall = calls.mode(Util.getMillis());
        if (shownCall != PhoneFallbackCall.Mode.NONE) {
            buildCall();
            return;
        }
        switch (page) {
            case CODE -> buildKeypad();
            case HOME -> buildHome();
            case SETTINGS -> buildSettings(keptOld, keptNew, keptConfirm);
            case SIM -> buildSim();
            case DIAL -> buildDial();
            case SOS -> buildSos();
        }
    }

    // --- Appels (lot T3, tâche 7) ----------------------------------------------------------------------------------

    /** L'écran d'appel : Refuser et Répondre, ou Silence, Haut-parleur et Raccrocher ; rien pendant la fin. */
    private void buildCall() {
        PhoneFallbackLayout.CallScreen layout = PhoneFallbackLayout.callScreen(inner, font.lineHeight);
        dev.oneforallsurvival.phone.call.PhoneCallStatePacket live = calls.live();
        boolean unlocked = state.lock() == Lock.UNLOCKED;
        for (String id : PhoneFallbackCall.buttons(shownCall)) {
            if (live == null) {
                break;
            }
            String callId = Integer.toString(live.callId());
            FitButton b = switch (id) {
                case "call-decline" -> new FitButton(layout.decline(), callText("decline"),
                        btn -> sendCall(PhoneCallActionPacket.Action.DECLINE, callId), null);
                case "call-answer" -> new FitButton(layout.answer(), callText("answer"),
                        btn -> sendCall(PhoneCallActionPacket.Action.ANSWER, callId), null);
                case "call-mute" -> toggle(layout.mute(), "mute", live.muted(),
                        btn -> sendCall(PhoneCallActionPacket.Action.MUTE, live.muted() ? "off" : "on"));
                case "call-speaker" -> toggle(layout.speaker(), "speaker", live.speaker(),
                        btn -> sendCall(PhoneCallActionPacket.Action.SPEAKER, live.speaker() ? "off" : "on"));
                default -> new FitButton(layout.hangup(), callText("hangup"),
                        btn -> sendCall(PhoneCallActionPacket.Action.HANGUP, callId), null);
            };
            // Verrouillé, le serveur refuse le silence et le haut-parleur : grisés, comme dans la page.
            b.active = PhoneFallbackCall.active(id, unlocked);
            add(id, b);
        }
    }

    private static Component callText(String name) {
        return Component.translatable(PhoneFallbackCall.KEY + name);
    }

    /** Silence ou Haut-parleur : en service, le bouton est entouré d'un trait clair ({@link FitButton#lit}). */
    private static FitButton toggle(Rect r, String name, boolean on, Button.OnPress press) {
        FitButton b = new FitButton(r, callText(name), press, null);
        b.lit = on;
        return b;
    }

    private static Component text(PhoneFallbackCall.Text t) {
        return t.key() == null ? Component.literal(t.literal())
                : Component.translatable(t.key()).append(t.literal());
    }

    private void sendCall(PhoneCallActionPacket.Action action, String first) {
        PhoneClient.sendCall(new PhoneCallActionPacket(state.session(), action, first,
                ""));
    }

    private void dial(String number) {
        if (!number.isEmpty()) {
            sendCall(PhoneCallActionPacket.Action.DIAL, number);
        }
    }

    /** L'appli « Téléphone » : clavier, Effacer, Appeler ; « Police 17 », « Secours 15 » ; « Retour » à l'accueil. */
    private void buildDial() {
        PhoneFallbackLayout.Dial layout = PhoneFallbackLayout.dial(inner, font.lineHeight);
        List<Rect> keys = layout.keys();
        for (int d = 1; d <= 9; d++) {
            dialKey(String.valueOf(d), keys.get(d - 1));
        }
        dialKey("0", keys.get(10));
        Component word = Component.translatable(KEY + "clear");
        Rect r = keys.get(9);
        boolean symbol = PhoneFallbackLayout.clearAsSymbol(font.width(word), r.width());
        FitButton clear = new FitButton(r, symbol ? Component.literal("←") : word, b -> {
            if (dialed.length() > 0) {
                dialed.setLength(dialed.length() - 1);
            }
        }, word);
        if (symbol) {
            clear.setTooltip(Tooltip.create(word));
        }
        add("key-clear", clear);
        add("dial-call", new FitButton(layout.call(), callText("dial"), b -> {
            String number = dialed.toString();
            message = Component.empty();
            dial(number);
        }, null));
        add("dial-17", new FitButton(layout.police(), callText("sos_police"), b -> dial("17"), null));
        add("dial-15", new FitButton(layout.medical(), callText("sos_medical"), b -> dial("15"), null));
        add("back", new FitButton(layout.back(), Component.translatable(KEY + "back"), b -> {
            page = Page.HOME;
            dialed.setLength(0);
            message = Component.empty();
            rebuild();
        }, null));
    }

    private void dialKey(String digit, Rect r) {
        add("key-" + digit, new FitButton(r, Component.literal(digit), b -> dialDigit(digit.charAt(0)), null));
    }

    private void dialDigit(char d) {
        if (dialed.length() < DIAL_MAX) {
            dialed.append(d);
        }
    }

    /** Le panneau « Urgence » de l'écran verrouillé : les deux numéros composent, « Annuler » revient au code. */
    private void buildSos() {
        PhoneFallbackLayout.Sos layout = PhoneFallbackLayout.sos(inner, font.lineHeight);
        add("sos-17", new FitButton(layout.police(), callText("sos_police"), b -> sosDial("17"), null));
        add("sos-15", new FitButton(layout.medical(), callText("sos_medical"), b -> sosDial("15"), null));
        add("sos-cancel", new FitButton(layout.cancel(), callText("cancel"), b -> {
            page = Page.CODE;
            rebuild();
        }, null));
    }

    private void sosDial(String number) {
        page = Page.CODE;
        message = Component.empty();
        dial(number);
        rebuild();
    }

    /** Le clavier : trois colonnes 1-9, puis Effacer et 0, dans le bas de l'écran ({@link PhoneFallbackLayout#keypad}). */
    private void buildKeypad() {
        List<Rect> keys = PhoneFallbackLayout.keypad(inner);
        boolean active = !lockedOut();
        for (int d = 1; d <= 9; d++) {
            key(String.valueOf(d), keys.get(d - 1), active);
        }
        // « Effacer » rétréci devient illisible dans une petite touche : une flèche, avec le mot en bulle et à la lecture.
        Component word = Component.translatable(KEY + "clear");
        Rect r = keys.get(9);
        boolean symbol = PhoneFallbackLayout.clearAsSymbol(font.width(word), r.width());
        FitButton clear = new FitButton(r, symbol ? Component.literal("\u2190") : word, b -> backspace(), word);
        if (symbol) {
            clear.setTooltip(Tooltip.create(word));
        }
        clear.active = active;
        add("key-clear", clear);
        key("0", keys.get(10), active);
        lockedOutShown = !active;
        if (state.lock() == Lock.SETUP) {
            add("setup-wallpaper", new FitButton(PhoneFallbackLayout.setupWallpaper(inner, font.lineHeight),
                    Component.translatable(KEY + "wallpaper",
                            Component.translatable(KEY + "wallpaper." + setupWallpaper)),
                    b -> {
                        setupWallpaper = nextWallpaper(setupWallpaper);
                        rebuild();
                    }, null));
        }
        if (state.lock() == Lock.LOCKED) {
            // « Urgence » : 17 et 15 sans le code, même pendant un blocage (comme la page).
            add("lock-sos", new FitButton(PhoneFallbackLayout.lockSos(inner, font.lineHeight), callText("sos"), b -> {
                page = Page.SOS;
                rebuild();
            }, null));
        }
    }

    private void key(String digit, Rect r, boolean active) {
        FitButton b = new FitButton(r, Component.literal(digit), btn -> digit(digit.charAt(0)), null);
        b.active = active;
        add("key-" + digit, b);
    }

    private void buildHome() {
        PhoneFallbackLayout.Home home = PhoneFallbackLayout.home(inner, net().founderApp(),
                net().founderAppAvailable());
        add("settings", new FitButton(home.settings(), Component.translatable(KEY + "settings"), b -> {
            page = Page.SETTINGS;
            message = Component.empty();
            rebuild();
        }, null));
        add("app-phone", new FitButton(home.phone(), callText("app"), b -> {
            page = Page.DIAL;
            dialed.setLength(0);
            message = Component.empty();
            rebuild();
        }, null));
        if (home.antenna() != null) {
            // L'appli « Antenne » : le geste part ; la vue du serveur ouvre le panneau Minecraft par-dessus (tâche 10).
            // Grisée, elle part quand même : le serveur répond par son refus (tâche 13b).
            FitButton antenna = new FitButton(home.antenna(), Component.translatable(KEY + "antenna_app"),
                    b -> send(Action.OPEN_OPERATOR_APP, "", "", false), null);
            antenna.dimmed = home.antennaDimmed();
            add("app-antenna", antenna);
        }
    }

    private void buildSettings(String keptOld, String keptNew, String keptConfirm) {
        PhoneFallbackLayout.Settings layout = PhoneFallbackLayout.settings(inner, font.lineHeight);
        settingsLayout = layout;
        Map<String, Rect> rows = layout.rows();
        // « SIM : 06 12 34 56 78 » pour une ligne ; sinon l'état seul (« Aucune carte SIM », « SIM inconnue »…).
        Component simRow = PhoneStatusText.card(net().sim()) == PhoneStatusText.Shown.NUMBER
                ? Component.translatable(KEY + "sim", simShort()) : simShort();
        add("sim", new FitButton(rows.get("sim"), simRow, b -> {
            page = Page.SIM;
            message = Component.empty();
            requestsOpen = false;
            simScroll = 0;
            rebuild();
        }, null));
        add("airplane", new FitButton(rows.get("airplane"), Component.translatable(KEY + "airplane",
                Component.translatable(KEY + (state.airplane() ? "on" : "off"))), b -> send(Action.AIRPLANE, "", "",
                        !state.airplane()), null));
        add("wallpaper", new FitButton(rows.get("wallpaper"), Component.translatable(KEY + "wallpaper",
                Component.translatable(KEY + "wallpaper." + wallpaper())), b -> send(Action.WALLPAPER,
                        nextWallpaper(wallpaper()), "", false), null));
        oldBox = digits("old", rows.get("old"), keptOld);
        newBox = digits("new", rows.get("new"), keptNew);
        confirmBox = digits("confirm", rows.get("confirm"), keptConfirm);
        add("change", new FitButton(rows.get("change"), Component.translatable(KEY + "change_code"),
                b -> changeCode(), null));
        add("lock", new FitButton(rows.get("lock"), Component.translatable(KEY + "lock"),
                b -> send(Action.LOCK, "", "", false), null));
        add("erase", new FitButton(rows.get("erase"),
                Component.translatable(KEY + (eraseArmed ? "erase_confirm" : "erase")), b -> erase(), null));
        add("back", new FitButton(rows.get("back"), Component.translatable(KEY + "back"), b -> {
            page = Page.HOME;
            eraseArmed = false;
            message = Component.empty();
            rebuild();
        }, null));
    }

    /** La feuille « Carte SIM » : « Retirer », la liste qui défile (cartes, « Demander une carte », opérateurs), « Retour ». */
    private void buildSim() {
        PhoneNetView n = net();
        List<String> ids = new ArrayList<>();
        Map<String, Runnable> actions = new LinkedHashMap<>();
        Map<String, Component> labels = new LinkedHashMap<>();
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        for (PhoneSims.SimChoice c : n.choices()) {
            String id = "sim-choice-" + c.slot();
            ids.add(id);
            labels.put(id, Component.literal(c.number() + " · " + c.operator() + " · ")
                    .append(Component.translatable(KEY + "validity." + c.validity().name().toLowerCase(Locale.ROOT))));
            int slot = c.slot();
            actions.put(id, () -> send(Action.INSERT_SIM, String.valueOf(slot), "", false));
        }
        if (n.choices().isEmpty()) {
            ids.add("sim-none");
            labels.put("sim-none", Component.translatable(KEY + "no_choices"));
            enabled.put("sim-none", false);
        }
        ids.add("sim-request");
        labels.put("sim-request", Component.literal(requestsOpen ? "\u25be " : "\u25b8 ")
                .append(Component.translatable(KEY + "sim_request")));
        // Ni avec une carte active ni en mode avion : le serveur ne propose alors aucun opérateur (même choix que la page).
        enabled.put("sim-request", PhoneStatusText.requestOffered(n.sim(), state.airplane()));
        actions.put("sim-request", () -> {
            requestsOpen = !requestsOpen;
            rebuild();
        });
        if (requestsOpen && PhoneStatusText.requestOffered(n.sim(), state.airplane())) {
            for (PhoneNetView.OperatorChoice o : n.requestable()) {
                String id = "request-" + o.id();
                ids.add(id);
                labels.put(id, Component.translatable(KEY + "request_row", o.name(), o.bars()));
                String op = o.id();
                actions.put(id, () -> send(Action.REQUEST_SIM, op, "", false));
            }
            if (n.requestable().isEmpty()) {
                ids.add("request-none");
                labels.put("request-none", Component.translatable(KEY + "no_requestable"));
                enabled.put("request-none", false);
            }
        }
        PhoneFallbackLayout.SimSheet layout = PhoneFallbackLayout.simSheet(inner, font.lineHeight, ids, simScroll);
        simLayout = layout;
        simScroll = layout.scroll();
        FitButton remove = new FitButton(layout.remove(), Component.translatable(KEY + "sim_remove"),
                b -> send(Action.REMOVE_SIM, "", "", false), null);
        remove.active = n.sim() != PhoneSims.Validity.NONE;
        add("sim-remove", remove);
        for (Map.Entry<String, Rect> row : layout.rows().entrySet()) {
            String id = row.getKey();
            Runnable action = actions.get(id);
            // Une donnée libre (carte, opérateur) est coupée par « … » ; un texte fixe est rétréci par son bouton.
            Component label = PhoneFallbackLayout.cutWithEllipsis(id)
                    ? fitted(labels.get(id), row.getValue().width()) : labels.get(id);
            FitButton b = new FitButton(row.getValue(), label,
                    btn -> {
                        if (action != null) {
                            action.run();
                        }
                    }, labels.get(id));
            b.active = enabled.getOrDefault(id, true);
            // Libellé coupé (« 06 12 34 56 78… ») : la bulle donne le texte entier, opérateur et état compris.
            if (!label.getString().equals(labels.get(id).getString())) {
                b.setTooltip(Tooltip.create(labels.get(id)));
            }
            add(id, b);
        }
        add("back", new FitButton(layout.back(), Component.translatable(KEY + "back"), b -> {
            page = Page.SETTINGS;
            message = Component.empty();
            rebuild();
        }, null));
    }

    /** Un libellé coupé par « … » s'il ne tient pas dans un bouton de cette largeur (jamais rétréci illisible). */
    private Component fitted(Component text, int buttonWidth) {
        String s = text.getString();
        String cut = PhoneFallbackLayout.ellipsize(s, PhoneFallbackLayout.textRoom(buttonWidth), font::width);
        return cut.equals(s) ? text : Component.literal(cut);
    }

    /** Un champ de quatre chiffres, masqué ; son libellé est dessiné au-dessus de lui ({@link #drawSettings}). */
    private EditBox digits(String name, Rect r, String kept) {
        EditBox box = new CodeBox(font, r.x() + 1, r.y() + 1, r.width() - 2, r.height() - 2,
                Component.translatable(KEY + name + "_code"));
        box.setMaxLength(PhoneCode.LENGTH);
        box.setFilter(s -> s.chars().allMatch(c -> c >= '0' && c <= '9'));
        // Un code ne s'affiche jamais en clair : un astérisque par chiffre.
        box.setFormatter((text, start) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        box.setValue(kept);
        add(name, box);
        return box;
    }

    private void add(String name, AbstractWidget widget) {
        named.put(name, widget);
        addRenderableWidget(widget);
    }

    // --- Gestes ----------------------------------------------------------------------------------------------------

    private void digit(char d) {
        if (page != Page.CODE || lockedOut() || entry.length() >= PhoneCode.LENGTH) {
            return;
        }
        entry.append(d);
        if (entry.length() < PhoneCode.LENGTH) {
            return;
        }
        String code = entry.toString();
        entry.setLength(0);
        if (state.lock() == Lock.SETUP) {
            if (setupFirst == null) {
                setupFirst = code;
                message = Component.empty();
            } else if (setupFirst.equals(code)) {
                setupFirst = null;
                send(Action.SETUP, code, setupWallpaper, false);
            } else {
                setupFirst = null;
                alert(Component.translatable(KEY + "mismatch"));
            }
        } else if (state.lock() == Lock.LOCKED) {
            send(Action.UNLOCK, code, "", false);
        }
    }

    private void backspace() {
        if (entry.length() > 0) {
            entry.setLength(entry.length() - 1);
        }
    }

    private void changeCode() {
        String old = oldBox.getValue();
        String fresh = newBox.getValue();
        String confirm = confirmBox.getValue();
        if (!fresh.equals(confirm)) {
            confirmBox.setValue("");
            alert(Component.translatable(KEY + "mismatch"));
            return;
        }
        if (!PhoneCode.isValid(old) || !PhoneCode.isValid(fresh)) {
            alert(Component.translatable(KEY + "invalid_code"));
            return;
        }
        oldBox.setValue("");
        newBox.setValue("");
        confirmBox.setValue("");
        send(Action.CHANGE_CODE, old, fresh, false);
    }

    /** Premier clic : arme (le code actuel doit être saisi) ; second clic : envoie l'effacement. */
    private void erase() {
        String code = oldBox.getValue();
        if (!PhoneCode.isValid(code)) {
            eraseArmed = false;
            alert(Component.translatable(KEY + "erase_needs_code"));
            rebuild();
            return;
        }
        if (!eraseArmed) {
            eraseArmed = true;
            rebuild();
            return;
        }
        eraseArmed = false;
        oldBox.setValue("");
        send(Action.ERASE, code, "", false);
        rebuild();
    }

    private void send(Action action, String first, String second, boolean flag) {
        PhoneClient.send(new PhoneActionPacket(state.session(), action, first, second, flag));
    }

    private void alert(Component text) {
        message = text;
        messageAlert = true;
        messageFromCall = false;
    }

    /** Le message de l'issue du dernier geste ; {@code NONE} laisse le message en place. */
    private void result(PhoneStatePacket s) {
        Result r = s.last();
        switch (r) {
            case NONE -> {
            }
            case WRONG_CODE -> alert(Component.translatable(KEY + "wrong_code", s.attemptsLeft()));
            case LOCKED_OUT -> alert(Component.translatable(KEY + "locked_out"));
            case INVALID_CODE -> alert(Component.translatable(KEY + "invalid_code"));
            case CODE_CHANGED -> {
                message = Component.translatable(KEY + "code_changed");
                messageAlert = false;
            }
            case NOT_OWNER -> alert(Component.translatable(KEY + "not_owner"));
            case ERASED -> alert(Component.translatable(KEY + "erased"));
            // Lot T2 : les textes figés à la tâche 7 (même clé que le nom de l'issue).
            case SIM_INSERTED, SIM_REMOVED, SIM_REQUESTED -> {
                message = Component.translatable(KEY + r.name().toLowerCase(Locale.ROOT));
                messageAlert = false;
            }
            case SIM_UNKNOWN, INVENTORY_FULL, ALREADY_REQUESTED, NO_NETWORK, REQUESTS_FULL ->
                    alert(Component.translatable(KEY + r.name().toLowerCase(Locale.ROOT)));
            // Retours d'essai du 27/11, Face ID : le repli montre le déverrouillage par le visage et le visage couvert ;
            // l'interrupteur est dans les Réglages de la page seulement (même clé que le nom de l'issue).
            case FACE_UNLOCKED, FACE_ID_ON, FACE_ID_OFF -> {
                message = Component.translatable(KEY + r.name().toLowerCase(Locale.ROOT));
                messageAlert = false;
            }
            case FACE_NOT_RECOGNIZED -> alert(Component.translatable(KEY + "face_not_recognized"));
        }
    }

    /** La vue réseau montrée : réduite hors {@code UNLOCKED}, même si le paquet en portait plus. */
    private PhoneNetView net() {
        return state.lock() == Lock.UNLOCKED ? state.net() : state.net().locked();
    }

    /**
     * Ce que la barre d'état écrit à la place de l'opérateur : la règle de {@link PhoneStatusText}, la même que celle de
     * la page. Verrouillé, la carte n'y est que « aucune », « non reconnue » ou « présente », et 0 barre s'y lit « Pas de
     * service ».
     */
    private String carrier() {
        PhoneNetView n = net();
        PhoneStatusText.Shown shown = PhoneStatusText.carrier(n.sim(), state.lock() == Lock.UNLOCKED, state.airplane(),
                n.bars(), n.operator());
        return shown == PhoneStatusText.Shown.OPERATOR ? n.operator()
                : Component.translatable(shown.key()).getString();
    }

    /** Les barres se dessinent quand une ligne peut en avoir (carte active, hors mode avion), vides ou pleines. */
    private boolean barsShown() {
        return PhoneStatusText.barsShown(net().sim(), state.lock() == Lock.UNLOCKED, state.airplane());
    }

    /**
     * La carte logée en quelques mots (Réglages, tête de la feuille) : le numéro d'une ligne, sinon son état, lu dans la
     * vue (jamais déduit d'un numéro vide : une carte désactivée n'a pas de numéro sans être « non reconnue »).
     */
    private Component simShort() {
        PhoneNetView n = net();
        PhoneStatusText.Shown shown = PhoneStatusText.card(n.sim());
        if (shown == PhoneStatusText.Shown.NUMBER) {
            return Component.literal(n.number().isEmpty() ? "—" : n.number());
        }
        return Component.translatable(shown.key());
    }

    private void noteLockout() {
        lockoutEnd = state.lockoutSeconds() > 0 ? Util.getMillis() + state.lockoutSeconds() * 1000L : 0L;
    }

    private boolean lockedOut() {
        return Util.getMillis() < lockoutEnd;
    }

    @Override
    public void tick() {
        super.tick();
        if (calls.mode(Util.getMillis()) != shownCall) {
            rebuild(); // fin du message de fin d'appel : la page revient
            return;
        }
        if (page == Page.CODE && lockedOutShown && !lockedOut()) {
            message = Component.empty();
            rebuild(); // fin du blocage : le clavier revient
        }
    }

    /**
     * Un bouton retiré par {@link #rebuild} ne garde pas le focus : le clic qui reconstruit l'écran le lui remet après
     * {@code onPress}, et Espace ou Entrée l'actionnerait sous le nouvel écran ({@link PhoneFallbackFocus}).
     */
    private void dropStaleFocus() {
        if (getFocused() != null && PhoneFallbackFocus.kept(getFocused(), children()) == null) {
            setFocused(null);
        }
    }

    /**
     * Retours d'essai du 27/11 : les chiffres viennent de la touche ({@link PhoneKeys#key} : rangée du haut avec ou sans
     * Maj, clavier AZERTY compris, et pavé numérique), et non plus du caractère tapé, que la rangée du haut d'un clavier
     * AZERTY sans Maj ne donne pas ; {@link #charTyped} ne compte donc plus les chiffres, qui ne comptent qu'une fois.
     * Les touches « parler » et « chuchoter » du voice chat ne sont pas prises.
     */
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (PhoneWebScreen.voiceKey(minecraft, key, scan)) {
            return false;
        }
        dropStaleFocus();
        boolean noCall = shownCall == PhoneFallbackCall.Mode.NONE;
        String k = PhoneKeys.key(key, modifiers);
        if (noCall && page == Page.CODE && k != null && !PhoneKeys.ENTER.equals(k)) {
            if (PhoneKeys.BACK.equals(k)) {
                backspace();
            } else {
                digit(k.charAt(0));
            }
            return true;
        }
        if (noCall && page == Page.DIAL && k != null && !PhoneKeys.ENTER.equals(k)) {
            if (!PhoneKeys.BACK.equals(k)) {
                dialDigit(k.charAt(0));
            } else if (dialed.length() > 0) {
                dialed.setLength(dialed.length() - 1);
            }
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (PhoneWebScreen.voiceKey(minecraft, key, scan)) {
            return false;
        }
        return super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (page == Page.SIM && simLayout != null && simLayout.maxScroll() > 0) {
            int next = Math.max(0, Math.min(simLayout.maxScroll(),
                    simScroll - (int) Math.signum(delta) * simLayout.rowStep()));
            if (next != simScroll) {
                simScroll = next;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        dropStaleFocus();
        boolean noCall = shownCall == PhoneFallbackCall.Mode.NONE;
        if (noCall && (page == Page.CODE || page == Page.DIAL) && c >= '0' && c <= '9') {
            return true; // déjà compté par keyPressed
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        entry.setLength(0);
        setupFirst = null;
        if (!byServer && !toAntenna) {
            PhoneClient.sendClose(state.session());
        }
    }

    // --- Dessin ----------------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int[] colors = wallpaperColors(wallpaper());
        g.fillGradient(frame.x(), frame.y(), frame.x() + frame.width(), frame.y() + frame.height(), colors[0],
                colors[1]);
        // Le cadre par-dessus le fond : ses coins arrondis (verre transparent) arrondissent le fond.
        PhoneWebScreen.drawBezel(g, frame);
        drawStatus(g);
        if (shownCall != PhoneFallbackCall.Mode.NONE) {
            drawCall(g);
        } else {
            switch (page) {
                case CODE -> drawCode(g);
                case HOME -> drawHome(g);
                case SETTINGS -> drawSettings(g);
                case SIM -> drawSim(g);
                case DIAL -> drawDial(g);
                case SOS -> drawCentered(g, callText("sos"), inner.x() + inner.width() / 2,
                        PhoneFallbackLayout.sos(inner, font.lineHeight).titleY(), WHITE);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** L'écran d'appel : le titre, puis la ligne d'état (position d'une urgence, durée, message de fin). */
    private void drawCall(GuiGraphics g) {
        PhoneFallbackLayout.CallScreen layout = PhoneFallbackLayout.callScreen(inner, font.lineHeight);
        long now = Util.getMillis();
        int cx = inner.x() + inner.width() / 2;
        String title = text(calls.title(now)).getString();
        PhoneFallbackLayout.StatusLine t = PhoneFallbackLayout.statusLine("", title, inner.width(), font::width);
        drawScaled(g, t, cx - Math.round(font.width(t.text()) * t.scale()) / 2, layout.titleY(), WHITE);
        long serverNow = state.serverMillis() + (now - receivedAt);
        Component sub = text(calls.sub(now, serverNow));
        if (!sub.getString().isEmpty()) {
            drawWrapped(g, sub, cx, layout.subY(), shownCall == PhoneFallbackCall.Mode.ENDED ? RED : SOFT);
        }
    }

    /** L'appli « Téléphone » : le numéro composé (par paires, comme la page) et le message d'un appel refusé. */
    private void drawDial(GuiGraphics g) {
        PhoneFallbackLayout.Dial layout = PhoneFallbackLayout.dial(inner, font.lineHeight);
        int cx = inner.x() + inner.width() / 2;
        String shown = dialed.length() == 0 ? "" : PhoneFallbackCall.pairs(dialed.toString());
        if (!shown.isEmpty()) {
            PhoneFallbackLayout.StatusLine l = PhoneFallbackLayout.statusLine("", shown, inner.width(), font::width);
            drawScaled(g, l, cx - Math.round(font.width(l.text()) * l.scale()) / 2, layout.numberY(), WHITE);
        }
        if (!message.getString().isEmpty()) {
            PhoneFallbackLayout.StatusLine l = PhoneFallbackLayout.statusLine("", message.getString(), inner.width(),
                    font::width);
            drawScaled(g, l, cx - Math.round(font.width(l.text()) * l.scale()) / 2, layout.messageY(),
                    messageAlert ? RED : WHITE);
        }
    }

    private void drawStatus(GuiGraphics g) {
        int y = frame.y() + Math.max(3, Math.round(frame.width() * 0.06f)) - 1;
        int left = inner.x();
        int right = inner.x() + inner.width();
        String battery = state.battery() + "%";
        boolean airplane = state.lock() == Lock.UNLOCKED && state.airplane();
        Component status = airplane
                ? Component.literal("\u2708 " + battery)
                : Component.literal(battery);
        int statusX = right - font.width(status);
        g.drawString(font, status, statusX, y, state.battery() <= 20 ? RED : WHITE, true);
        // À gauche, l'heure et l'opérateur (ou l'état de la carte) ; verrouillé, l'opérateur seul ; puis les barres.
        // Jamais sous le plancher : l'heure cède d'abord sa place, puis des barres toutes vides ; un nom d'opérateur
        // trop long est enfin coupé par « … » (PhoneFallbackLayout.status).
        int room = PhoneFallbackLayout.statusRoom(inner.width(), font.width(status));
        boolean bars = barsShown();
        PhoneFallbackLayout.Status lead = PhoneFallbackLayout.status(
                state.lock() == Lock.UNLOCKED ? clock() + "  " : "", carrier(),
                bars ? PhoneFallbackLayout.BARS_WIDTH : 0, bars && net().bars() == 0, room, font::width);
        if (lead.bars()) {
            int filled = net().bars();
            int base = y + font.lineHeight - 2;
            for (int i = 0; i < 4; i++) {
                int bx = statusX - 3 - (4 - i) * 3;
                int h = 2 + 2 * i;
                g.fill(bx, base - h, bx + 2, base, i < filled ? WHITE : 0x55FFFFFF);
            }
        }
        drawScaled(g, lead.line(), left, y, state.lock() == Lock.UNLOCKED ? WHITE : SOFT);
    }

    private void drawCode(GuiGraphics g) {
        int cx = inner.x() + inner.width() / 2;
        PhoneFallbackLayout.CodeText text = PhoneFallbackLayout.codeText(inner, font.lineHeight,
                lockedOut() ? PhoneFallbackLayout.LOCKOUT_LINES : 1);
        int y = text.titleY();
        Component title = Component.translatable(KEY + (state.lock() == Lock.SETUP
                ? (setupFirst == null ? "choose" : "confirm")
                : "enter"));
        drawCentered(g, title, cx, y, WHITE);
        y = text.dotsY();
        int dot = text.dot();
        int step = dot * 3;
        int x0 = cx - (step * (PhoneCode.LENGTH - 1) + dot) / 2;
        for (int i = 0; i < PhoneCode.LENGTH; i++) {
            int x = x0 + i * step;
            if (i < entry.length()) {
                g.fill(x, y, x + dot, y + dot, WHITE);
            } else {
                g.renderOutline(x, y, dot, dot, WHITE);
            }
        }
        y = text.messageY();
        if (lockedOut()) {
            // Une urgence refusée pendant le blocage (avion, pas de réseau…) prend la ligne « bloqué » : le décompte
            // dessous dit encore le blocage.
            boolean refusal = messageFromCall && !message.getString().isEmpty();
            drawCentered(g, refusal ? message : Component.translatable(KEY + "locked_out"), cx, y, RED);
            y += text.lineStep();
            PhoneWaitText.Wait wait = PhoneWaitText.of((lockoutEnd - Util.getMillis() + 999) / 1000);
            String duration = Component.translatable(wait.form().key(), wait.arguments()).getString();
            String sentence = Component.translatable(KEY + "retry_in", duration).getString();
            drawLines(g, PhoneFallbackLayout.retryLines(sentence, duration, inner.width(), font::width), cx, y, WHITE);
        } else if (!message.getString().isEmpty()) {
            drawCentered(g, message, cx, y, messageAlert ? RED : WHITE);
        }
    }

    private void drawHome(GuiGraphics g) {
        int cx = inner.x() + inner.width() / 2;
        int y = inner.y() + 10;
        g.pose().pushPose();
        float big = Math.max(1f, Math.min(3f, inner.width() / 45f));
        g.pose().translate(cx, y, 0);
        g.pose().scale(big, big, 1f);
        String time = clock();
        g.drawString(font, time, -font.width(time) / 2, 0, WHITE, true);
        g.pose().popPose();
        y += Math.round(font.lineHeight * big) + 6;
        if (!state.ownerName().isEmpty()) {
            drawCentered(g, Component.translatable(KEY + "owner", state.ownerName()), cx, y, SOFT);
            y += font.lineHeight + 4;
        }
        if (!message.getString().isEmpty()) {
            drawWrapped(g, message, cx, y, messageAlert ? RED : WHITE);
        }
    }

    private void drawSettings(GuiGraphics g) {
        int cx = inner.x() + inner.width() / 2;
        int y = inner.y();
        Component owner = state.ownerName().isEmpty()
                ? Component.translatable(KEY + "settings")
                : Component.translatable(KEY + "owner", state.ownerName());
        drawCentered(g, owner, cx, y, SOFT);
        y += font.lineHeight + 1;
        if (!message.getString().isEmpty()) {
            drawCentered(g, message, cx, y, messageAlert ? RED : WHITE);
        }
        // Le libellé de chaque champ de code, au-dessus de lui : il reste lisible une fois le champ rempli.
        if (settingsLayout != null) {
            for (String name : PhoneFallbackLayout.LABELLED) {
                Integer top = settingsLayout.labelTops().get(name);
                if (top != null) {
                    drawLeft(g, Component.translatable(KEY + name + "_code"), inner.x() + 1, top, SOFT);
                }
            }
        }
    }

    /**
     * La feuille « Carte SIM » : quatre lignes au-dessus de « Retirer » : le numéro (ou l'état de la carte), le
     * titulaire (ou « SIM sans nom ») et l'opérateur ou l'état de la ligne, puis le message du dernier geste ou
     * l'abonnement, sur deux lignes au plus.
     */
    private void drawSim(GuiGraphics g) {
        PhoneNetView n = net();
        int cx = inner.x() + inner.width() / 2;
        int y = inner.y();
        // L'état vient de la vue, jamais d'un numéro vide : une ligne seulement pour une carte active ou suspendue.
        boolean line = PhoneStatusText.card(n.sim()) == PhoneStatusText.Shown.NUMBER;
        drawCentered(g, simShort(), cx, y, WHITE);
        y += font.lineHeight + 1;
        if (line) {
            String holder = n.holder().isEmpty()
                    ? Component.translatable("tooltip.oneforallsurvival.sim_card.anonymous").getString()
                    : n.holder();
            String lineState = n.sim() == PhoneSims.Validity.SUSPENDED
                    ? Component.translatable(PhoneStatusText.Shown.SUSPENDED.key()).getString()
                    : n.operator();
            PhoneFallbackLayout.StatusLine second = PhoneFallbackLayout.statusLine(holder + " · ", lineState,
                    inner.width(), font::width);
            int w = Math.round(font.width(second.text()) * second.scale());
            drawScaled(g, second, cx - w / 2, y, SOFT);
        }
        y += font.lineHeight + 1;
        // Le message du dernier geste, ou l'abonnement : à la ligne sur deux lignes au plus, jamais rétréci sous le plancher.
        if (!message.getString().isEmpty()) {
            drawWrapped(g, message, cx, y, messageAlert ? RED : WHITE);
        } else if (line) {
            drawWrapped(g, Component.translatable(KEY + "plan"), cx, y, SOFT);
        }
        // La liste défile : une barre fine et son curseur, comme dans le panneau de l'opérateur (revue de la tâche 12, I2).
        if (simLayout != null && simLayout.track() != null && simLayout.thumb() != null) {
            Rect t = simLayout.track();
            Rect c = simLayout.thumb();
            g.fill(t.x(), t.y(), t.x() + t.width(), t.y() + t.height(), dev.oneforallsurvival.bench.client.BenchTheme.WELL);
            g.fill(c.x(), c.y(), c.x() + c.width(), c.y() + c.height(), dev.oneforallsurvival.bench.client.BenchTheme.BRASS);
        }
    }

    /**
     * Un message centré, passé à la ligne entre les mots sur {@value PhoneFallbackLayout#MAX_TEXT_LINES} lignes au plus
     * ({@link PhoneFallbackLayout#wrap}) : à l'échelle 1, sinon au plancher, jamais plus petit.
     */
    private void drawWrapped(GuiGraphics g, Component text, int cx, int y, int color) {
        drawLines(g, PhoneFallbackLayout.wrap(text.getString(), inner.width(), font::width,
                PhoneFallbackLayout.MAX_TEXT_LINES), cx, y, color);
    }

    /** Les lignes d'un texte déjà passé à la ligne, centrées, à leur échelle, depuis {@code y}. */
    private void drawLines(GuiGraphics g, PhoneFallbackLayout.Wrapped w, int cx, int y, int color) {
        float s = w.scale();
        for (String line : w.lines()) {
            g.pose().pushPose();
            g.pose().translate(cx - font.width(line) * s / 2f, y, 0);
            g.pose().scale(s, s, 1f);
            g.drawString(font, line, 0, 0, color, true);
            g.pose().popPose();
            y += Math.round((font.lineHeight + 1) * s);
        }
    }

    /** Une ligne mise en page par {@link PhoneFallbackLayout#statusLine}, à son échelle, depuis {@code x}. */
    private void drawScaled(GuiGraphics g, PhoneFallbackLayout.StatusLine line, int x, int y, int color) {
        float s = line.scale();
        g.pose().pushPose();
        g.pose().translate(x, y + (1f - s) * font.lineHeight / 2f, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, line.text(), 0, 0, color, true);
        g.pose().popPose();
    }

    /** Un texte aligné à gauche, rétréci s'il dépasse la largeur de l'écran. */
    private void drawLeft(GuiGraphics g, Component text, int x, int y, int color) {
        float s = PhoneFallbackLayout.fit(font.width(text), inner.width() - 2, 1f);
        g.pose().pushPose();
        g.pose().translate(x, y + (1f - s) * font.lineHeight / 2f, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, 0, 0, color, true);
        g.pose().popPose();
    }

    /** Un texte centré, rétréci s'il dépasse la largeur de l'écran. */
    private void drawCentered(GuiGraphics g, Component text, int cx, int y, int color) {
        int w = font.width(text);
        int max = inner.width();
        if (w <= max) {
            g.drawString(font, text, cx - w / 2, y, color, true);
            return;
        }
        float s = max / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - max / 2f, y, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, 0, 0, color, true);
        g.pose().popPose();
    }

    private String clock() {
        long millis = state.serverMillis() + state.zoneOffsetSeconds() * 1000L + (Util.getMillis() - receivedAt);
        ZonedDateTime t = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC);
        return String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
    }

    /** Le fond montré : au choix du code, celui que le joueur est en train de choisir ; sinon celui du serveur. */
    private String wallpaper() {
        return state.lock() == Lock.SETUP ? setupWallpaper : shownWallpaper(state.wallpaper());
    }

    private static String shownWallpaper(String id) {
        return PhoneWallpapers.isValid(id) ? id : PhoneWallpapers.DEFAULT;
    }

    static String nextWallpaper(String current) {
        List<String> ids = PhoneWallpapers.IDS;
        int i = ids.indexOf(current);
        return ids.get((i + 1) % ids.size());
    }

    /** Haut et bas du dégradé de chaque fond (couleurs proches des fonds de la page). */
    private static int[] wallpaperColors(String id) {
        return switch (id) {
            case "lagon" -> new int[] {0xFF2BB8C4, 0xFF0B3F6E};
            case "jungle" -> new int[] {0xFF4C9A3E, 0xFF0E3522};
            case "nuit" -> new int[] {0xFF1D2648, 0xFF04060E};
            default -> new int[] {0xFFF2A26E, 0xFF5E4596};
        };
    }

    /**
     * Un bouton dont le texte, trop large, est rétréci pour tenir (au lieu du défilement de Minecraft), jamais sous le
     * plancher {@link PhoneFallbackLayout#MIN_TEXT_SCALE} : au-delà, coupé par « … ».
     * {@code spoken} remplace le texte lu par le narrateur (la flèche d'Effacer se lit « Effacer »).
     */
    private static final class FitButton extends Button {

        /** Dessiné comme un bouton inactif (grisé) tout en restant touchable. */
        boolean dimmed;
        /** Un interrupteur en service (Silence, Haut-parleur) : entouré d'un trait clair. */
        boolean lit;

        FitButton(Rect r, Component text, OnPress onPress, @Nullable Component spoken) {
            super(r.x(), r.y(), r.width(), r.height(), text, onPress,
                    spoken == null ? DEFAULT_NARRATION : narration -> Component.translatable("gui.narrate.button", spoken));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (!dimmed) {
                super.renderWidget(g, mouseX, mouseY, partialTick);
                if (lit) {
                    g.renderOutline(getX(), getY(), getWidth(), getHeight(), WHITE);
                }
                return;
            }
            // L'apparence d'un bouton inactif (fond et texte gris du jeu), le temps du dessin seulement.
            boolean was = active;
            active = false;
            super.renderWidget(g, mouseX, mouseY, partialTick);
            active = was;
        }

        @Override
        public void renderString(GuiGraphics g, Font font, int color) {
            int room = PhoneFallbackLayout.textRoom(getWidth());
            if (font.width(getMessage()) <= room) {
                super.renderString(g, font, color);
                return;
            }
            // Rétréci jusqu'au plancher (0,75), puis coupé par « … » : jamais illisible.
            PhoneFallbackLayout.StatusLine l = PhoneFallbackLayout.button(getMessage().getString(), getWidth(),
                    font::width);
            // Coupé au rendu (« ▾ Demander une car… ») : la bulle donne le texte entier, s'il n'en a pas déjà une
            // (relecture de la tâche 12).
            if (l.cut() && getTooltip() == null) {
                setTooltip(Tooltip.create(getMessage()));
            }
            float s = l.scale();
            int w = font.width(l.text());
            g.pose().pushPose();
            g.pose().translate(getX() + getWidth() / 2f, getY() + (getHeight() - font.lineHeight * s) / 2f + s, 0);
            g.pose().scale(s, s, 1f);
            g.drawString(font, l.text(), -w / 2, 0, color, true);
            g.pose().popPose();
        }
    }

    /**
     * Un champ de code : le narrateur n'en lit que le libellé, jamais les chiffres saisis. Retours d'essai du 27/11 : ses
     * chiffres viennent de la touche ({@link PhoneKeys#key}), pas du caractère, comme le clavier de l'écran.
     */
    private static final class CodeBox extends EditBox {

        CodeBox(Font font, int x, int y, int w, int h, Component label) {
            super(font, x, y, w, h, label);
        }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers) {
            String k = PhoneKeys.key(key, modifiers);
            if (canConsumeInput() && k != null && k.length() == 1) {
                insertText(k);
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }

        @Override
        public boolean charTyped(char c, int modifiers) {
            if (canConsumeInput() && c >= '0' && c <= '9') {
                return true; // déjà compté par keyPressed
            }
            return super.charTyped(c, modifiers);
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return getMessage().copy();
        }
    }
}
