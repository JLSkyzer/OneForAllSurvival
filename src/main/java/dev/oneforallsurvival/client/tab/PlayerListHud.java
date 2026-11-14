package dev.oneforallsurvival.client.tab;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.client.debug.DebugTheme;
import dev.oneforallsurvival.client.tab.TabLayout.Mode;
import dev.oneforallsurvival.client.tab.TabLayout.Result;
import dev.oneforallsurvival.client.tab.TabRules.Job;
import dev.oneforallsurvival.client.tab.TabRules.Segment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Liste des joueurs (touche TAB) « Verre ambré », à la place de celle de vanilla : métier et couleur lus dans les équipes
 * du mod, maire dans un bandeau, jour et heure du monde. Mêmes conditions d'affichage que vanilla ; ni animation, ni
 * voile sur le jeu. Spécification : {@code docs/superpowers/specs/2026-11-14-liste-joueurs-design.md}.
 */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT)
public final class PlayerListHud {

    private static final int RULE = 0x59FFB544;
    private static final int SEPARATOR = 0x24FFBE5A;
    private static final int ODD_ROW = 0x09FFFFFF;
    private static final int BAR_OFF = 0x24FFFFFF;
    private static final int BAR_SHADOW = 0x8C000000;
    private static final int DOT_SHADOW = 0x99000000;
    private static final int HEAD_FRAME = 0x99000000;
    private static final int MAYOR_BG = 0x1AFFB544;
    private static final int MAYOR_EDGE = 0x38FFB544;
    private static final int MAYOR_COLOR = 0xFFFF55FF;
    private static final double SPECTATOR_ALPHA = 0.45;

    /** Un joueur listé, prêt à dessiner. */
    record Entry(PlayerInfo info, String name, Job job, String jobShown, int latency, boolean spectator) {}

    private static Result last;

    private PlayerListHud() {}

    /** Dernière mise en page dessinée ({@code null} si la liste n'a jamais été affichée) : lue par les essais en jeu. */
    public static Result lastLayout() {
        return last;
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.PLAYER_LIST.type()) {
            return;
        }
        event.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        ClientPacketListener connection = mc.player.connection;
        // Condition de ForgeGui.renderPlayerList (vanilla) : sinon la liste reste cachée.
        boolean show = mc.options.keyPlayerList.isDown() && (!mc.isLocalServer() || connection.getOnlinePlayers().size() > 1
                || mc.level.getScoreboard().getDisplayObjective(0) != null);
        mc.gui.getTabList().setVisible(show);
        if (!show) {
            return;
        }
        RenderSystem.enableBlend();
        draw(event.getGuiGraphics(), mc, connection.getListedOnlinePlayers());
    }

    private static Job jobOf(PlayerInfo info) {
        PlayerTeam team = info.getTeam();
        if (team == null) {
            return Job.NONE;
        }
        List<Segment> segments = new ArrayList<>();
        team.getPlayerPrefix().visit((style, text) -> {
            if (!text.isEmpty()) {
                segments.add(new Segment(text, style.getColor() == null ? null : style.getColor().getValue()));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return TabRules.job(team.getName(), segments);
    }

    private static void draw(GuiGraphics g, Minecraft mc, Collection<PlayerInfo> listed) {
        Font font = mc.font;
        TabLayout.Measure measure = measure(font);
        List<Entry> entries = new ArrayList<>();
        Entry mayor = null;
        int survivors = 0;
        for (PlayerInfo info : listed) {
            Job job = jobOf(info);
            boolean spectator = info.getGameMode() == GameType.SPECTATOR;
            String name = info.getProfile().getName();
            Entry e = new Entry(info, name, job, TabRules.truncate(job.label(), TabRules.JOB_MAX_W, font::width),
                    info.getLatency(), spectator);
            if (!spectator) {
                survivors++;
            }
            if (job.mayor() && (mayor == null || String.CASE_INSENSITIVE_ORDER.compare(name, mayor.name()) < 0)) {
                if (mayor != null) {
                    entries.add(mayor);
                }
                mayor = e;
            } else {
                entries.add(e);
            }
        }
        entries.sort(TabRules.order(Entry::spectator, Entry::job, Entry::name));

        long dayTime = mc.level.getDayTime();
        String day = Long.toString(TabRules.day(dayTime));
        String clock = TabRules.clock(dayTime);
        List<TabLayout.Row> rows = entries.stream().map(e -> new TabLayout.Row(e.name(), e.jobShown())).toList();
        TabLayout.Mayor mayorRow = mayor == null ? null : new TabLayout.Mayor(mayor.name(), mayor.jobShown());
        Result r = TabLayout.compute(rows, mayorRow, survivors, day, clock, g.guiWidth(), g.guiHeight(), measure);
        last = r;

        int x = r.x();
        int y = r.y();
        int iw = r.innerWidth();
        // Verre et bord de 1 px à l'intérieur.
        g.fill(x, y, x + r.width(), y + r.height(), DebugTheme.GLASS);
        g.fill(x, y, x + r.width(), y + 1, DebugTheme.BORDER);
        g.fill(x, y + r.height() - 1, x + r.width(), y + r.height(), DebugTheme.BORDER);
        g.fill(x, y + 1, x + 1, y + r.height() - 1, DebugTheme.BORDER);
        g.fill(x + r.width() - 1, y + 1, x + r.width(), y + r.height() - 1, DebugTheme.BORDER);

        drawHeader(g, font, measure, r, survivors);
        if (mayor != null) {
            drawMayor(g, font, measure, mayor, r, x + TabLayout.PAD, y + r.mayorY());
        }
        if (r.colsY() >= 0) {
            drawColumns(g, font, entries, r, x + TabLayout.PAD, y + r.colsY());
        }
        drawFooter(g, font, r, day, clock, x + TabLayout.PAD, y + r.footY(), iw);
    }

    private static void drawHeader(GuiGraphics g, Font font, TabLayout.Measure m, Result r, int survivors) {
        int x = r.x();
        int y = r.y();
        int ty = y + TabLayout.TEXT_Y;
        g.fill(x, y + 7, x + 3, y + 16, DebugTheme.ORANGE);
        int tx = x + TabLayout.PAD;
        g.drawString(font, bold(TabLayout.TITLE), tx, ty, DebugTheme.AMBER, true);
        tx += m.boldWidth(TabLayout.TITLE) + 4;
        g.drawString(font, bold(TabLayout.TITLE_RP), tx, ty, DebugTheme.ORANGE, true);
        if (!r.shortForms()) {
            tx += m.boldWidth(TabLayout.TITLE_RP) + 6;
            g.drawString(font, TabLayout.SUBTITLE, tx, ty, DebugTheme.LABEL, true);
        }
        String number = Integer.toString(survivors);
        String text = TabRules.survivorsText(survivors);
        int right = x + TabLayout.PAD + r.innerWidth();
        int textX = right - font.width(text);
        g.drawString(font, text, textX, ty, DebugTheme.LABEL, true);
        g.drawString(font, bold(number), textX - 4 - m.boldWidth(number), ty, DebugTheme.ISLAND, true);
        g.fill(x + TabLayout.PAD, y + 22, x + TabLayout.PAD + r.innerWidth(), y + 23, RULE);
        g.fill(x, y + 22, x + 40, y + 23, DebugTheme.AMBER);
    }

    private static void drawMayor(GuiGraphics g, Font font, TabLayout.Measure m, Entry mayor, Result r, int bx, int by) {
        int iw = r.innerWidth();
        double alpha = mayor.spectator() ? SPECTATOR_ALPHA : 1.0;
        g.fill(bx, by, bx + iw, by + TabLayout.MAYOR_H, MAYOR_BG);
        g.fill(bx, by, bx + iw, by + 1, MAYOR_EDGE);
        g.fill(bx, by + TabLayout.MAYOR_H - 1, bx + iw, by + TabLayout.MAYOR_H, MAYOR_EDGE);
        g.fill(bx + iw - 1, by + 1, bx + iw, by + TabLayout.MAYOR_H - 1, MAYOR_EDGE);
        g.fill(bx, by, bx + 2, by + TabLayout.MAYOR_H, MAYOR_COLOR);
        g.fill(bx + 5, by + 3, bx + 23, by + 21, fade(HEAD_FRAME, alpha));
        face(g, mayor, bx + 6, by + 4, 16, alpha);
        g.drawString(font, bold(TabLayout.MAYOR_WORD), bx + 28, by + 4, fade(MAYOR_COLOR, alpha), true);
        g.drawString(font, TabLayout.MAYOR_OF, bx + 28 + m.boldWidth(TabLayout.MAYOR_WORD) + 4, by + 4,
                fade(DebugTheme.LABEL, alpha), true);
        g.drawString(font, bold(mayor.name()), bx + 28, by + 13, fade(DebugTheme.VALUE, alpha), true);
        if (r.mayorJob() != null) {
            int dot = TabLayout.mayorDot(mayor.name(), m);
            int color = TabRules.displayColor(mayor.job().rgb());
            g.fill(bx + dot + 1, by + 16, bx + dot + 4, by + 19, fade(DOT_SHADOW, alpha));
            g.fill(bx + dot, by + 15, bx + dot + 3, by + 18, fade(color, alpha));
            g.drawString(font, r.mayorJob(), bx + dot + 6, by + 13, fade(color, alpha), true);
        }
        bars(g, bx + iw - 14, by + 13, mayor.latency(), alpha);
        String ping = pingWithUnit(mayor.latency());
        g.drawString(font, ping, bx + iw - 18 - font.width(ping), by + 13, fade(TabRules.pingColor(mayor.latency()), alpha), true);
    }

    private static void drawColumns(GuiGraphics g, Font font, List<Entry> entries, Result r, int left, int top) {
        boolean full = r.mode() == Mode.FULL;
        int rowsTop = top + TabLayout.COLHEAD_H;
        for (int c = 0; c < r.cols(); c++) {
            int cx = left + c * (r.colWidth() + TabLayout.COL_GAP);
            if (c > 0) {
                int sx = cx - TabLayout.COL_GAP / 2;
                g.fill(sx, top, sx + 1, top + TabLayout.COLHEAD_H + r.rows() * TabLayout.ROW_H, SEPARATOR);
            }
            g.drawString(font, TabLayout.COL_NAME, cx + (full ? 13 : 15), top, DebugTheme.LABEL, true);
            if (full) {
                g.drawString(font, TabLayout.COL_JOB, cx + r.dot(), top, DebugTheme.LABEL, true);
                g.drawString(font, TabLayout.COL_PING, cx + r.msRight() - font.width(TabLayout.COL_PING), top,
                        DebugTheme.LABEL, true);
            }
            for (int row = 0; row < r.rows(); row++) {
                int index = c * r.rows() + row;
                int ry = rowsTop + row * TabLayout.ROW_H;
                if (index < r.shown() || (r.hidden() > 0 && index == r.shown())) {
                    if (row % 2 == 1) {
                        g.fill(cx, ry, cx + r.colWidth(), ry + TabLayout.ROW_H, ODD_ROW);
                    }
                }
                if (index < r.shown()) {
                    drawRow(g, font, entries.get(index), r, cx, ry, full);
                } else if (r.hidden() > 0 && index == r.shown()) {
                    g.drawString(font, TabRules.othersText(r.hidden()), cx + 15, ry + 2, DebugTheme.LABEL, true);
                }
            }
        }
    }

    private static void drawRow(GuiGraphics g, Font font, Entry e, Result r, int cx, int y, boolean full) {
        double alpha = e.spectator() ? SPECTATOR_ALPHA : 1.0;
        int jobColor = TabRules.displayColor(e.job().rgb());
        if (full) {
            face(g, e, cx + 2, y + 1, 8, alpha);
            g.drawString(font, e.name(), cx + 13, y + 2, fade(DebugTheme.VALUE, alpha), true);
            if (e.jobShown() != null) {
                int dot = cx + r.dot();
                g.fill(dot + 1, y + 5, dot + 4, y + 8, fade(DOT_SHADOW, alpha));
                g.fill(dot, y + 4, dot + 3, y + 7, fade(jobColor, alpha));
                g.drawString(font, e.jobShown(), cx + r.jobX(), y + 2, fade(jobColor, alpha), true);
            }
            String ping = TabRules.pingText(e.latency());
            g.drawString(font, ping, cx + r.msRight() - font.width(ping), y + 2, fade(TabRules.pingColor(e.latency()), alpha), true);
        } else {
            if (e.jobShown() != null) {
                g.fill(cx, y + 1, cx + 2, y + 10, fade(jobColor, alpha));
            }
            face(g, e, cx + 4, y + 1, 8, alpha);
            g.drawString(font, e.name(), cx + 15, y + 2, fade(DebugTheme.VALUE, alpha), true);
        }
        bars(g, cx + r.bars(), y + 2, e.latency(), alpha);
    }

    private static void drawFooter(GuiGraphics g, Font font, Result r, String day, String clock, int fx, int fy, int iw) {
        g.fill(fx, fy, fx + iw, fy + 1, RULE);
        int ty = fy + 5;
        int tx = fx;
        g.drawString(font, TabLayout.DAY, tx, ty, DebugTheme.LABEL, true);
        tx += font.width(TabLayout.DAY) + 4;
        g.drawString(font, day, tx, ty, DebugTheme.VALUE, true);
        tx += font.width(day) + 8;
        g.drawString(font, TabLayout.HOUR, tx, ty, DebugTheme.LABEL, true);
        tx += font.width(TabLayout.HOUR) + 4;
        g.drawString(font, clock, tx, ty, DebugTheme.AMBER, true);
        if (!r.shortForms()) {
            g.drawString(font, TabLayout.FLIGHT, fx + iw - font.width(TabLayout.FLIGHT), ty, DebugTheme.ISLAND, true);
        }
    }

    /** Cinq barres de 1 px, une tous les 2 px, hauteurs 3 à 7 alignées en bas, chacune avec son ombre. */
    private static void bars(GuiGraphics g, int x, int y, int latency, double alpha) {
        int lit = TabRules.pingBars(latency);
        int on = fade(TabRules.pingColor(latency), alpha);
        int off = fade(BAR_OFF, alpha);
        int shadow = fade(BAR_SHADOW, alpha);
        for (int k = 0; k < 5; k++) {
            int h = 3 + k;
            int bx = x + 2 * k;
            int top = y + 7 - h;
            g.fill(bx + 1, top + 1, bx + 2, y + 8, shadow);
            g.fill(bx, top, bx + 1, y + 7, k < lit ? on : off);
        }
    }

    private static void face(GuiGraphics g, Entry e, int x, int y, int size, double alpha) {
        // Un fill précédent laisse le mélange coupé (RenderType.gui) : la tête d'un spectateur en a besoin.
        RenderSystem.enableBlend();
        if (alpha < 1.0) {
            g.setColor(1.0f, 1.0f, 1.0f, (float) alpha);
        }
        PlayerFaceRenderer.draw(g, e.info().getSkinLocation(), x, y, size);
        if (alpha < 1.0) {
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private static String pingWithUnit(int latency) {
        String text = TabRules.pingText(latency);
        return latency <= 0 ? text : text + " ms";
    }

    private static int fade(int argb, double alpha) {
        return alpha >= 1.0 ? argb : DebugTheme.fade(argb, alpha);
    }

    private static Component bold(String text) {
        return Component.literal(text).withStyle(ChatFormatting.BOLD);
    }

    private static TabLayout.Measure measure(Font font) {
        return new TabLayout.Measure() {
            @Override
            public int width(String text) {
                return font.width(text);
            }

            @Override
            public int boldWidth(String text) {
                return font.width(bold(text));
            }
        };
    }
}
