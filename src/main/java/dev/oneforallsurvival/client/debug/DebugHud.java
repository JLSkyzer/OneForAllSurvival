package dev.oneforallsurvival.client.debug;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.client.debug.DebugFormat.Row;
import dev.oneforallsurvival.client.debug.DebugFormat.Segment;
import dev.oneforallsurvival.client.debug.DebugFormat.Tone;
import dev.oneforallsurvival.client.debug.DebugLayout.Placement;
import dev.oneforallsurvival.client.debug.DebugLayout.Size;
import dev.oneforallsurvival.client.debug.DebugLayout.Slot;
import dev.oneforallsurvival.client.debug.DebugLines.Classified;
import dev.oneforallsurvival.client.debug.DebugLines.Line;
import dev.oneforallsurvival.client.debug.DebugLines.Panel;
import dev.oneforallsurvival.debug.IslandDebugInfo;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FrameTimer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Le nouveau F3. Passe en dernier sur {@code DebugText} pour voir les lignes de tous les mods, les classe, vide les
 * listes (Forge ne dessine plus rien) et dessine les cartes. L'événement est posté à chaque image, même F3 fermé.
 */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT)
public final class DebugHud {

    private static final int NORMAL_MIN_W = 112;
    private static final int NORMAL_MAX_W = 220;
    private static final int PERFORMANCE_W = 136;
    private static final int DETAIL_MAX_W = 270;
    /** Bande libre en bas pour la pastille de vue. */
    private static final int HINT_H = 12;
    /** Au-delà, la réponse du serveur ne dit plus rien de la position actuelle. */
    private static final long STALE_MILLIS = 3000;
    /** Images moyennées pour le temps d'image affiché. */
    private static final int AVERAGE_FRAMES = 30;
    /** Une image très longue (fenêtre gelée) ne doit pas faire sauter les animations. */
    private static final double MAX_DT = 0.25;
    private static final double TICK_MILLIS = 1000.0 / 20.0;
    private static final Map<Slot, Panel> PANELS = Map.of(
            Slot.POSITION, Panel.POSITION,
            Slot.WORLD, Panel.WORLD,
            Slot.MODS, Panel.MODS,
            Slot.DETAIL, Panel.DETAIL_ONLY,
            Slot.PERFORMANCE, Panel.PERFORMANCE,
            Slot.TARGET, Panel.TARGET);

    private static final SmoothLines SMOOTH = new SmoothLines();
    private static final SmoothNumber TPS_SMOOTH = new SmoothNumber(false);
    private static final Map<String, String> LABELS = new HashMap<>();
    private static final Map<String, String> NAMES = new HashMap<>();
    private static final int[] TRACKED_WIDTHS = new int[Slot.values().length];

    private static boolean open;
    private static long lastNanos;
    private static double sinceOpen;
    private static int view;
    private static int pages;
    private static int trackedGuiWidth;
    private static SmoothNumber feSmooth = new SmoothNumber(false);
    @Nullable
    private static BlockPos feTarget;

    private DebugHud() {}

    /** F3 + Tab : vue suivante, avec une nouvelle entrée des panneaux. */
    static void nextView() {
        view = DebugViews.next(view, Math.max(pages, 1));
        sinceOpen = 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.renderDebug) {
            close();
            return;
        }
        if (mc.options.hideGui) {
            event.getLeft().clear();
            event.getRight().clear();
            return;
        }
        boolean reduced = mc.showOnlyReducedInfo();
        Classified classified = DebugLines.classify(event.getLeft(), event.getRight(), reduced);
        event.getLeft().clear();
        event.getRight().clear();

        double dt = advanceClock();
        Map<Panel, List<Line>> lines = smoothed(classified, dt);
        List<Row> island = islandRows(mc, reduced, dt);
        Performance performance = performance(mc, lines.get(Panel.PERFORMANCE), dt);
        GuiGraphics g = event.getGuiGraphics();
        double titleScale = DebugTheme.titleScale((int) mc.getWindow().getGuiScale());
        g.drawManaged(() -> {
            int usable = g.guiHeight() - HINT_H - (mc.options.renderFpsChart ? DebugGraphs.RESERVED_H : 0);
            // La vue détaillée n'est calculée que quand elle est affichée.
            Detail detail = view == 0 ? null : layoutDetail(mc.font, g.guiWidth(), usable, lines, island);
            if (detail != null) {
                pages = detail.pages().size();
                view = DebugViews.clamp(view, pages);
            }
            if (view == 0) {
                drawNormal(g, mc.font, usable, lines, island, performance, titleScale);
            } else {
                drawDetail(g, mc.font, detail, detail.pages().get(view - 1), titleScale);
            }
            String hint = view == 0
                    ? I18n.get(DebugFormat.key("hint.normal"), DebugKeys.shortcut())
                    : I18n.get(DebugFormat.key("hint.page"), view, pages, DebugKeys.shortcut());
            DebugPanels.hint(g, mc.font, hint, g.guiWidth() - DebugLayout.MARGIN, usable - 1);
        });
    }

    /** Fermeture immédiate : tout repart de zéro à la prochaine ouverture. */
    private static void close() {
        if (!open) {
            return;
        }
        open = false;
        view = 0;
        pages = 0;
        SMOOTH.clear();
        LABELS.clear();
        NAMES.clear();
        DebugPanels.forgetWidths();
        Arrays.fill(TRACKED_WIDTHS, 0);
    }

    private static double advanceClock() {
        long now = Util.getNanos();
        double dt = 0;
        if (open) {
            dt = Math.min(MAX_DT, (now - lastNanos) / 1_000_000_000.0);
        } else {
            open = true;
            sinceOpen = 0;
        }
        lastNanos = now;
        sinceOpen += dt;
        return dt;
    }

    private static Map<Panel, List<Line>> smoothed(Classified classified, double dt) {
        Map<Panel, List<Line>> out = new EnumMap<>(Panel.class);
        for (Panel panel : Panel.values()) {
            List<Line> source = classified.lines(panel);
            List<Line> lines = new ArrayList<>(source.size());
            for (Line line : source) {
                lines.add(new Line(line.key(), SMOOTH.apply(line.key(), line.text(), dt)));
            }
            out.put(panel, lines);
        }
        return out;
    }

    private static String label(String key) {
        return LABELS.computeIfAbsent(key, I18n::get);
    }

    /** Le nom d'affichage d'un bloc ou d'une entité visés, gardé pendant que le F3 est ouvert. */
    @Nullable
    private static String targetName(String lineKey, String id) {
        boolean block = lineKey.equals("target.block");
        if (!block && !lineKey.equals("target.entity")) {
            return null;
        }
        String known = NAMES.get(id);
        if (known != null) {
            return known;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        String name = "";
        if (location != null && block && ForgeRegistries.BLOCKS.containsKey(location)) {
            name = ForgeRegistries.BLOCKS.getValue(location).getName().getString();
        } else if (location != null && !block && ForgeRegistries.ENTITY_TYPES.containsKey(location)) {
            name = ForgeRegistries.ENTITY_TYPES.getValue(location).getDescription().getString();
        }
        NAMES.put(id, name);
        return name;
    }

    private static List<Row> islandRows(Minecraft mc, boolean reduced, double dt) {
        if (reduced) {
            return List.of();
        }
        BlockPos looked = mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? hit.getBlockPos() : null;
        MachineState state = null;
        String name = null;
        if (looked != null && mc.level != null) {
            BlockState blockState = mc.level.getBlockState(looked);
            if (blockState.hasProperty(ElectricMachineBlock.STATE)) {
                state = blockState.getValue(ElectricMachineBlock.STATE);
                name = blockState.getBlock().getName().getString();
            }
        }
        IslandDebugInfo info = IslandDebugCache.ageMillis() > STALE_MILLIS ? null : IslandDebugCache.info();
        boolean matches = info != null && looked != null && Objects.equals(IslandDebugCache.target(), looked);
        double fe = 0;
        if (matches && info.meter() != null) {
            if (!looked.equals(feTarget)) {
                feTarget = looked;
                feSmooth = new SmoothNumber(false);
            }
            fe = feSmooth.update(info.meter().fePerTick(), dt);
        }
        return IslandLines.rows(false, new IslandLines.Input(info, matches, state, name, fe), DebugHud::label);
    }

    private static void drawNormal(GuiGraphics g, Font font, int usable, Map<Panel, List<Line>> lines, List<Row> island,
            Performance perf, double titleScale) {
        int guiW = g.guiWidth();
        if (guiW != trackedGuiWidth) {
            trackedGuiWidth = guiW;
            Arrays.fill(TRACKED_WIDTHS, 0);
        }
        Map<Slot, List<Row>> rows = new EnumMap<>(Slot.class);
        for (Slot slot : List.of(Slot.POSITION, Slot.WORLD, Slot.MODS, Slot.TARGET)) {
            rows.put(slot, DebugFormat.rows(lines.get(PANELS.get(slot)), DebugHud::label, DebugHud::targetName));
        }
        rows.put(Slot.ISLAND, island);

        int maxW = Math.min(NORMAL_MAX_W, guiW / 2 - DebugLayout.MARGIN - DebugLayout.GAP);
        Map<Slot, Size> sizes = new EnumMap<>(Slot.class);
        rows.forEach((slot, list) -> {
            if (!list.isEmpty()) {
                sizes.put(slot, new Size(trackedWidth(slot, font, list, maxW), DebugPanels.CHROME + list.size() * DebugPanels.LINE_H));
            }
        });
        sizes.put(Slot.PERFORMANCE, new Size(Math.min(PERFORMANCE_W, maxW),
                DebugPanels.performanceHeight(perf.memory() != null, perf.footer() != null)));

        DebugLayout.place(guiW, usable, sizes).forEach((slot, rect) -> {
            int rank = slot.ordinal();
            double alpha = PanelMotion.alpha(sinceOpen, rank);
            if (alpha <= 0) {
                return;
            }
            g.pose().pushPose();
            g.pose().translate(0, -PanelMotion.offset(sinceOpen, rank), 0);
            DebugPanels.card(g, font, rect, title(slot), slot == Slot.ISLAND, alpha, titleScale);
            if (slot == Slot.PERFORMANCE) {
                DebugPanels.performance(g, font, rect, perf.fps(), perf.fpsSuffix(), perf.timer(), perf.memory(),
                        perf.memoryLabel(), perf.memoryValue(), perf.footer(), alpha);
            } else {
                DebugPanels.rows(g, font, rect, rows.get(slot), alpha);
            }
            g.pose().popPose();
        });
    }

    /** La largeur ne rétrécit pas tant que le F3 reste ouvert : une carte ne tremble pas quand un chiffre change. */
    private static int trackedWidth(Slot slot, Font font, List<Row> rows, int maxW) {
        int w = NORMAL_MIN_W;
        for (Row row : rows) {
            w = Math.max(w, DebugPanels.rowWidth(font, row) + 2 * DebugPanels.PAD_X);
        }
        int kept = Math.min(maxW, Math.max(w, TRACKED_WIDTHS[slot.ordinal()]));
        TRACKED_WIDTHS[slot.ordinal()] = kept;
        return kept;
    }

    private static String title(Slot slot) {
        return label(DebugFormat.key("panel." + slot.name().toLowerCase(Locale.ROOT)));
    }

    /** Ce que montre la carte Performances pour cette image. */
    private record Performance(int fps, String fpsSuffix, FrameTimer timer, @Nullable DebugFormat.Memory memory,
            String memoryLabel, String memoryValue, @Nullable Row footer) {}

    private static Performance performance(Minecraft mc, List<Line> lines, double dt) {
        int fps = -1;
        DebugFormat.Memory memory = null;
        for (Line line : lines) {
            if (line.key().equals("fps")) {
                fps = DebugFormat.fps(line.text());
            } else if (line.key().equals("mem")) {
                memory = DebugFormat.memory(line.text());
            }
        }
        FrameTimer timer = mc.getFrameTimer();
        String suffix = "FPS" + DebugFormat.SEPARATOR + String.format(Locale.ROOT, "%.1f ms", averageMillis(timer));
        String memoryValue = memory == null ? "" : memory.used() + " / " + memory.max() + " " + label(DebugFormat.key("unit.megabytes"));
        return new Performance(fps, suffix, timer, memory, label(DebugFormat.key("label.memory")), memoryValue, footer(mc, dt));
    }

    private static double averageMillis(FrameTimer timer) {
        long[] log = timer.getLog();
        int end = timer.getLogEnd();
        long sum = 0;
        for (int i = 1; i <= AVERAGE_FRAMES; i++) {
            sum += log[timer.wrapIndex(end - i + log.length)];
        }
        return sum / (double) AVERAGE_FRAMES / 1_000_000.0;
    }

    /** TPS en solo (serveur intégré), ping en multijoueur. */
    @Nullable
    private static Row footer(Minecraft mc, double dt) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            double tps = TPS_SMOOTH.update(1000.0 / Math.max(TICK_MILLIS, server.getAverageTickTime()), dt);
            return new Row("footer", List.of(new Segment(label(DebugFormat.key("label.server")), Tone.LABEL),
                    new Segment(String.format(Locale.ROOT, " %.1f TPS", tps), Tone.VALUE)));
        }
        ClientPacketListener connection = mc.getConnection();
        PlayerInfo info = connection == null || mc.player == null ? null : connection.getPlayerInfo(mc.player.getUUID());
        if (info == null) {
            return null;
        }
        return new Row("footer", List.of(new Segment(label(DebugFormat.key("label.ping")), Tone.LABEL),
                new Segment(" " + info.getLatency() + " ms", Tone.VALUE)));
    }

    /** La vue détaillée d'une image : les textes découpés par carte et leurs pages. */
    private record Detail(Map<Slot, List<String>> texts, List<List<Placement>> pages) {}

    /** Toutes les lignes, découpées à la largeur de la colonne : rien n'est coupé ni perdu dans cette vue. */
    private static Detail layoutDetail(Font font, int guiW, int usable, Map<Panel, List<Line>> lines, List<Row> island) {
        int usableW = Math.max(1, guiW - 2 * DebugLayout.MARGIN);
        int inner = Math.max(1, Math.min(DETAIL_MAX_W, usableW) - 2 * DebugPanels.PAD_X);
        Map<Slot, List<String>> texts = new EnumMap<>(Slot.class);
        int widest = 0;
        for (Slot slot : Slot.values()) {
            List<String> wrapped = new ArrayList<>();
            if (slot == Slot.ISLAND) {
                for (Row row : island) {
                    wrapped.addAll(DebugFormat.wrap(row.text(), inner, s -> DebugPanels.width(font, s)));
                }
            } else {
                for (Line line : lines.get(PANELS.get(slot))) {
                    wrapped.addAll(DebugFormat.wrap(line.text(), inner, s -> DebugPanels.width(font, s)));
                }
            }
            if (!wrapped.isEmpty()) {
                texts.put(slot, wrapped);
                for (String text : wrapped) {
                    widest = Math.max(widest, DebugPanels.width(font, text));
                }
            }
        }
        // Colonnes de largeur égale, comme dans la maquette.
        int colW = Math.min(widest + 2 * DebugPanels.PAD_X, usableW);
        Map<Slot, Size> sizes = new EnumMap<>(Slot.class);
        texts.forEach((slot, list) -> sizes.put(slot, new Size(colW, DebugPanels.CHROME + list.size() * DebugPanels.LINE_H)));
        return new Detail(texts, DebugLayout.placeDetail(guiW, usable, sizes, DebugPanels.LINE_H, DebugPanels.CHROME));
    }

    private static void drawDetail(GuiGraphics g, Font font, Detail detail, List<Placement> page, double titleScale) {
        int rank = 0;
        for (Placement p : page) {
            double alpha = PanelMotion.alpha(sinceOpen, rank);
            if (alpha > 0) {
                g.pose().pushPose();
                g.pose().translate(0, -PanelMotion.offset(sinceOpen, rank), 0);
                DebugPanels.card(g, font, p.rect(), title(p.slot()), p.slot() == Slot.ISLAND, alpha, titleScale);
                DebugPanels.detailLines(g, font, p.rect(), detail.texts().get(p.slot()), p.firstLine(), p.lineCount(), alpha);
                g.pose().popPose();
            }
            rank++;
        }
    }
}
