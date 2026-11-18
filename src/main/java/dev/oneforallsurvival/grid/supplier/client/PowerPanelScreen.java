package dev.oneforallsurvival.grid.supplier.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.grid.supplier.PowerActionPacket;
import dev.oneforallsurvival.grid.supplier.PowerClient;
import dev.oneforallsurvival.grid.supplier.PowerPanelPacket;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.Field;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.GapLine;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.GapView;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.Layout;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.Rect;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.RowLayout;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.RowView;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.Text;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout.View;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Le panneau du poste de fournisseur (clic droit d'un ayant droit) : le nom d'un poste neuf ; sinon le tarif du
 * lendemain (prix et abonnement), le plafond et la taxe, l'écart entre la sortie du poste et les compteurs (en rouge
 * au-delà du seuil), la caisse et la liste des clients, avec « Accepter » / « Refuser » une demande, « Couper » /
 * « Rétablir » et « Détacher » (deux clics) un client. Il montre la copie reçue du serveur ({@link PowerPanelPacket}) :
 * chaque geste envoie un {@link PowerActionPacket} que le serveur revérifie, l'état revient ensuite. Mise en page :
 * {@link PowerPanelLayout}.
 */
public final class PowerPanelScreen extends Screen {

    private static final String GUI = "gui.oneforallsurvival.power_panel.";
    private static final int TOOLTIP_W = 200;
    private static final double MAX_DISTANCE_SQR = PowerActionPacket.MAX_DISTANCE_SQR;

    private PowerPanelPacket state;
    private int version;
    @Nullable
    private Layout layout;
    private int layoutVersion = -1;
    private int layoutWidth;
    private int layoutHeight;
    private int scroll;
    @Nullable
    private EditBox nameBox;
    @Nullable
    private EditBox priceBox;
    @Nullable
    private EditBox subscriptionBox;
    /** Le compteur dont « Détacher » attend sa confirmation (second clic). */
    @Nullable
    private UUID confirmDetach;
    /** Démonstration : la souris tenue sur un élément (la souris de la machine reste libre). */
    @Nullable
    private String pinned;

    public PowerPanelScreen(PowerPanelPacket state) {
        super(Component.translatable(GUI + "title"));
        this.state = state;
    }

    /**
     * Un état reçu du serveur, sur le fil du client : met à jour le panneau ouvert sur ce poste. Seul l'état d'un clic
     * droit ({@link PowerPanelPacket#open()}) ouvre le panneau, et seulement sans autre écran ouvert : la réponse à un
     * geste arrivée après la fermeture du panneau (Échap, inventaire) ne le rouvre pas.
     */
    public static void accept(PowerPanelPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PowerPanelScreen open && open.state.pos().equals(packet.pos())) {
            open.update(packet);
        } else if (packet.open() && mc.screen == null) {
            mc.setScreen(new PowerPanelScreen(packet));
        }
    }

    private void update(PowerPanelPacket next) {
        boolean founded = !state.name().isEmpty();
        this.state = next;
        version++;
        if (founded != !next.name().isEmpty()) {
            rebuildWidgets(); // le poste vient d'être fondé : les champs ne sont plus les mêmes
        } else {
            lockTariff();
        }
    }

    /** Les champs du tarif ne s'écrivent que si le serveur dit que l'observateur peut le régler. */
    private void lockTariff() {
        for (EditBox box : new EditBox[] {priceBox, subscriptionBox}) {
            if (box != null) {
                box.setEditable(state.mayTariff());
                if (!state.mayTariff()) {
                    box.setValue("");
                    box.setFocused(false);
                }
            }
        }
    }

    public PowerPanelPacket state() {
        return state;
    }

    /** Démonstration : la bulle d'un élément comme si la souris était dessus ({@code "name:i"}, {@code "unpaid:i"},
     * {@code "usage:i"}, {@code "button:i:b"}, {@code "till"}, {@code "title"}, {@code "cap"}) ; nul pour rendre la
     * souris. */
    public void pinHover(@Nullable String element) {
        this.pinned = element;
    }

    /** Démonstration : le défilement de la liste. */
    public void scrollTo(int pixels) {
        this.scroll = Math.max(0, layout == null ? pixels : Math.min(layout.scrollMax(), pixels));
    }

    /** Démonstration : le texte d'un champ ({@code "name"}, {@code "price"}, {@code "subscription"}). */
    public void type(String field, String text) {
        EditBox box = switch (field) {
            case "name" -> nameBox;
            case "price" -> priceBox;
            default -> subscriptionBox;
        };
        if (box != null) {
            box.setValue(text);
        }
    }

    @Nullable
    public Layout layout() {
        return layout;
    }

    // ---------------------------------------------------------------------------------------------
    // Champs
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        Layout l = currentLayout(true);
        String keptName = nameBox == null ? "" : nameBox.getValue();
        String keptPrice = priceBox == null ? "" : priceBox.getValue();
        String keptSubscription = subscriptionBox == null ? "" : subscriptionBox.getValue();
        nameBox = null;
        priceBox = null;
        subscriptionBox = null;
        if (l.nameField() != null) {
            nameBox = box(l.nameField(), PowerSuppliers.MAX_NAME, GUI + "name");
            nameBox.setFilter(t -> t.chars().noneMatch(c -> Character.isISOControl(c) || c == '§'));
            nameBox.setValue(keptName);
            setInitialFocus(nameBox);
        }
        if (l.price() != null) {
            priceBox = box(l.price().box(), 12, GUI + "price");
            priceBox.setFilter(PowerPanelScreen::digits);
            priceBox.setValue(keptPrice);
            subscriptionBox = box(l.subscription().box(), 12, GUI + "subscription");
            subscriptionBox.setFilter(PowerPanelScreen::digits);
            subscriptionBox.setValue(keptSubscription);
            lockTariff();
        }
    }

    private static boolean digits(String text) {
        return text.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private EditBox box(Rect r, int maxLength, String key) {
        EditBox box = new EditBox(font, r.x() + 4, r.y() + (r.h() - 8) / 2, r.w() - 8, 9, Component.translatable(key));
        box.setBordered(false);
        box.setTextColor(BenchTheme.TEXT);
        box.setMaxLength(maxLength);
        addRenderableWidget(box);
        return box;
    }

    // ---------------------------------------------------------------------------------------------
    // Mise en page
    // ---------------------------------------------------------------------------------------------

    private Layout currentLayout(boolean force) {
        if (force || layout == null || layoutVersion != version || layoutWidth != width || layoutHeight != height
                || confirmVersion != confirmShown) {
            layoutVersion = version;
            layoutWidth = width;
            layoutHeight = height;
            confirmShown = confirmVersion;
            layout = PowerPanelLayout.of(width, height, font::width, view());
            scroll = Math.max(0, Math.min(scroll, layout.scrollMax()));
        }
        return layout;
    }

    private int confirmVersion;
    private int confirmShown = -1;

    private View view() {
        PowerPanelPacket s = state;
        List<String> headers = List.of(tr("gap.out"), tr("gap.meters"), tr("gap.gap"));
        if (s.name().isEmpty()) {
            GapView none = new GapView("", "", "", "", 0L, 0L);
            return new View(null, tr("new_station"), tr("till"),
                    Component.translatable(GUI + "name_label", PowerSuppliers.MAX_NAME).getString(), tr("found"), "",
                    "", tr("ok"), "", headers, none, none, s.warnPercent(), "", List.of(), "", List.of(), "");
        }
        String price = s.nextPrice() == s.price()
                ? Component.translatable(GUI + "price", CoinMath.format(s.price())).getString()
                : Component.translatable(GUI + "price_next", CoinMath.format(s.price()),
                        CoinMath.format(s.nextPrice())).getString();
        String subscription = s.nextSubscription() == s.subscription()
                ? Component.translatable(GUI + "subscription", CoinMath.format(s.subscription())).getString()
                : Component.translatable(GUI + "subscription_next", CoinMath.format(s.subscription()),
                        CoinMath.format(s.nextSubscription())).getString();
        String cap = Component.translatable(GUI + "cap", CoinMath.format(s.cap()), grouped(s.unitFe()), s.tax())
                .getString();
        if (s.nextCap() != s.cap() || s.nextTax() != s.tax()) {
            cap = cap + " " + Component.translatable(GUI + "cap_next", CoinMath.format(s.nextCap()), s.nextTax())
                    .getString();
        }
        GapView today = gapView(tr("gap.today"), s.outToday(), s.metersToday());
        GapView yesterday = gapView(tr("gap.yesterday"), s.outYesterday(), s.metersYesterday());
        List<String> tillParts = new ArrayList<>();
        CoinMath.parts(s.till()).forEach(p -> tillParts.add(p.getString()));
        if (tillParts.isEmpty()) {
            tillParts.add(tr("till_empty"));
        }
        String header = s.clientCount() > s.clients().size()
                ? Component.translatable(GUI + "clients_more", s.clients().size(), s.clientCount()).getString()
                : Component.translatable(GUI + "clients", s.clientCount()).getString();
        List<RowView> rows = new ArrayList<>();
        for (PowerPanelPacket.Client c : s.clients()) {
            rows.add(row(c));
        }
        return new View(s.name(), s.name(), s.mayTakeTill() ? tr("till") : tr("till_read"), "", "", price,
                subscription, tr("ok"), cap, headers, today, yesterday, s.warnPercent(), tr("till_label"), tillParts,
                header, rows, tr("no_clients"));
    }

    private RowView row(PowerPanelPacket.Client c) {
        String name = c.subscriber().equalsIgnoreCase(c.player()) ? c.player()
                : Component.translatable(GUI + "client_name", c.subscriber(), c.player()).getString();
        String usage = Component.translatable(GUI + "usage", energy(c.feToday()), energy(c.feYesterday())).getString();
        List<String> unpaid = new ArrayList<>();
        if (c.unpaid() > 0L) {
            // Rien à payer : rien d'affiché (« 0 cuivre » en rouge se lirait comme une dette).
            CoinMath.parts(c.unpaid()).forEach(p -> unpaid.add(p.getString()));
        }
        return new RowView(name, tr("state." + stateKey(c)), usage, tr("unpaid"), unpaid, buttons(c));
    }

    private static String stateKey(PowerPanelPacket.Client c) {
        if (c.state().passes() && c.cut()) {
            return "cut";
        }
        return switch (c.state()) {
            case AWAITING_CONSENT -> "awaiting_consent";
            case AWAITING_ACCEPT -> "awaiting_accept";
            case ACTIVE -> "active";
            case RESIGNING -> "resigning";
        };
    }

    /** Les libellés des boutons d'un client (dans l'ordre de {@link #actions}). */
    private List<String> buttons(PowerPanelPacket.Client c) {
        List<String> out = new ArrayList<>();
        for (int action : actions(c)) {
            out.add(switch (action) {
                case PowerActionPacket.ACCEPT -> tr("accept");
                case PowerActionPacket.REFUSE -> tr("refuse");
                case PowerActionPacket.CUT -> tr("cut");
                case PowerActionPacket.RESTORE -> tr("restore");
                default -> c.meter().equals(confirmDetach) ? tr("confirm") : tr("detach");
            });
        }
        return out;
    }

    /** Les gestes offerts pour un client : une demande s'accepte ou se refuse ; un raccordé se coupe ou se rétablit, et
     * se détache. */
    private static int[] actions(PowerPanelPacket.Client c) {
        if (c.state() == PowerClient.State.AWAITING_ACCEPT) {
            return new int[] {PowerActionPacket.ACCEPT, PowerActionPacket.REFUSE};
        }
        if (c.state() == PowerClient.State.AWAITING_CONSENT) {
            return new int[] {PowerActionPacket.REFUSE};
        }
        return new int[] {c.cut() ? PowerActionPacket.RESTORE : PowerActionPacket.CUT, PowerActionPacket.DETACH};
    }

    private static boolean enabled(PowerPanelPacket.Client c, int action) {
        return action != PowerActionPacket.CUT || c.breaker();
    }

    private GapView gapView(String label, long out, long meters) {
        long gap = out - meters;
        String percent = out > 0L ? Long.toString(Math.round(Math.abs((double) gap) * 100.0D / out)) : "—";
        return new GapView(label, energy(out), energy(meters),
                Component.translatable(GUI + "gap.value", energy(gap), percent).getString(), out, meters);
    }

    /** « 12 345 kFE », ou « 840 FE » sous 10 000 FE (signé pour l'écart). */
    static String energy(long fe) {
        long abs = fe == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(fe);
        String sign = fe < 0 ? "-" : "";
        if (abs < 10_000L) {
            return Component.translatable(GUI + "fe", sign + abs).getString();
        }
        return Component.translatable(GUI + "kfe", sign + grouped(abs / 1000L)).getString();
    }

    /** « 1 000 » : les milliers séparés selon la langue. */
    static String grouped(long value) {
        String sep = Component.translatable(GUI + "thousands").getString();
        String digits = Long.toString(Math.abs(value));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append(sep);
            }
            out.append(digits.charAt(i));
        }
        return (value < 0 ? "-" : "") + out;
    }

    private static String tr(String key) {
        return Component.translatable(GUI + key).getString();
    }

    // ---------------------------------------------------------------------------------------------
    // Rendu
    // ---------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && minecraft.player != null
                && minecraft.player.distanceToSqr(Vec3.atCenterOf(state.pos())) > MAX_DISTANCE_SQR) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int realMx, int realMy, float partialTick) {
        renderBackground(g);
        Layout l = currentLayout(false);
        int[] mouse = pinnedMouse(l, realMx, realMy);
        int mx = mouse[0];
        int my = mouse[1];
        Rect f = l.frame();
        BenchTheme.plate(g, f.x(), f.y(), f.w(), f.h());
        BenchTheme.rivets(g, f.x(), f.y(), f.w(), f.h());
        draw(g, l.title(), BenchTheme.BRASS_LIGHT);
        BenchTheme.rule(g, f.x() + PowerPanelLayout.PAD, f.y() + PowerPanelLayout.RULE_Y, PowerPanelLayout.INNER);

        if (l.nameField() != null) {
            draw(g, l.nameLabel(), BenchTheme.TEXT);
            Rect nf = l.nameField();
            BenchTheme.well(g, nf.x(), nf.y(), nf.w(), nf.h());
            Rect fb = l.foundButton();
            boolean ok = nameValid();
            BenchTheme.button(g, font, fb.x(), fb.y(), fb.w(), fb.h(), l.foundLabel().full(), ok && fb.inside(mx, my), ok,
                    true);
            super.render(g, mx, my, partialTick);
            renderTooltips(g, l, mx, my);
            return;
        }

        Rect tb = l.tillButton();
        BenchTheme.button(g, font, tb.x(), tb.y(), tb.w(), tb.h(), l.tillLabel().full(), tb.inside(mx, my), true, false);
        for (Field field : new Field[] {l.price(), l.subscription()}) {
            draw(g, field.label(), state.mayTariff() ? BenchTheme.TEXT : BenchTheme.TEXT_DIM);
            Rect b = field.box();
            BenchTheme.well(g, b.x(), b.y(), b.w(), b.h());
            Rect ok = field.button();
            boolean filled = state.mayTariff()
                    && !(field == l.price() ? priceBox : subscriptionBox).getValue().isEmpty();
            BenchTheme.button(g, font, ok.x(), ok.y(), ok.w(), ok.h(), field.buttonLabel().full(),
                    filled && ok.inside(mx, my), filled, true);
        }
        draw(g, l.cap(), BenchTheme.TEXT_DIM);
        for (Text h : l.gapHeaders()) {
            draw(g, h, BenchTheme.TEXT_DIM);
        }
        for (GapLine gap : new GapLine[] {l.today(), l.yesterday()}) {
            draw(g, gap.label(), BenchTheme.TEXT_DIM);
            draw(g, gap.out(), BenchTheme.TEXT);
            draw(g, gap.meters(), BenchTheme.TEXT);
            draw(g, gap.gap(), gap.alert() ? BenchTheme.TEXT_BAD : BenchTheme.TEXT_OK);
        }
        draw(g, l.till(), BenchTheme.BRASS_LIGHT);
        BenchTheme.rule(g, f.x() + PowerPanelLayout.PAD, f.y() + PowerPanelLayout.RULE2_Y, PowerPanelLayout.INNER);
        draw(g, l.listHeader(), BenchTheme.BRASS_LIGHT);

        Rect list = l.list();
        g.enableScissor(list.x(), list.y(), list.x() + list.w(), list.y() + list.h());
        if (l.empty() != null) {
            draw(g, l.empty(), BenchTheme.TEXT_DISABLED);
        }
        for (int i = 0; i < l.rows().size() && i < state.clients().size(); i++) {
            renderRow(g, l.rows().get(i), state.clients().get(i), mx, my, list);
        }
        g.disableScissor();
        if (l.scrollMax() > 0) {
            int trackH = list.h();
            int thumbH = Math.max(12, trackH * trackH / (trackH + l.scrollMax()));
            int thumbY = list.y() + (trackH - thumbH) * scroll / l.scrollMax();
            int x = list.x() + list.w() - 3;
            g.fill(x, list.y(), x + 2, list.y() + trackH, BenchTheme.WELL);
            g.fill(x, thumbY, x + 2, thumbY + thumbH, BenchTheme.BRASS);
        }
        super.render(g, mx, my, partialTick);
        renderTooltips(g, l, mx, my);
    }

    private void renderRow(GuiGraphics g, RowLayout row, PowerPanelPacket.Client c, int mx, int my, Rect list) {
        Rect box = shifted(row.box());
        if (box.y() + box.h() < list.y() || box.y() > list.y() + list.h()) {
            return;
        }
        boolean inList = list.inside(mx, my);
        if (inList && box.inside(mx, my)) {
            g.fill(box.x(), box.y(), box.x() + box.w(), box.y() + box.h(), BenchTheme.ROW_HOVER);
        }
        drawShifted(g, row.name(), BenchTheme.TEXT);
        int stateColor = c.state() == PowerClient.State.AWAITING_ACCEPT ? BenchTheme.BRASS_LIGHT
                : c.cut() ? BenchTheme.TEXT_BAD
                : c.state().passes() ? BenchTheme.TEXT_OK : BenchTheme.TEXT_DIM;
        drawShifted(g, row.state(), stateColor);
        drawShifted(g, row.usage(), BenchTheme.TEXT_DIM);
        drawShifted(g, row.unpaid(), BenchTheme.TEXT_BAD);
        int[] actions = actions(c);
        for (int b = 0; b < row.buttons().size() && b < actions.length; b++) {
            Rect r = shifted(row.buttons().get(b));
            boolean enabled = enabled(c, actions[b]);
            boolean primary = actions[b] == PowerActionPacket.ACCEPT
                    || actions[b] == PowerActionPacket.DETACH && c.meter().equals(confirmDetach);
            BenchTheme.button(g, font, r.x(), r.y(), r.w(), r.h(), row.buttonLabels().get(b).full(),
                    enabled && inList && r.inside(mx, my), enabled, primary);
        }
    }

    private void draw(GuiGraphics g, @Nullable Text t, int color) {
        if (t != null && !t.shown().isEmpty()) {
            g.drawString(font, t.shown(), t.x(), t.y(), color, false);
        }
    }

    private void drawShifted(GuiGraphics g, Text t, int color) {
        if (!t.shown().isEmpty()) {
            g.drawString(font, t.shown(), t.x(), t.y() - scroll, color, false);
        }
    }

    private Rect shifted(Rect r) {
        return r.shifted(-scroll);
    }

    private void renderTooltips(GuiGraphics g, Layout l, int mx, int my) {
        for (Text t : new Text[] {l.title(), l.nameLabel(), l.cap(), l.till()}) {
            if (t != null && t.cut() && t.area().inside(mx, my)) {
                tooltip(g, Component.literal(t.full()), mx, my);
                return;
            }
        }
        if (l.nameField() != null) {
            return;
        }
        for (Field field : new Field[] {l.price(), l.subscription()}) {
            if (field.label().cut() && field.label().area().inside(mx, my)) {
                tooltip(g, Component.literal(field.label().full()), mx, my);
                return;
            }
            if (!state.mayTariff() && (field.box().inside(mx, my) || field.button().inside(mx, my))) {
                tooltip(g, Component.translatable(GUI + "tariff_locked.tooltip"), mx, my);
                return;
            }
            if (field.box().inside(mx, my) || field.button().inside(mx, my)) {
                tooltip(g, field == l.price()
                        ? Component.translatable(GUI + "price.tooltip", grouped(state.unitFe()),
                                CoinMath.format(state.nextCap()))
                        : Component.translatable(GUI + "subscription.tooltip", CoinMath.format(state.maxSubscription())),
                        mx, my);
                return;
            }
        }
        for (GapLine gap : new GapLine[] {l.today(), l.yesterday()}) {
            for (Text t : new Text[] {gap.out(), gap.meters()}) {
                if (t.cut() && t.area().inside(mx, my)) {
                    tooltip(g, Component.literal(t.full()), mx, my);
                    return;
                }
            }
            if (gap.gap().area().inside(mx, my)) {
                // L'écart entier (s'il est coupé), puis ce que veut dire sa couleur.
                List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
                if (gap.gap().cut()) {
                    lines.addAll(font.split(Component.literal(gap.gap().full()), TOOLTIP_W));
                }
                lines.addAll(font.split(Component.translatable(GUI + (gap.alert() ? "gap.alert" : "gap.tooltip"),
                        state.warnPercent(), grouped(PowerPanelLayout.GAP_WARN_MIN_FE)), TOOLTIP_W));
                g.renderTooltip(font, lines, mx, my);
                return;
            }
        }
        if (l.tillButton().inside(mx, my) && !state.mayTakeTill()) {
            tooltip(g, Component.translatable(GUI + "till_read.tooltip"), mx, my);
            return;
        }
        if (!l.list().inside(mx, my)) {
            return;
        }
        for (int i = 0; i < l.rows().size() && i < state.clients().size(); i++) {
            RowLayout row = l.rows().get(i);
            PowerPanelPacket.Client c = state.clients().get(i);
            int[] actions = actions(c);
            for (int b = 0; b < row.buttons().size() && b < actions.length; b++) {
                if (shifted(row.buttons().get(b)).inside(mx, my)) {
                    if (!enabled(c, actions[b])) {
                        tooltip(g, Component.translatable(GUI + "no_breaker.tooltip"), mx, my);
                        return;
                    }
                    if (actions[b] == PowerActionPacket.DETACH) {
                        tooltip(g, Component.translatable(GUI + "detach.tooltip"), mx, my);
                        return;
                    }
                }
            }
            for (Text t : new Text[] {row.name(), row.state(), row.usage(), row.unpaid()}) {
                if (t.cut() && shifted(t.area()).inside(mx, my)) {
                    tooltip(g, Component.literal(t.full()), mx, my);
                    return;
                }
            }
        }
    }

    /** Une bulle repliée en lignes de {@value #TOOLTIP_W} pixels au plus : elle tient dans 320 × 240. */
    private void tooltip(GuiGraphics g, Component text, int mx, int my) {
        g.renderTooltip(font, font.split(text, TOOLTIP_W), mx, my);
    }

    /** La souris réelle, ou le centre de l'élément tenu par la démonstration. */
    private int[] pinnedMouse(Layout l, int mx, int my) {
        if (pinned == null) {
            return new int[] {mx, my};
        }
        Rect target = null;
        String[] p = pinned.split(":");
        switch (p[0]) {
            case "title" -> target = l.title().box();
            case "cap" -> target = l.cap() == null ? null : l.cap().box();
            case "till" -> target = l.till() == null ? null : l.till().box();
            case "gap" -> target = l.today() == null ? null : l.today().gap().box();
            case "price" -> target = l.price() == null ? null : l.price().box();
            case "price_ok" -> target = l.price() == null ? null : l.price().button();
            default -> {
                if (p.length >= 2) {
                    int i = Integer.parseInt(p[1]);
                    if (i < l.rows().size()) {
                        RowLayout row = l.rows().get(i);
                        Rect r = switch (p[0]) {
                            case "name" -> row.name().box();
                            case "usage" -> row.usage().box();
                            case "unpaid" -> row.unpaid().box();
                            case "button" -> p.length >= 3 && Integer.parseInt(p[2]) < row.buttons().size()
                                    ? row.buttons().get(Integer.parseInt(p[2])) : null;
                            default -> null;
                        };
                        target = r == null ? null : shifted(r);
                    }
                }
            }
        }
        return target == null ? new int[] {mx, my}
                : new int[] {target.x() + target.w() / 2, target.y() + target.h() / 2};
    }

    // ---------------------------------------------------------------------------------------------
    // Gestes
    // ---------------------------------------------------------------------------------------------

    private boolean nameValid() {
        return nameBox != null && PowerSuppliers.validName(nameBox.getValue()).isPresent();
    }

    private void send(PowerActionPacket packet) {
        click();
        BenchNetwork.CHANNEL.sendToServer(packet);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Layout l = currentLayout(false);
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        if (l.foundButton() != null && l.foundButton().inside(mx, my)) {
            if (nameValid()) {
                send(PowerActionPacket.name(state.pos(), nameBox.getValue().strip()));
            }
            return true;
        }
        if (l.tillButton() != null && l.tillButton().inside(mx, my)) {
            send(PowerActionPacket.openTill(state.pos()));
            return true;
        }
        if (l.price() != null) {
            if (l.price().button().inside(mx, my)) {
                submit(priceBox, true);
                return true;
            }
            if (l.subscription().button().inside(mx, my)) {
                submit(subscriptionBox, false);
                return true;
            }
        }
        if (l.list() != null && l.list().inside(mx, my)) {
            for (int i = 0; i < l.rows().size() && i < state.clients().size(); i++) {
                RowLayout row = l.rows().get(i);
                PowerPanelPacket.Client c = state.clients().get(i);
                int[] actions = actions(c);
                for (int b = 0; b < row.buttons().size() && b < actions.length; b++) {
                    if (!shifted(row.buttons().get(b)).inside(mx, my)) {
                        continue;
                    }
                    int action = actions[b];
                    if (!enabled(c, action)) {
                        return true;
                    }
                    if (action == PowerActionPacket.DETACH && !c.meter().equals(confirmDetach)) {
                        click();
                        confirmDetach = c.meter();
                        confirmVersion++;
                        return true;
                    }
                    confirmDetach = null;
                    confirmVersion++;
                    send(PowerActionPacket.onMeter(action, state.pos(), c.meter()));
                    return true;
                }
            }
        }
        if (confirmDetach != null) {
            confirmDetach = null;
            confirmVersion++;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void submit(@Nullable EditBox box, boolean price) {
        if (box == null || !state.mayTariff() || box.getValue().isEmpty()) {
            return;
        }
        long value;
        try {
            value = Long.parseLong(box.getValue());
        } catch (NumberFormatException e) {
            return;
        }
        send(price ? PowerActionPacket.price(state.pos(), value) : PowerActionPacket.subscription(state.pos(), value));
        box.setValue("");
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Entrée valide le champ qui a la main.
        if (key == 257 || key == 335) {
            if (nameBox != null && nameBox.isFocused()) {
                if (nameValid()) {
                    send(PowerActionPacket.name(state.pos(), nameBox.getValue().strip()));
                }
                return true;
            }
            if (priceBox != null && priceBox.isFocused()) {
                submit(priceBox, true);
                return true;
            }
            if (subscriptionBox != null && subscriptionBox.isFocused()) {
                submit(subscriptionBox, false);
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        Layout l = currentLayout(false);
        if (l.scrollMax() > 0) {
            scroll = Math.max(0, Math.min(l.scrollMax(), scroll - (int) Math.signum(delta) * PowerPanelLayout.ROW_H));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
