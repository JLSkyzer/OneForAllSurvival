package dev.oneforallsurvival.phone.client;

import dev.oneforallsurvival.client.web.WebScreen.Rect;
import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneActionPacket.Action;
import dev.oneforallsurvival.phone.PhoneCode;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import dev.oneforallsurvival.phone.PhoneWallpapers;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * Le téléphone en interface Minecraft classique, quand le navigateur du jeu ne sert pas : même cadre que la page, mêmes
 * gestes ({@link PhoneActionPacket}), mêmes états. Écran du code (clavier 0 à 9, Effacer, points), accueil (Réglages),
 * Réglages (mode avion, fond suivant, changement du code par trois champs de chiffres, verrouillage, effacement). Le
 * serveur juge tout ; l'écran ne garde un code saisi que le temps de l'envoyer et ne l'écrit nulle part. Échap ferme et
 * prévient le serveur.
 */
public final class PhoneFallbackScreen extends Screen implements PhoneClient.Shown {

    private static final String KEY = "screen.oneforallsurvival.phone.";
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SOFT = 0xFFD8DCE4;
    private static final int RED = 0xFFFF6B6B;

    enum Page { CODE, HOME, SETTINGS }

    private PhoneStatePacket state;
    private long receivedAt = Util.getMillis();
    private long lockoutEnd;
    private Page page;
    private final StringBuilder entry = new StringBuilder();
    @Nullable private String setupFirst;
    private Component message = Component.empty();
    private boolean messageAlert;
    private boolean eraseArmed;
    private boolean byServer;
    private Rect frame = new Rect(0, 0, 0, 0);
    private Rect inner = new Rect(0, 0, 0, 0);
    private final Map<String, AbstractWidget> named = new LinkedHashMap<>();
    private EditBox oldBox;
    private EditBox newBox;
    private EditBox confirmBox;
    private boolean lockedOutShown;

    public PhoneFallbackScreen(PhoneStatePacket state) {
        super(Component.translatable("item.oneforallsurvival.phone"));
        this.state = state;
        this.page = state.lock() == Lock.UNLOCKED ? Page.HOME : Page.CODE;
        noteLockout();
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
                || next.last() != Result.NONE;
        state = next;
        receivedAt = Util.getMillis();
        noteLockout();
        if (next.lock() != before) {
            entry.setLength(0);
            setupFirst = null;
            eraseArmed = false;
            message = Component.empty();
            page = next.lock() == Lock.UNLOCKED ? Page.HOME : Page.CODE;
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
     * Les commandes de l'écran par nom : {@code key-0} à {@code key-9}, {@code key-clear} ; {@code settings} ;
     * {@code airplane}, {@code wallpaper}, {@code old}, {@code new}, {@code confirm}, {@code change}, {@code lock},
     * {@code erase}, {@code back}.
     */
    public Map<String, AbstractWidget> widgets() {
        return Collections.unmodifiableMap(named);
    }

    /** Le cadre de l'écran du téléphone, en coordonnées d'interface. */
    public Rect frameRect() {
        return frame;
    }

    // --- Screen ----------------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        frame = PhoneFrameLayout.fit(width, height);
        int pad = Math.max(3, Math.round(frame.width() * 0.06f));
        inner = new Rect(frame.x() + pad, frame.y() + pad + statusHeight(), frame.width() - 2 * pad,
                frame.height() - 2 * pad - statusHeight());
        rebuild();
    }

    private int statusHeight() {
        return font.lineHeight + 4;
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
        lockedOutShown = false;
        switch (page) {
            case CODE -> buildKeypad();
            case HOME -> buildHome();
            case SETTINGS -> buildSettings(keptOld, keptNew, keptConfirm);
        }
    }

    /** Le clavier : trois colonnes 1-9, puis Effacer et 0, dans le bas de l'écran. */
    private void buildKeypad() {
        int gap = 3;
        int bw = (inner.width() - 2 * gap) / 3;
        int bh = Math.max(10, Math.min(20, (inner.height() / 2 - 3 * gap) / 4));
        int top = inner.y() + inner.height() - 4 * bh - 3 * gap;
        boolean active = !lockedOut();
        for (int d = 1; d <= 9; d++) {
            int col = (d - 1) % 3;
            int row = (d - 1) / 3;
            key(String.valueOf(d), inner.x() + col * (bw + gap), top + row * (bh + gap), bw, bh, active);
        }
        int lastRow = top + 3 * (bh + gap);
        Button clear = Button.builder(Component.translatable(KEY + "clear"), b -> backspace())
                .bounds(inner.x(), lastRow, bw, bh).build();
        clear.active = active;
        add("key-clear", clear);
        key("0", inner.x() + bw + gap, lastRow, bw, bh, active);
        lockedOutShown = !active;
    }

    private void key(String digit, int x, int y, int w, int h, boolean active) {
        Button b = Button.builder(Component.literal(digit), btn -> digit(digit.charAt(0))).bounds(x, y, w, h).build();
        b.active = active;
        add("key-" + digit, b);
    }

    private void buildHome() {
        int bh = 20;
        add("settings", Button.builder(Component.translatable(KEY + "settings"), b -> {
            page = Page.SETTINGS;
            message = Component.empty();
            rebuild();
        }).bounds(inner.x(), inner.y() + inner.height() - bh - 4, inner.width(), bh).build());
    }

    private void buildSettings(String keptOld, String keptNew, String keptConfirm) {
        int rows = 10;
        int gap = 2;
        int text = 2 * (font.lineHeight + 1); // ligne du propriétaire et ligne du message
        int rh = Math.max(10, Math.min(20, (inner.height() - text - (rows - 1) * gap) / rows));
        int x = inner.x();
        int w = inner.width();
        int y = inner.y() + text;
        add("airplane", Button.builder(Component.translatable(KEY + "airplane",
                Component.translatable(KEY + (state.airplane() ? "on" : "off"))), b -> send(Action.AIRPLANE, "", "",
                        !state.airplane())).bounds(x, y, w, rh).build());
        y += rh + gap;
        add("wallpaper", Button.builder(Component.translatable(KEY + "wallpaper",
                Component.translatable(KEY + "wallpaper." + wallpaper())), b -> send(Action.WALLPAPER,
                        nextWallpaper(wallpaper()), "", false)).bounds(x, y, w, rh).build());
        y += rh + gap;
        oldBox = digits("old", x, y, w, rh, keptOld);
        y += rh + gap;
        newBox = digits("new", x, y, w, rh, keptNew);
        y += rh + gap;
        confirmBox = digits("confirm", x, y, w, rh, keptConfirm);
        y += rh + gap;
        add("change", Button.builder(Component.translatable(KEY + "change_code"), b -> changeCode())
                .bounds(x, y, w, rh).build());
        y += rh + gap;
        add("lock", Button.builder(Component.translatable(KEY + "lock"), b -> send(Action.LOCK, "", "", false))
                .bounds(x, y, w, rh).build());
        y += rh + gap;
        add("erase", Button.builder(Component.translatable(KEY + (eraseArmed ? "erase_confirm" : "erase")),
                b -> erase()).bounds(x, y, w, rh).build());
        y += rh + gap;
        add("back", Button.builder(Component.translatable(KEY + "back"), b -> {
            page = Page.HOME;
            eraseArmed = false;
            message = Component.empty();
            rebuild();
        }).bounds(x, y, w, rh).build());
    }

    private EditBox digits(String name, int x, int y, int w, int h, String kept) {
        EditBox box = new EditBox(font, x + 1, y + 1, w - 2, h - 2, Component.translatable(KEY + name + "_code"));
        box.setMaxLength(PhoneCode.LENGTH);
        box.setFilter(s -> s.chars().allMatch(c -> c >= '0' && c <= '9'));
        box.setHint(Component.translatable(KEY + name + "_code"));
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
                send(Action.SETUP, code, "", false);
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
        }
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
        if (page == Page.CODE && lockedOutShown && !lockedOut()) {
            message = Component.empty();
            rebuild(); // fin du blocage : le clavier revient
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (page == Page.CODE && key == GLFW.GLFW_KEY_BACKSPACE) {
            backspace();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (page == Page.CODE && c >= '0' && c <= '9') {
            digit(c);
            return true;
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
        if (!byServer) {
            PhoneClient.sendClose(state.session());
        }
    }

    // --- Dessin ----------------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        PhoneWebScreen.drawBezel(g, frame);
        int[] colors = wallpaperColors(wallpaper());
        g.fillGradient(frame.x(), frame.y(), frame.x() + frame.width(), frame.y() + frame.height(), colors[0],
                colors[1]);
        drawStatus(g);
        switch (page) {
            case CODE -> drawCode(g);
            case HOME -> drawHome(g);
            case SETTINGS -> drawSettings(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawStatus(GuiGraphics g) {
        int y = frame.y() + Math.max(3, Math.round(frame.width() * 0.06f)) - 1;
        int left = inner.x();
        int right = inner.x() + inner.width();
        if (state.lock() == Lock.UNLOCKED) {
            g.drawString(font, clock(), left, y, WHITE, true);
        } else {
            g.drawString(font, Component.translatable(KEY + "no_service"), left, y, SOFT, true);
        }
        String battery = state.battery() + "%";
        Component status = state.airplane()
                ? Component.literal("✈ " + battery)
                : Component.literal(battery);
        g.drawString(font, status, right - font.width(status), y, state.battery() <= 20 ? RED : WHITE, true);
    }

    private void drawCode(GuiGraphics g) {
        int cx = inner.x() + inner.width() / 2;
        int y = inner.y() + 6;
        Component title = Component.translatable(KEY + (state.lock() == Lock.SETUP
                ? (setupFirst == null ? "choose" : "confirm")
                : "enter"));
        drawCentered(g, title, cx, y, WHITE);
        y += font.lineHeight + 6;
        int dot = Math.max(3, Math.min(6, inner.width() / 20));
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
        y += dot + 6;
        if (lockedOut()) {
            drawCentered(g, Component.translatable(KEY + "locked_out"), cx, y, RED);
            y += font.lineHeight + 2;
            long left = (lockoutEnd - Util.getMillis() + 999) / 1000;
            drawCentered(g, Component.translatable(KEY + "retry_in", left), cx, y, WHITE);
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
            drawCentered(g, message, cx, y, messageAlert ? RED : WHITE);
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

    private String wallpaper() {
        return PhoneWallpapers.isValid(state.wallpaper()) ? state.wallpaper() : PhoneWallpapers.DEFAULT;
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
}
