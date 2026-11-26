package dev.oneforallsurvival.phone.net.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import dev.oneforallsurvival.phone.net.OperatorActionPacket;
import dev.oneforallsurvival.phone.net.OperatorActionPacket.Action;
import dev.oneforallsurvival.phone.net.OperatorNoticePacket;
import dev.oneforallsurvival.phone.net.OperatorView;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Button;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Layout;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Pane;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Rect;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Row;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Tab;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Text;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Le panneau de l'opérateur dans Minecraft (lot T2, tâche 9), ouvert à la réception d'une vue de source {@code BLOCK},
 * ou depuis l'écran de repli du téléphone (source {@code PHONE}, tâche 10 : {@code PhoneClient} l'ouvre par-dessus le
 * repli, et Échap y revient, sur son dernier état).
 * Il n'a <b>aucune règle</b> : il montre la vue reçue ({@link OperatorView}, mise en page par
 * {@link OperatorPanelLayout}), n'affiche un bouton que si les droits reçus le permettent, et envoie chaque geste tel
 * quel ({@link OperatorActionPacket}) ; le serveur rejoue tout ({@code OperatorActions.apply}) et renvoie la vue.
 * Échap ferme le panneau (et le dit au serveur). La ligne d'état montre le dernier retour du serveur
 * ({@link OperatorNoticePacket}) ; une fermeture par le serveur ferme l'écran sans rien renvoyer.
 *
 * <p>« Désactiver » (tâche 15) est irréversible : le premier appui ne part pas, le bouton dit « Confirmer ? » ; seul un
 * second appui sur la même carte dans les {@link OperatorPanelLayout#CONFIRM_MS} millisecondes envoie le geste. Cette
 * attente n'est pas une règle (le serveur juge le geste confirmé comme tout autre) : elle évite une fausse manœuvre.
 */
public final class OperatorPanelScreen extends Screen {

    private static final int TOOLTIP_W = 200;
    /** Le dernier numéro de panneau vu : une vue d'un panneau déjà fermé ne le rouvre pas. */
    private static int lastSession = -1;

    private OperatorView view;
    private Tab tab = Tab.NETWORK;
    @Nullable
    private Layout layout;
    private int layoutWidth = -1;
    private int layoutHeight = -1;
    @Nullable
    private OperatorView layoutView;
    private final int[] scroll = new int[Tab.values().length];
    /** Le premier appui sur « Désactiver » d'une carte, en attente du second ; nul sinon. */
    @Nullable
    private OperatorPanelLayout.Armed armed;
    /** La carte armée de la mise en page en cache (vide : aucune). */
    private String layoutArmed = "";
    @Nullable
    private EditBox nameBox;
    @Nullable
    private EditBox tariffBox;

    /** L'écran de repli du téléphone sous le panneau (source {@code PHONE}), rendu à la fermeture ; nul sinon. */
    @Nullable
    private final Screen back;
    /** Le dernier retour du serveur, sur la ligne d'état. */
    private String status = "";
    private boolean closedByServer;
    /** La sortie passe par {@link #leave} (Échap, ou fermeture par le serveur) : le serveur est déjà prévenu. */
    private boolean leaving;

    public OperatorPanelScreen(OperatorView view) {
        this(view, null);
    }

    public OperatorPanelScreen(OperatorView view, @Nullable Screen back) {
        super(Component.translatable(OperatorPanelLayout.KEY + "title"));
        this.view = view;
        this.back = back;
    }

    /** L'écran rendu à la fermeture (le repli du téléphone), ou nul. */
    @Nullable
    public Screen back() {
        return back;
    }

    /** L'onglet montré (lu par un automate). */
    public Tab tab() {
        return tab;
    }

    /**
     * Lu par un automate : le centre (coordonnées d'interface) de l'onglet {@code t}, ou du bouton visible de l'onglet
     * montré qui envoie {@code action} ({@code arg} vide : le premier), ou du champ du tarif ({@code action} nul) ;
     * {@code null} s'il n'est pas à l'écran.
     */
    @Nullable
    public double[] centreOf(@Nullable Tab t, @Nullable Action action, String arg) {
        Layout l = currentLayout();
        if (t != null) {
            if (l.tabs().size() <= t.ordinal()) {
                return null;
            }
            Rect r = l.tabs().get(t.ordinal());
            return new double[] {r.x() + r.w() / 2.0, r.y() + r.h() / 2.0};
        }
        if (l.panes().isEmpty()) {
            return null;
        }
        Pane pane = l.panes().get(tab.ordinal());
        int dy = -scroll[tab.ordinal()];
        for (Row row : pane.rows()) {
            if (action == null && row.field() != null) {
                Rect r = row.field().shifted(dy);
                return pane.list().inside(r.x() + 2, r.y() + r.h() / 2.0) ? new double[] {r.x() + 6, r.y() + r.h() / 2.0}
                        : null;
            }
            for (Button b : row.buttons()) {
                if (b.action() == action && (arg.isEmpty() || arg.equals(b.arg()))) {
                    Rect r = b.box().shifted(dy);
                    double[] c = {r.x() + r.w() / 2.0, r.y() + r.h() / 2.0};
                    return pane.list().inside(c[0], c[1]) ? c : null;
                }
            }
        }
        return null;
    }

    /** Le texte de la ligne d'état (lu par un automate). */
    public String status() {
        return status;
    }

    /**
     * Une vue reçue du serveur, sur le fil du client. Source {@code BLOCK} : met à jour le panneau ouvert de ce numéro, ou
     * ouvre le panneau d'un numéro nouveau s'il n'y a pas d'autre écran (la vue d'un panneau qu'on vient de fermer ne le
     * rouvre pas). Source {@code PHONE} : au téléphone ({@code PhoneClient.acceptAntenna} : la page, ou ce panneau
     * par-dessus l'écran de repli).
     */
    public static void accept(OperatorView view) {
        if (view.source() != OperatorView.Source.BLOCK) {
            dev.oneforallsurvival.phone.client.PhoneClient.acceptAntenna(view);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof OperatorPanelScreen open && open.view.session() == view.session()) {
            open.update(view);
        } else if (view.session() != lastSession && mc.screen == null) {
            mc.setScreen(new OperatorPanelScreen(view));
        }
        lastSession = view.session();
    }

    /**
     * Un mot du serveur pour le panneau de ce numéro : le retour d'un geste sur la ligne d'état, ou la fermeture (l'écran
     * se ferme sans renvoyer {@code CLOSE}). Pour un autre écran, l'appli « Antenne » de la page le reçoit.
     */
    public static void notice(OperatorNoticePacket notice) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof OperatorPanelScreen open && open.view.session() == notice.session()) {
            if (notice.closed()) {
                open.closedByServer = true;
                open.leave();
            } else {
                open.status = notice.message().getString();
                open.layout = null;
            }
            return;
        }
        dev.oneforallsurvival.phone.client.PhoneClient.antennaNotice(notice);
    }

    /** Une vue de ce même panneau (source {@code PHONE}, routée par {@code PhoneClient}). */
    public void show(OperatorView next) {
        if (next.session() == view.session()) {
            update(next);
        }
    }

    private void update(OperatorView next) {
        boolean founded = !view.operatorId().isEmpty();
        boolean tariff = view.rights().tariff();
        this.view = next;
        if (founded != !next.operatorId().isEmpty() || tariff != next.rights().tariff()) {
            rebuildWidgets();
        }
    }

    public OperatorView view() {
        return view;
    }

    // ---------------------------------------------------------------------------------------------
    // Champs et mise en page
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        Layout l = currentLayout();
        String keptName = nameBox == null ? "" : nameBox.getValue();
        String keptTariff = tariffBox == null ? "" : tariffBox.getValue();
        nameBox = null;
        tariffBox = null;
        if (l.name() != null) {
            nameBox = box(l.name().field(), PowerSuppliers.MAX_NAME);
            nameBox.setFilter(t -> t.chars().noneMatch(c -> Character.isISOControl(c) || c == '§'));
            nameBox.setValue(keptName);
            setInitialFocus(nameBox);
        }
        Row field = tariffRow(l);
        if (field != null) {
            tariffBox = box(field.field(), 7);
            tariffBox.setFilter(t -> t.chars().allMatch(c -> c >= '0' && c <= '9'));
            tariffBox.setValue(keptTariff);
        }
        placeTariffBox();
    }

    private EditBox box(Rect r, int maxLength) {
        EditBox box = new EditBox(font, r.x() + 4, r.y() + (r.h() - 8) / 2, r.w() - 8, 9, Component.empty());
        box.setBordered(false);
        box.setTextColor(BenchTheme.TEXT);
        box.setMaxLength(maxLength);
        addRenderableWidget(box);
        return box;
    }

    @Nullable
    private static Row tariffRow(Layout l) {
        if (l.panes().isEmpty()) {
            return null;
        }
        for (Row r : l.panes().get(Tab.TARIFF.ordinal()).rows()) {
            if (r.field() != null) {
                return r;
            }
        }
        return null;
    }

    /** Le champ du tarif suit le défilement et ne se voit que sur son onglet. */
    private void placeTariffBox() {
        Row field = tariffRow(currentLayout());
        if (tariffBox != null && field != null) {
            Rect r = field.field().shifted(-scroll[Tab.TARIFF.ordinal()]);
            Rect list = currentLayout().panes().get(Tab.TARIFF.ordinal()).list();
            tariffBox.setX(r.x() + 4);
            tariffBox.setY(r.y() + (r.h() - 8) / 2);
            tariffBox.visible = tab == Tab.TARIFF && r.y() >= list.y() && r.y() + r.h() <= list.y() + list.h();
        }
    }

    /** La carte dont « Désactiver » attend encore son second appui, ou vide (l'attente s'éteint d'elle-même). */
    private String armedArg() {
        if (armed != null && !armed.live(Util.getMillis())) {
            armed = null;
        }
        return armed == null ? "" : armed.arg();
    }

    private Layout currentLayout() {
        String a = armedArg();
        if (layout == null || layoutWidth != width || layoutHeight != height || layoutView != view
                || !a.equals(layoutArmed)) {
            layout = OperatorPanelLayout.of(width, height, font::width, view, TEXTS, status, a);
            layoutWidth = width;
            layoutHeight = height;
            layoutView = view;
            layoutArmed = a;
            for (int i = 0; i < scroll.length && i < layout.panes().size(); i++) {
                scroll[i] = Math.max(0, Math.min(scroll[i], layout.panes().get(i).scrollMax()));
            }
        }
        return layout;
    }

    /** Les textes du jeu : la langue du joueur, les montants en coupures. */
    private static final OperatorPanelLayout.Texts TEXTS = new OperatorPanelLayout.Texts() {
        @Override
        public String t(String key, Object... args) {
            return I18n.get(key, args);
        }

        @Override
        public String coins(long amount) {
            return CoinMath.format(amount).getString();
        }

        @Override
        public String number(long value) {
            String digits = Long.toString(Math.abs(value));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < digits.length(); i++) {
                if (i > 0 && (digits.length() - i) % 3 == 0) {
                    out.append(' ');
                }
                out.append(digits.charAt(i));
            }
            return (value < 0 ? "-" : "") + out;
        }
    };

    // ---------------------------------------------------------------------------------------------
    // Rendu
    // ---------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        // Le panneau du téléphone (pos = 0, 0, 0) ne se ferme pas à la distance (revue de la tâche 9).
        if (minecraft != null && minecraft.player != null && OperatorPanelLayout.closesAtDistance(view,
                minecraft.player.distanceToSqr(Vec3.atCenterOf(view.pos())))) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        Layout l = currentLayout();
        placeTariffBox();
        Rect f = l.frame();
        BenchTheme.plate(g, f.x(), f.y(), f.w(), f.h());
        BenchTheme.rivets(g, f.x(), f.y(), f.w(), f.h());
        draw(g, l.title(), BenchTheme.BRASS_LIGHT, 0);
        if (l.name() != null) {
            draw(g, l.name().label(), BenchTheme.TEXT, 0);
            Rect nf = l.name().field();
            BenchTheme.well(g, nf.x(), nf.y(), nf.w(), nf.h());
            Rect b = l.name().button();
            boolean ok = nameBox != null && PowerSuppliers.validName(nameBox.getValue()).isPresent();
            BenchTheme.button(g, font, b.x(), b.y(), b.w(), b.h(), "", ok && b.inside(mx, my), ok, true);
            draw(g, l.name().buttonLabel(), ok ? BenchTheme.TEXT_ON_BRASS : BenchTheme.TEXT_DISABLED, 0);
            for (Text t : l.status()) {
                draw(g, t, BenchTheme.BRASS_LIGHT, 0);
            }
            super.render(g, mx, my, partialTick);
            return;
        }
        for (int i = 0; i < l.tabs().size(); i++) {
            Rect r = l.tabs().get(i);
            boolean on = i == tab.ordinal();
            BenchTheme.button(g, font, r.x(), r.y(), r.w(), r.h(), "", !on && r.inside(mx, my), true, on);
            draw(g, l.tabLabels().get(i), on ? BenchTheme.TEXT_ON_BRASS : BenchTheme.TEXT, 0);
        }
        Pane pane = l.panes().get(tab.ordinal());
        int dy = -scroll[tab.ordinal()];
        Rect list = pane.list();
        g.enableScissor(list.x(), list.y(), list.x() + list.w(), list.y() + list.h());
        boolean inList = list.inside(mx, my);
        for (Row row : pane.rows()) {
            Rect box = row.box().shifted(dy);
            if (box.y() + box.h() < list.y() || box.y() > list.y() + list.h()) {
                continue;
            }
            int color = row.alert() ? BenchTheme.TEXT_BAD
                    : row.id().endsWith("-header") ? BenchTheme.BRASS_LIGHT : BenchTheme.TEXT;
            for (Text t : row.texts()) {
                draw(g, t, color, dy);
            }
            if (row.field() != null) {
                Rect fr = row.field().shifted(dy);
                BenchTheme.well(g, fr.x(), fr.y(), fr.w(), fr.h());
            }
            for (Button b : row.buttons()) {
                Rect r = b.box().shifted(dy);
                boolean confirming = b.action() == Action.DEACTIVATE && b.arg().equals(layoutArmed);
                boolean lit = b.action() == Action.ISSUE || confirming;
                BenchTheme.button(g, font, r.x(), r.y(), r.w(), r.h(), "", inList && r.inside(mx, my), true, lit);
                int labelColor = lit ? BenchTheme.TEXT_ON_BRASS
                        : b.action() == Action.DEACTIVATE ? BenchTheme.TEXT_BAD : BenchTheme.TEXT;
                draw(g, b.label(), labelColor, dy);
            }
        }
        g.disableScissor();
        if (pane.scrollMax() > 0) {
            int trackH = list.h();
            int thumbH = Math.max(12, trackH * trackH / (trackH + pane.scrollMax()));
            int thumbY = list.y() + (trackH - thumbH) * scroll[tab.ordinal()] / pane.scrollMax();
            int x = list.x() + list.w() - 3;
            g.fill(x, list.y(), x + 2, list.y() + trackH, BenchTheme.WELL);
            g.fill(x, thumbY, x + 2, thumbY + thumbH, BenchTheme.BRASS);
        }
        for (Text t : l.status()) {
            draw(g, t, BenchTheme.BRASS_LIGHT, 0);
        }
        super.render(g, mx, my, partialTick);
        renderTooltip(g, l, pane, dy, mx, my);
    }

    /** Un texte à son échelle (jamais sous 0,75, voir la mise en page). */
    private void draw(GuiGraphics g, @Nullable Text t, int color, int dy) {
        if (t == null || t.shown().isEmpty()) {
            return;
        }
        if (t.scale() >= 0.999f) {
            g.drawString(font, t.shown(), t.x(), t.y() + dy, color, false);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(t.x(), t.y() + dy + (1f - t.scale()) * 4f, 0f);
        g.pose().scale(t.scale(), t.scale(), 1f);
        g.drawString(font, t.shown(), 0, 0, color, false);
        g.pose().popPose();
    }

    /** La bulle d'un texte coupé : le texte entier. */
    private void renderTooltip(GuiGraphics g, Layout l, Pane pane, int dy, int mx, int my) {
        if (l.title().cut() && l.title().area().inside(mx, my)) {
            g.renderTooltip(font, font.split(Component.literal(l.title().full()), TOOLTIP_W), mx, my);
            return;
        }
        if (!pane.list().inside(mx, my)) {
            return;
        }
        for (Row row : pane.rows()) {
            for (Text t : row.texts()) {
                if (t.cut() && t.area().shifted(dy).inside(mx, my)) {
                    g.renderTooltip(font, font.split(Component.literal(t.full()), TOOLTIP_W), mx, my);
                    return;
                }
            }
            for (Button b : row.buttons()) {
                if (b.label().cut() && b.box().shifted(dy).inside(mx, my)) {
                    g.renderTooltip(font, font.split(Component.literal(b.label().full()), TOOLTIP_W), mx, my);
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Gestes : envoyés tels quels, le serveur juge
    // ---------------------------------------------------------------------------------------------

    private void send(Action action, String arg, long value) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        BenchNetwork.CHANNEL.sendToServer(new OperatorActionPacket(view.session(), action, arg, value));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        Layout l = currentLayout();
        if (l.name() != null && l.name().button().inside(mx, my)) {
            submitName();
            return true;
        }
        for (int i = 0; i < l.tabs().size(); i++) {
            if (l.tabs().get(i).inside(mx, my)) {
                tab = Tab.values()[i];
                armed = null;
                placeTariffBox();
                return true;
            }
        }
        if (!l.panes().isEmpty()) {
            Pane pane = l.panes().get(tab.ordinal());
            int dy = -scroll[tab.ordinal()];
            if (pane.list().inside(mx, my)) {
                for (Row row : pane.rows()) {
                    for (Button b : row.buttons()) {
                        if (b.box().shifted(dy).inside(mx, my)) {
                            if (b.action() == Action.SET_TARIFF) {
                                submitTariff();
                            } else if (b.action() == Action.DEACTIVATE) {
                                deactivate(b.arg());
                            } else {
                                send(b.action(), b.arg(), b.value());
                            }
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /**
     * « Désactiver » : le premier appui arme (le bouton dit « Confirmer ? »), le second sur la même carte, entre 0,4 et
     * 5 s plus tard, envoie ; plus tôt, il ne compte pas.
     */
    private void deactivate(String arg) {
        long now = Util.getMillis();
        switch (OperatorPanelLayout.press(armed, arg, now)) {
            case CONFIRM -> {
                armed = null;
                send(Action.DEACTIVATE, arg, 0L);
            }
            case ARM -> {
                armed = new OperatorPanelLayout.Armed(arg, now);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK,
                        1.0F));
            }
            case IGNORE -> {
                // Double-clic machinal : le second appui ne compte pas, « Confirmer ? » reste.
            }
        }
        layout = null;
    }

    private void submitName() {
        if (nameBox != null && PowerSuppliers.validName(nameBox.getValue()).isPresent()) {
            send(Action.NAME, nameBox.getValue().strip(), 0L);
        }
    }

    private void submitTariff() {
        if (tariffBox == null || tariffBox.getValue().isEmpty()) {
            return;
        }
        try {
            send(Action.SET_TARIFF, "", Long.parseLong(tariffBox.getValue()));
            tariffBox.setValue("");
        } catch (NumberFormatException e) {
            // Sept chiffres au plus : jamais hors d'un long.
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 257 || key == 335) {
            if (nameBox != null && nameBox.isFocused()) {
                submitName();
                return true;
            }
            if (tariffBox != null && tariffBox.isFocused()) {
                submitTariff();
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        Layout l = currentLayout();
        if (!l.panes().isEmpty()) {
            int i = tab.ordinal();
            int max = l.panes().get(i).scrollMax();
            if (max > 0) {
                scroll[i] = Math.max(0, Math.min(max, scroll[i] - (int) Math.signum(delta) * 3 * OperatorPanelLayout.LINE));
                placeTariffBox();
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void onClose() {
        if (!closedByServer) {
            BenchNetwork.CHANNEL.sendToServer(new OperatorActionPacket(view.session(), Action.CLOSE, "", 0L));
            if (back != null) {
                dev.oneforallsurvival.phone.client.PhoneClient.antennaClosed(view.session());
            }
        }
        leave();
    }

    /** Rend l'écran d'avant : le repli du téléphone (source {@code PHONE}), sinon le jeu. */
    private void leave() {
        leaving = true;
        if (minecraft != null) {
            minecraft.setScreen(back);
        }
    }

    /**
     * Retiré sans Échap ni fermeture par le serveur (écran de chargement, écran ouvert par le serveur ou un autre mod) :
     * le serveur est prévenu ; pour le panneau du repli, la session du téléphone est fermée aussi, puisque le repli
     * caché ne l'a pas fait (revue de la tâche 10, mineur 1).
     */
    @Override
    public void removed() {
        super.removed();
        OperatorPanelLayout.Removal r = OperatorPanelLayout.onRemoval(back != null, leaving, closedByServer);
        leaving = true;
        if (r == OperatorPanelLayout.Removal.NONE) {
            return;
        }
        BenchNetwork.CHANNEL.sendToServer(new OperatorActionPacket(view.session(), Action.CLOSE, "", 0L));
        if (r == OperatorPanelLayout.Removal.PANEL_AND_PHONE
                && back instanceof dev.oneforallsurvival.phone.client.PhoneFallbackScreen fallback) {
            dev.oneforallsurvival.phone.client.PhoneClient.antennaClosed(view.session());
            fallback.closedUnderPanel();
        }
    }

    /** La session du téléphone fermée (ou remplacée) par le serveur : ce panneau se retire sans rien envoyer. */
    public void phoneClosedByServer() {
        closedByServer = true;
    }

    /** La sortie du monde : les numéros vus ne valent plus (un serveur relancé repart de 1, revue de la tâche 10). */
    public static void forgetSessions() {
        lastSession = -1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
