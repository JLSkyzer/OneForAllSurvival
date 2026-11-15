package dev.oneforallsurvival.economy.quest.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.quest.QuestActionPacket;
import dev.oneforallsurvival.economy.quest.QuestRef;
import dev.oneforallsurvival.economy.quest.QuestSyncPacket;
import dev.oneforallsurvival.economy.quest.QuestSyncPacket.Entry;
import dev.oneforallsurvival.economy.quest.QuestSyncPacket.State;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.Layout;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.Rect;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.RowLayout;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.RowView;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.SectionLayout;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.SectionView;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.Text;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout.View;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * L'écran des quêtes (touche J) : plafond de la semaine en haut, « Aujourd'hui » et « Cette semaine », chaque quête
 * avec l'icône de sa cible, son titre, sa progression, sa récompense et le bouton « Réclamer » (une quête finie) ou
 * « Relancer » (une quête en cours, s'il reste une relance) ; les prochains renouvellements en bas ; un onglet
 * « Événements » s'il y en a. Il montre la copie du client ({@link QuestClientCache}) : chaque bouton envoie un
 * {@link QuestActionPacket} que le serveur revérifie, l'état revient ensuite. Mise en page : {@link QuestScreenLayout}.
 */
public final class QuestScreen extends Screen {

    private static final String GUI = "gui.oneforallsurvival.quests.";
    private static final long MINUTE = 60_000L;
    private static final long DAY = 24L * 60L * MINUTE;
    private static final int TOOLTIP_W = 200;

    private int tab;
    private int scroll;
    @Nullable
    private Layout layout;
    /** Les quêtes derrière chaque ligne de chaque section de la mise en page. */
    private final List<List<Entry>> rows = new ArrayList<>();
    private int layoutVersion = -1;
    private int layoutWidth;
    private int layoutHeight;
    private int layoutTab = -1;
    /** La minute de la mise en page : le bas (délais de renouvellement) suit l'heure. */
    private long layoutMinute = -1L;
    /** Démonstration : la souris tenue sur un élément (la souris de la machine reste libre). */
    @Nullable
    private String pinned;

    public QuestScreen() {
        super(Component.translatable(GUI + "title"));
    }

    /** La touche : l'écran s'ouvre sur la copie du client, et l'état frais est demandé au serveur. */
    public static void openAndRequest() {
        Minecraft.getInstance().setScreen(new QuestScreen());
        BenchNetwork.CHANNEL.sendToServer(QuestActionPacket.open());
    }

    /** Démonstration : l'onglet montré (0 quêtes, 1 événements). */
    public void showTab(int tab) {
        this.tab = tab;
        this.scroll = 0;
    }

    /**
     * Démonstration : la bulle d'un élément comme si la souris était dessus. {@code "title:s:r"}, {@code "button:s:r"},
     * {@code "reward:s:r"} (section, ligne) ou {@code "pending"} ; nul pour rendre la souris.
     */
    public void pinHover(@Nullable String element) {
        this.pinned = element;
    }

    @Nullable
    public Layout layout() {
        return layout;
    }

    // ---------------------------------------------------------------------------------------------
    // Mise en page
    // ---------------------------------------------------------------------------------------------

    private boolean hasEvents(@Nullable QuestSyncPacket state) {
        return state != null && state.quests().stream().anyMatch(e -> e.ref().kind() == QuestRef.Kind.EVENT);
    }

    private Layout currentLayout() {
        QuestSyncPacket state = QuestClientCache.get();
        if (tab == 1 && !hasEvents(state)) {
            tab = 0;
        }
        if (layout == null || layoutVersion != QuestClientCache.version() || layoutWidth != width
                || layoutHeight != height || layoutTab != tab || layoutMinute != QuestClientCache.ageMillis() / MINUTE) {
            layoutMinute = QuestClientCache.ageMillis() / MINUTE;
            layoutVersion = QuestClientCache.version();
            layoutWidth = width;
            layoutHeight = height;
            layoutTab = tab;
            layout = QuestScreenLayout.of(width, height, font::width, view(state));
            scroll = Math.max(0, Math.min(scroll, layout.scrollMax()));
        }
        return layout;
    }

    private View view(@Nullable QuestSyncPacket state) {
        rows.clear();
        String tabQuests = tr("title");
        if (state == null) {
            rows.add(List.of());
            return new View(tabQuests, null, "", null,
                    List.of(new SectionView(tr("loading"), "", List.of(), null)), "");
        }
        String tabEvents = hasEvents(state) ? tr("tab.events") : null;
        String cap = state.capPaid() >= state.cap()
                ? Component.translatable(GUI + "cap.reached").getString()
                : Component.translatable(GUI + "cap", grouped(state.capPaid()), grouped(state.cap())).getString();
        String pending = state.pendingCount() > 0
                ? Component.translatable(GUI + "pending", state.pendingCount()).getString() : null;
        List<SectionView> sections = new ArrayList<>();
        if (tab == 1) {
            section(sections, state, QuestRef.Kind.EVENT, tr("section.events"), "");
        } else {
            String rerolls = state.rerollsLeft() <= 0 ? ""
                    : state.rerollsLeft() == 1 ? tr("rerolls.one")
                    : Component.translatable(GUI + "rerolls.many", state.rerollsLeft()).getString();
            section(sections, state, QuestRef.Kind.DAILY, tr("section.daily"), rerolls);
            section(sections, state, QuestRef.Kind.WEEKLY, tr("section.weekly"), "");
        }
        return new View(tabQuests, tabEvents, cap, pending, sections, footer(state));
    }

    private void section(List<SectionView> sections, QuestSyncPacket state, QuestRef.Kind kind, String header,
            String right) {
        List<Entry> entries = new ArrayList<>();
        List<RowView> views = new ArrayList<>();
        for (Entry e : state.quests()) {
            if (e.ref().kind() != kind) {
                continue;
            }
            entries.add(e);
            List<String> parts = new ArrayList<>();
            CoinMath.parts(e.reward()).forEach(p -> parts.add(p.getString()));
            String button = null;
            String status = null;
            if (e.state() == State.COMPLETE) {
                button = tr("claim");
            } else if (e.state() == State.CLAIMED) {
                status = tr("claimed");
            } else if (kind != QuestRef.Kind.EVENT && state.rerollsLeft() > 0) {
                button = tr("reroll");
            }
            views.add(new RowView(e.title().getString(), parts, e.progress() + " / " + e.goal(), button, status));
        }
        rows.add(entries);
        sections.add(new SectionView(header, right, views, tr("none")));
    }

    private static String footer(QuestSyncPacket state) {
        long age = QuestClientCache.ageMillis();
        long daily = Math.max(0L, state.dailyResetIn() - age);
        long weekly = Math.max(0L, state.weeklyResetIn() - age);
        // Le dimanche, les hebdomadaires tournent avec les journalières (même heure) : une seule phrase.
        if (weekly < DAY && Math.abs(weekly - daily) < MINUTE) {
            return Component.translatable(GUI + "footer.both", duration(daily)).getString();
        }
        Component weeklyText = weekly < DAY ? Component.translatable(GUI + "footer.in", duration(weekly))
                : Component.translatable(GUI + "footer.monday");
        return Component.translatable(GUI + "footer", duration(daily), weeklyText).getString();
    }

    /** « 5 h 07 », ou « 12 min » sous l'heure (arrondi à la minute supérieure). */
    static Component duration(long millis) {
        long minutes = Math.max(1L, (millis + MINUTE - 1) / MINUTE);
        long hours = minutes / 60L;
        if (hours == 0L) {
            return Component.translatable(GUI + "minutes", minutes);
        }
        return Component.translatable(GUI + "hours", hours, String.format("%02d", minutes % 60L));
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

    private int listY(int y) {
        return y - scroll;
    }

    @Nullable
    private Entry entry(int section, int row) {
        if (section < 0 || section >= rows.size() || row < 0 || row >= rows.get(section).size()) {
            return null;
        }
        return rows.get(section).get(row);
    }

    private boolean claimable(Entry e) {
        return e.state() == State.COMPLETE && e.payable() > 0L;
    }

    @Override
    public void render(GuiGraphics g, int realMx, int realMy, float partialTick) {
        renderBackground(g);
        Layout l = currentLayout();
        int[] mouse = pinnedMouse(l, realMx, realMy);
        int mx = mouse[0];
        int my = mouse[1];
        QuestSyncPacket state = QuestClientCache.get();
        Rect f = l.frame();
        BenchTheme.plate(g, f.x(), f.y(), f.w(), f.h());
        BenchTheme.rivets(g, f.x(), f.y(), f.w(), f.h());

        for (int i = 0; i < l.tabs().size(); i++) {
            Rect t = l.tabs().get(i);
            boolean selected = i == tab;
            boolean clickable = l.tabs().size() > 1 && !selected;
            BenchTheme.button(g, font, t.x(), t.y(), t.w(), t.h(), "", clickable && t.inside(mx, my), true, selected);
            Text label = l.tabLabels().get(i);
            g.drawString(font, label.shown(), label.x(), label.y(),
                    selected ? BenchTheme.TEXT_ON_BRASS : BenchTheme.TEXT, false);
        }
        if (l.pendingButton() != null) {
            Rect p = l.pendingButton();
            BenchTheme.button(g, font, p.x(), p.y(), p.w(), p.h(), "", p.inside(mx, my), true, true);
            g.drawString(font, l.pendingLabel().shown(), l.pendingLabel().x(), l.pendingLabel().y(),
                    BenchTheme.TEXT_ON_BRASS, false);
        }
        boolean capReached = state != null && state.capPaid() >= state.cap();
        g.drawString(font, l.cap().shown(), l.cap().x(), l.cap().y(), capReached ? BenchTheme.TEXT_BAD : BenchTheme.TEXT_DIM,
                false);
        BenchTheme.rule(g, f.x() + QuestScreenLayout.PAD, l.list().y() - 2, QuestScreenLayout.INNER);

        Rect list = l.list();
        g.enableScissor(list.x(), list.y(), list.x() + list.w(), list.y() + list.h());
        for (int s = 0; s < l.sections().size(); s++) {
            SectionLayout section = l.sections().get(s);
            g.drawString(font, section.header().shown(), section.header().x(), listY(section.header().y()),
                    BenchTheme.BRASS_LIGHT, false);
            g.drawString(font, section.headerRight().shown(), section.headerRight().x(), listY(section.headerRight().y()),
                    BenchTheme.TEXT_DIM, false);
            if (section.empty() != null) {
                g.drawString(font, section.empty().shown(), section.empty().x(), listY(section.empty().y()),
                        BenchTheme.TEXT_DISABLED, false);
            }
            for (int r = 0; r < section.rows().size(); r++) {
                Entry e = entry(s, r);
                if (e != null) {
                    renderRow(g, section.rows().get(r), e, mx, my, list);
                }
            }
        }
        g.disableScissor();
        if (l.scrollMax() > 0) {
            int trackH = list.h();
            int thumbH = Math.max(12, trackH * trackH / (trackH + l.scrollMax()));
            int thumbY = list.y() + (trackH - thumbH) * scroll / l.scrollMax();
            g.fill(f.x() + f.w() - 5, list.y(), f.x() + f.w() - 3, list.y() + trackH, BenchTheme.WELL);
            g.fill(f.x() + f.w() - 5, thumbY, f.x() + f.w() - 3, thumbY + thumbH, BenchTheme.BRASS);
        }
        BenchTheme.rule(g, f.x() + QuestScreenLayout.PAD, l.footer().y() - 3, QuestScreenLayout.INNER);
        g.drawString(font, l.footer().shown(), l.footer().x(), l.footer().y(), BenchTheme.TEXT_DIM, false);
        super.render(g, mx, my, partialTick);
        renderTooltips(g, l, state, mx, my);
    }

    private void renderRow(GuiGraphics g, RowLayout row, Entry e, int mx, int my, Rect list) {
        Rect box = shifted(row.box());
        boolean hover = list.inside(mx, my) && box.inside(mx, my);
        if (hover) {
            g.fill(box.x(), box.y(), box.x() + box.w(), box.y() + box.h(), BenchTheme.ROW_HOVER);
        }
        g.renderItem(e.icon(), row.icon().x(), listY(row.icon().y()));
        boolean done = e.state() == State.CLAIMED;
        g.drawString(font, row.title().shown(), row.title().x(), listY(row.title().y()),
                done ? BenchTheme.TEXT_DIM : BenchTheme.TEXT, false);
        Rect bar = shifted(row.bar());
        BenchTheme.progress(g, bar.x(), bar.y(), bar.w(), bar.h(),
                e.goal() <= 0 ? 1.0D : Math.min(1.0D, e.progress() / (double) e.goal()));
        g.drawString(font, row.progress().shown(), row.progress().x(), listY(row.progress().y()),
                e.state() == State.IN_PROGRESS ? BenchTheme.TEXT_DIM : BenchTheme.TEXT_OK, false);
        int rewardColor = done ? BenchTheme.TEXT_DISABLED
                : e.state() == State.COMPLETE && e.payable() < e.reward() ? BenchTheme.TEXT_BAD : BenchTheme.BRASS_LIGHT;
        g.drawString(font, row.reward().shown(), row.reward().x(), listY(row.reward().y()), rewardColor, false);
        if (row.button() != null) {
            Rect b = shifted(row.button());
            boolean claim = e.state() == State.COMPLETE;
            boolean enabled = !claim || claimable(e);
            BenchTheme.button(g, font, b.x(), b.y(), b.w(), b.h(), "", enabled && list.inside(mx, my) && b.inside(mx, my),
                    enabled, claim);
            int color = !enabled ? BenchTheme.TEXT_DISABLED : claim ? BenchTheme.TEXT_ON_BRASS : BenchTheme.TEXT;
            g.drawString(font, row.buttonLabel().shown(), row.buttonLabel().x(), listY(row.buttonLabel().y()), color,
                    false);
        } else if (row.status() != null) {
            g.drawString(font, row.status().shown(), row.status().x(), listY(row.status().y()), BenchTheme.TEXT_OK,
                    false);
        }
    }

    private Rect shifted(Rect r) {
        return new Rect(r.x(), listY(r.y()), r.w(), r.h());
    }

    private void renderTooltips(GuiGraphics g, Layout l, @Nullable QuestSyncPacket state, int mx, int my) {
        if (state == null) {
            return;
        }
        if (l.pendingButton() != null && l.pendingButton().inside(mx, my)) {
            tooltip(g, Component.translatable(GUI + "pending.tooltip", state.pendingCount()), mx, my);
            return;
        }
        if (l.cap().cut() && l.cap().box().inside(mx, my)) {
            tooltip(g, Component.literal(l.cap().full()), mx, my);
            return;
        }
        if (!l.list().inside(mx, my)) {
            return;
        }
        for (int s = 0; s < l.sections().size(); s++) {
            List<RowLayout> sectionRows = l.sections().get(s).rows();
            for (int r = 0; r < sectionRows.size(); r++) {
                RowLayout row = sectionRows.get(r);
                Entry e = entry(s, r);
                if (e == null) {
                    continue;
                }
                if (row.button() != null && shifted(row.button()).inside(mx, my) && e.state() == State.COMPLETE) {
                    if (e.payable() <= 0L) {
                        tooltip(g, Component.translatable(GUI + "cap.reached"), mx, my);
                        return;
                    }
                    if (e.payable() < e.reward()) {
                        tooltip(g, Component.translatable(GUI + "partial", CoinMath.format(e.payable()),
                                CoinMath.format(e.reward())), mx, my);
                        return;
                    }
                }
                if (row.title().cut() && shifted(row.title().area()).inside(mx, my)) {
                    tooltip(g, e.title(), mx, my);
                    return;
                }
                if (row.reward().cut() && shifted(row.reward().box()).inside(mx, my)) {
                    tooltip(g, CoinMath.format(e.reward()), mx, my);
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
        if ("pending".equals(pinned)) {
            target = l.pendingButton();
        } else {
            String[] p = pinned.split(":");
            if (p.length == 3) {
                int s = Integer.parseInt(p[1]);
                int r = Integer.parseInt(p[2]);
                if (s < l.sections().size() && r < l.sections().get(s).rows().size()) {
                    RowLayout row = l.sections().get(s).rows().get(r);
                    target = switch (p[0]) {
                        case "title" -> row.title().box();
                        case "button" -> row.button();
                        case "reward" -> row.reward().box();
                        default -> null;
                    };
                    if (target != null) {
                        target = shifted(target);
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

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Layout l = currentLayout();
        QuestSyncPacket state = QuestClientCache.get();
        if (button != 0 || state == null) {
            return super.mouseClicked(mx, my, button);
        }
        for (int i = 0; i < l.tabs().size(); i++) {
            if (l.tabs().get(i).inside(mx, my) && i != tab && l.tabs().size() > 1) {
                click();
                showTab(i);
                return true;
            }
        }
        if (l.pendingButton() != null && l.pendingButton().inside(mx, my)) {
            click();
            BenchNetwork.CHANNEL.sendToServer(QuestActionPacket.claimPendingAll());
            return true;
        }
        if (l.list().inside(mx, my)) {
            for (int s = 0; s < l.sections().size(); s++) {
                List<RowLayout> sectionRows = l.sections().get(s).rows();
                for (int r = 0; r < sectionRows.size(); r++) {
                    RowLayout row = sectionRows.get(r);
                    Entry e = entry(s, r);
                    if (e == null || row.button() == null || !shifted(row.button()).inside(mx, my)) {
                        continue;
                    }
                    if (e.state() == State.COMPLETE) {
                        if (claimable(e)) {
                            click();
                            BenchNetwork.CHANNEL.sendToServer(QuestActionPacket.claim(e.ref()));
                        }
                    } else if (e.state() == State.IN_PROGRESS) {
                        click();
                        BenchNetwork.CHANNEL.sendToServer(QuestActionPacket.reroll(e.ref()));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        Layout l = currentLayout();
        if (l.scrollMax() > 0) {
            scroll = Math.max(0, Math.min(l.scrollMax(), scroll - (int) Math.signum(delta) * QuestScreenLayout.ROW_H));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (QuestKeys.OPEN.matches(key, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
