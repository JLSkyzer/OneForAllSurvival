package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinAccountMenu;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.CoinStore;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * L'écran commun du coffre-fort et de la bourse, au décor de l'établi ({@link BenchTheme}) : le total en haut, une
 * ligne par coupure (la plus grande d'abord) avec ses boutons 1, 10 et 64 (clic gauche : déposer, clic droit :
 * retirer), « Déposer tout » et « Regrouper », puis l'inventaire du joueur ; à droite, le panneau « Change » (casser
 * une pièce, regrouper une paire ; maj + clic : autant que possible). Chaque bouton a sa bulle d'aide, qui dit aussi
 * quel sens est indisponible et pourquoi.
 *
 * <p>Tout ce qui se clique est un {@link Element} d'une seule liste ({@link #elements}), qui sert à la fois au rendu
 * et aux clics : la géométrie n'est écrite qu'une fois. L'écran ne décide de rien : chaque clic envoie une
 * {@link CoinRequest} que le serveur revalide, et les nombres affichés sont ceux que le serveur a envoyés. Les
 * sous-classes ne fournissent que le paquet.
 */
public abstract class CoinAccountScreen<M extends CoinAccountMenu> extends AbstractContainerScreen<M> {

    protected static final String GUI = "gui.oneforallsurvival.coins.";

    static final int MAIN_W = 176;
    static final int HEIGHT = 232;
    static final int PAD = 8;
    static final int TOTAL_Y = 18;
    static final int ROWS_Y = 30;
    static final int ROW_H = 18;
    static final int ROW_BUTTON_W = 22;
    static final int ROW_BUTTON_H = 14;
    static final int ROW_BUTTON_GAP = 2;
    static final int FOOT_Y = ROWS_Y + 5 * ROW_H + 3;
    static final int FOOT_W = (MAIN_W - 2 * PAD - 4) / 2;
    static final int FOOT_H = 14;
    static final int INVENTORY_LABEL_Y = CoinAccountMenu.INVENTORY_Y - 10;
    /** Le panneau « Change », accolé à droite du cadre principal. */
    static final int PANEL_X = MAIN_W + 2;
    static final int PANEL_W = 124;
    static final int PANEL_H = 98;
    static final int CHANGE_Y = 20;
    static final int CHANGE_W = 56;
    static final int CHANGE_H = 16;
    static final int CHANGE_ROW = 18;

    /** Ce qu'un élément dessine dans son bouton. */
    @FunctionalInterface
    protected interface Painter {
        void paint(GuiGraphics g, Element element, boolean hover);
    }

    /**
     * Un bouton : son rectangle (coordonnées d'écran), sa bulle d'aide, ce qu'envoient le clic gauche, le clic droit
     * et le maj-clic gauche (nul : indisponible), et ce qu'il dessine.
     */
    protected record Element(String id, int x, int y, int w, int h, List<Component> tooltip,
            @Nullable CoinRequest left, @Nullable CoinRequest right, @Nullable CoinRequest shift, Painter painter) {

        boolean active() {
            return left != null || right != null || shift != null;
        }

        boolean contains(double mx, double my) {
            return BenchTheme.inside(mx, my, x, y, w, h);
        }
    }

    /** Pour les captures : l'élément dont on montre la bulle, comme si la souris était dessus. */
    @Nullable
    private String pinnedHover;

    protected CoinAccountScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // Le cadre et le panneau : 302 x 232, ce qui tient dans la plus petite interface automatique (320 x 240).
        imageWidth = PANEL_X + PANEL_W;
        imageHeight = HEIGHT;
    }

    /** Envoie la demande au serveur, dans le paquet de l'écran. */
    protected abstract void send(CoinRequest request);

    @Override
    protected void init() {
        super.init();
        titleLabelX = PAD;
        titleLabelY = 6;
        inventoryLabelX = PAD;
        inventoryLabelY = INVENTORY_LABEL_Y;
    }

    /** La coupure de la ligne {@code row} : la plus grande en haut. */
    static Coin coinOfRow(int row) {
        return Coin.values()[Coin.values().length - 1 - row];
    }

    private int rowY(int row) {
        return topPos + ROWS_Y + row * ROW_H;
    }

    private int rowButtonX(int index, int count) {
        int first = leftPos + MAIN_W - PAD - 2 - count * ROW_BUTTON_W - (count - 1) * ROW_BUTTON_GAP;
        return first + index * (ROW_BUTTON_W + ROW_BUTTON_GAP);
    }

    /** Le contenu tel que le client le connaît. */
    protected EnumMap<Coin, Long> counts() {
        EnumMap<Coin, Long> counts = new EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            long n = menu.count(coin);
            if (n > 0) {
                counts.put(coin, n);
            }
        }
        return counts;
    }

    /** Y a-t-il de quoi regrouper (selon les valeurs vivantes) ? Le serveur a le dernier mot. */
    private boolean canGroup(EnumMap<Coin, Long> counts) {
        return !CoinMath.group(counts, Coin::value).equals(counts);
    }

    private static Component unit(Coin coin, long n) {
        return Component.translatable(coin.unitKey(n));
    }

    /** Une ligne de bulle : blanche si possible, grise sinon, suivie de la raison en rouge. */
    private static void line(List<Component> out, Component text, @Nullable String why) {
        out.add(text.copy().withStyle(why == null ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        if (why != null) {
            out.add(Component.translatable(GUI + "unavailable", Component.translatable(GUI + "why." + why))
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** Tous les éléments de l'écran, à leur place : la même liste sert au rendu et aux clics. */
    protected List<Element> elements() {
        List<Element> out = new ArrayList<>();
        EnumMap<Coin, Long> counts = counts();
        int[] amounts = CoinRequest.amounts();
        Component where = Component.translatable(GUI + (menu.usesPurses() ? "row.safe" : "row.purse"))
                .withStyle(ChatFormatting.DARK_GRAY);
        boolean anyToDeposit = false;
        for (int row = 0; row < Coin.values().length; row++) {
            Coin coin = coinOfRow(row);
            long count = counts.getOrDefault(coin, 0L);
            long carried = menu.carried(coin);
            long room = Math.max(0L, CoinStore.MAX_PER_COIN - count);
            String depositWhy = carried <= 0L ? "none_carried" : room <= 0L ? "store_full" : null;
            anyToDeposit |= depositWhy == null;
            for (int i = 0; i < amounts.length; i++) {
                int amount = amounts[i];
                String withdrawWhy = count >= amount ? null : "not_enough";
                List<Component> tip = new ArrayList<>();
                line(tip, Component.translatable(GUI + "left_deposit", amount, unit(coin, amount)), depositWhy);
                line(tip, Component.translatable(GUI + "right_withdraw", amount, unit(coin, amount)), withdrawWhy);
                tip.add(where);
                out.add(new Element("row." + coin.key() + "." + amount, rowButtonX(i, amounts.length),
                        rowY(row) + (ROW_H - ROW_BUTTON_H) / 2, ROW_BUTTON_W, ROW_BUTTON_H, tip,
                        depositWhy == null ? CoinRequest.deposit(coin, amount) : null,
                        withdrawWhy == null ? CoinRequest.withdraw(coin, amount) : null, null,
                        label(String.valueOf(amount), false)));
            }
        }
        int footY = topPos + FOOT_Y;
        out.add(new Element("deposit_all", leftPos + PAD, footY, FOOT_W, FOOT_H,
                List.of(Component.translatable(GUI + "deposit_all.tooltip." + (menu.usesPurses() ? "safe" : "purse"))),
                anyToDeposit ? CoinRequest.depositAll() : null, null, null,
                label(Component.translatable(GUI + "deposit_all").getString(), true)));
        boolean group = canGroup(counts);
        List<Component> groupTip = new ArrayList<>();
        line(groupTip, Component.translatable(GUI + "group.tooltip"), group ? null : "nothing");
        out.add(new Element("group", leftPos + PAD + FOOT_W + 4, footY, FOOT_W, FOOT_H, groupTip,
                group ? CoinRequest.groupAll() : null, null, null,
                label(Component.translatable(GUI + "group").getString(), false)));
        // Le panneau « Change » : une ligne par paire voisine, la plus grande en haut.
        for (int row = 0; row < Coin.values().length - 1; row++) {
            Coin upper = coinOfRow(row);
            Coin lower = Coin.values()[upper.ordinal() - 1];
            int y = topPos + CHANGE_Y + row * CHANGE_ROW;
            boolean rate = CoinMath.atPar(upper, Coin::value);
            String breakWhy = !rate ? "rate"
                    : CoinMath.maxBreaks(counts, upper, Coin::value, CoinStore.MAX_PER_COIN) > 0L ? null
                    : counts.getOrDefault(upper, 0L) <= 0L ? "not_enough" : "store_full";
            String groupWhy = !rate ? "rate"
                    : CoinMath.maxGroups(counts, upper, Coin::value, CoinStore.MAX_PER_COIN) > 0L ? null
                    : counts.getOrDefault(lower, 0L) < 10L ? "not_enough" : "store_full";
            List<Component> breakTip = new ArrayList<>();
            line(breakTip, Component.translatable(GUI + "break", unit(upper, 1), unit(lower, 10)), breakWhy);
            breakTip.add(Component.translatable(GUI + "shift_all").withStyle(ChatFormatting.DARK_GRAY));
            out.add(new Element("break." + upper.key(), leftPos + PANEL_X + 5, y, CHANGE_W, CHANGE_H, breakTip,
                    breakWhy == null ? CoinRequest.breakCoin(upper, false) : null, null,
                    breakWhy == null ? CoinRequest.breakCoin(upper, true) : null, exchange(upper, 1, lower, 10)));
            List<Component> pairTip = new ArrayList<>();
            line(pairTip, Component.translatable(GUI + "group_pair", unit(lower, 10), unit(upper, 1)), groupWhy);
            pairTip.add(Component.translatable(GUI + "shift_all").withStyle(ChatFormatting.DARK_GRAY));
            out.add(new Element("group." + upper.key(), leftPos + PANEL_X + 5 + CHANGE_W + 2, y, CHANGE_W, CHANGE_H,
                    pairTip, groupWhy == null ? CoinRequest.groupPair(upper, false) : null, null,
                    groupWhy == null ? CoinRequest.groupPair(upper, true) : null, exchange(lower, 10, upper, 1)));
        }
        return out;
    }

    /** Un bouton de change : « 1 [or] → 10 [argent] », pièces en petit. */
    private Painter exchange(Coin from, int fromCount, Coin to, int toCount) {
        return (g, e, hover) -> {
            BenchTheme.button(g, font, e.x(), e.y(), e.w(), e.h(), "", hover && e.active(), e.active(), false);
            int color = !e.active() ? BenchTheme.TEXT_DISABLED : hover ? BenchTheme.BRASS_LIGHT : BenchTheme.TEXT;
            String a = String.valueOf(fromCount);
            String arrow = "→";
            String b = String.valueOf(toCount);
            int icon = 12;
            int width = font.width(a) + 1 + icon + 2 + font.width(arrow) + 2 + font.width(b) + 1 + icon;
            int x = e.x() + (e.w() - width) / 2;
            int ty = e.y() + (e.h() - 8) / 2 + 1;
            int iy = e.y() + (e.h() - icon) / 2;
            g.drawString(font, a, x, ty, color, false);
            x += font.width(a) + 1;
            BenchTheme.item(g, font, new ItemStack(from.item()), x, iy, 0.75F, false);
            x += icon + 2;
            g.drawString(font, arrow, x, ty, color, false);
            x += font.width(arrow) + 2;
            g.drawString(font, b, x, ty, color, false);
            x += font.width(b) + 1;
            BenchTheme.item(g, font, new ItemStack(to.item()), x, iy, 0.75F, false);
        };
    }

    /** Un bouton à texte. */
    protected Painter label(String text, boolean primary) {
        return (g, e, hover) -> BenchTheme.button(g, font, e.x(), e.y(), e.w(), e.h(), text, hover && e.active(),
                e.active(), primary);
    }

    private Optional<Element> elementAt(double mx, double my) {
        for (Element element : elements()) {
            if (element.contains(mx, my)) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    /**
     * Le centre de l'élément {@code id} en coordonnées d'écran, pour l'essai de bout en bout qui clique comme un
     * joueur ; vide s'il n'existe pas.
     */
    public Optional<int[]> elementCenter(String id) {
        for (Element element : elements()) {
            if (element.id().equals(id)) {
                return Optional.of(new int[] {element.x() + element.w() / 2, element.y() + element.h() / 2});
            }
        }
        return Optional.empty();
    }

    /** Pour les captures : montre la bulle de l'élément {@code id} (nul : la souris décide). */
    public void pinHover(@Nullable String id) {
        this.pinnedHover = id;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Optional<Element> hit = elementAt(mx, my);
        if (hit.isPresent() && (button == 0 || button == 1)) {
            Element element = hit.get();
            CoinRequest request;
            if (button == 1) {
                request = element.right();
            } else if (Screen.hasShiftDown() && element.shift() != null) {
                request = element.shift();
            } else {
                request = element.left();
            }
            if (request != null) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                send(request);
            }
            // Un bouton, même grisé, prend le clic : il ne tombe pas dans l'écran (ni sur une case, ni hors du cadre).
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        int hx = mx;
        int hy = my;
        if (pinnedHover != null) {
            Optional<int[]> center = elementCenter(pinnedHover);
            if (center.isPresent()) {
                hx = center.get()[0];
                hy = center.get()[1];
            }
        }
        renderBackground(g);
        super.render(g, hx, hy, partialTick);
        Optional<Element> hover = elementAt(hx, hy);
        if (hover.isPresent() && !hover.get().tooltip().isEmpty()) {
            g.renderComponentTooltip(font, hover.get().tooltip(), hx, hy);
        } else {
            renderTooltip(g, hx, hy);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        BenchTheme.plate(g, leftPos, topPos, MAIN_W, imageHeight);
        BenchTheme.rivets(g, leftPos, topPos, MAIN_W, imageHeight);
        BenchTheme.plate(g, leftPos + PANEL_X, topPos, PANEL_W, PANEL_H);
        BenchTheme.rivets(g, leftPos + PANEL_X, topPos, PANEL_W, PANEL_H);
        BenchTheme.rule(g, leftPos + PANEL_X + 5, topPos + 15, PANEL_W - 10);
        BenchTheme.rule(g, leftPos + PAD, topPos + 15, MAIN_W - 2 * PAD);
        BenchTheme.well(g, leftPos + PAD, topPos + ROWS_Y - 1, MAIN_W - 2 * PAD, Coin.values().length * ROW_H + 1);
        int textRight = rowButtonX(0, CoinRequest.amounts().length) - 2;
        for (int row = 0; row < Coin.values().length; row++) {
            Coin coin = coinOfRow(row);
            long count = menu.count(coin);
            BenchTheme.item(g, font, new ItemStack(coin.item()), leftPos + PAD + 2, rowY(row) + 1, 1.0F, false);
            String text = count + " " + Component.translatable(coin.unitKey(count)).getString();
            int x = leftPos + PAD + 21;
            fitted(g, text, x, rowY(row) + 5, textRight - x, count > 0 ? BenchTheme.TEXT : BenchTheme.TEXT_DIM);
        }
        for (Slot slot : menu.slots) {
            BenchTheme.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        for (Element element : elements()) {
            element.painter().paint(g, element, element.contains(mx, my));
        }
    }

    /** Un texte qui rétrécit pour tenir dans {@code width} pixels. */
    protected void fitted(GuiGraphics g, String text, int x, int y, int width, int color) {
        float scale = Math.min(1.0F, width / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - scale) * 4.0F, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, titleLabelX, titleLabelY, BenchTheme.TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, BenchTheme.TEXT_DIM, false);
        g.drawString(font, Component.translatable(GUI + "change"), PANEL_X + 6, 6, BenchTheme.TEXT, false);
        // Le total le plus long (cinq coupures, nombres à plusieurs chiffres) déborderait du cadre : il rétrécit.
        Component total = Component.translatable(GUI + "total", CoinMath.format(menu.total()));
        float scale = Math.min(1.0F, (MAIN_W - 2 * PAD) / (float) font.width(total));
        g.pose().pushPose();
        g.pose().translate(PAD, TOTAL_Y + (1.0F - scale) * 4.0F, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, total, 0, 0, BenchTheme.BRASS_LIGHT, false);
        g.pose().popPose();
    }
}
