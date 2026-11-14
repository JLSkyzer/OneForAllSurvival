package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.bench.client.ScrollModel;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.stall.MarketStallBlockEntity;
import dev.oneforallsurvival.economy.stall.StallActionPacket;
import dev.oneforallsurvival.economy.stall.StallBuyback;
import dev.oneforallsurvival.economy.stall.StallLedger;
import dev.oneforallsurvival.economy.stall.StallLedgerPacket;
import dev.oneforallsurvival.economy.stall.StallOffer;
import dev.oneforallsurvival.economy.stall.StallOwnerMenu;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/**
 * La gestion d'un étal, au décor de l'établi : quatre onglets.
 *
 * <ul>
 *   <li><b>Stock</b> : les 27 cases du stock et l'inventaire.
 *   <li><b>Offres</b> : les cases d'offre de vente ou de rachat (bascule « Ventes / Rachats »), une rangée de neuf
 *       (une seconde page au-delà de neuf). On choisit une case, on prend l'objet sur le curseur, on règle le lot, le
 *       prix (en cuivres, aperçu en pièces) et, pour un rachat, le nombre de lots ; « Définir » envoie
 *       {@link StallActionPacket} : le serveur copie la pile de <i>son</i> curseur. L'objet reste au joueur.
 *   <li><b>Caisse</b> : bouton de menu du jeu, le serveur ouvre la caisse.
 *   <li><b>Registre</b> : une page de 20 lignes demandée au serveur (envoyée à ce seul joueur), qui défile.
 * </ul>
 *
 * <p>Offres et Registre sont des vues de ce menu, côté client : les cases inutiles y sont cachées
 * ({@link StallOwnerMenu#setView}). Les textes dont la longueur dépend du contenu sont mesurés et rétrécis pour tenir.
 */
public final class StallOwnerScreen extends AbstractContainerScreen<StallOwnerMenu> {

    private static final String GUI = "gui.oneforallsurvival.stall.";

    static final int MAIN_W = 176;
    static final int HEIGHT = StallOwnerMenu.HOTBAR_Y + 16 + 8;
    static final int PAD = 8;
    static final int INNER_W = MAIN_W - 2 * PAD;
    static final int TAB_Y = 18;
    static final int TAB_H = 14;
    static final int TAB_GAP = 2;
    static final int TAB_W = (INNER_W - 3 * TAB_GAP) / 4;

    // Vue Offres.
    static final int SUB_Y = 36;
    static final int SUB_H = 12;
    static final int SUB_W = 62;
    static final int PAGE_W = INNER_W - 2 * SUB_W - 2 * TAB_GAP;
    static final int CELLS_Y = 50;
    static final int CELLS_PER_PAGE = 9;
    static final int FIELDS_Y = 70;
    static final int FIELD_H = 12;
    static final int LOT_W = 24;
    static final int LOTS_W = 34;
    static final int ACTIONS_Y = 84;
    static final int ACTION_H = 12;
    static final int CLEAR_W = 40;
    static final int DEFINE_W = 40;
    static final int PREVIEW_W = INNER_W - DEFINE_W - CLEAR_W - 2 * TAB_GAP;

    // Vue Registre.
    static final int LEDGER_NAV_Y = 36;
    static final int LEDGER_Y = 52;
    static final int LINE_H = 16;
    static final int LEDGER_ROWS = (HEIGHT - PAD - LEDGER_Y) / LINE_H;
    static final int SCROLL_W = 5;
    /** Échelle de toute une ligne du registre : l'heure, le texte et le montant ensemble. */
    static final float LINE_SCALE = 0.75F;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    /** Un onglet : son nom et sa vue ; la caisse est un bouton de menu (-1 : pas une vue). */
    private record Tab(String key, int view) {}

    private static final List<Tab> TABS = List.of(
            new Tab("stock", StallOwnerMenu.VIEW_STOCK),
            new Tab("offers", StallOwnerMenu.VIEW_OFFERS),
            new Tab("cash", -1),
            new Tab("ledger", StallOwnerMenu.VIEW_LEDGER));

    private boolean buybackKind;
    private int page;
    private int selected;
    private EditBox lotBox;
    private EditBox priceBox;
    private EditBox lotsBox;
    private int ledgerPage;
    private final ScrollModel ledgerScroll = new ScrollModel();

    public StallOwnerScreen(StallOwnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = MAIN_W;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = PAD;
        titleLabelY = 6;
        inventoryLabelX = PAD;
        inventoryLabelY = StallOwnerMenu.INVENTORY_Y - 10;
        String lot = lotBox == null ? "1" : lotBox.getValue();
        String price = priceBox == null ? "" : priceBox.getValue();
        String lots = lotsBox == null ? "1" : lotsBox.getValue();
        lotBox = field(2, lot, GUI + "offers.lot");
        priceBox = field(String.valueOf(EconomyConfig.invoiceMaxAmount()).length(), price, GUI + "offers.price");
        lotsBox = field(4, lots, GUI + "offers.lots");
        layoutFields();
        applyView();
    }

    private EditBox field(int maxLength, String value, String hintKey) {
        EditBox box = new EditBox(font, 0, 0, 10, FIELD_H - 4, Component.translatable(hintKey));
        box.setBordered(false);
        box.setTextColor(BenchTheme.TEXT);
        box.setMaxLength(maxLength);
        box.setFilter(text -> text.chars().allMatch(Character::isDigit));
        box.setValue(value);
        box.setHint(Component.translatable(hintKey));
        addRenderableWidget(box);
        return box;
    }

    /** Place les champs selon le genre d'offre : la place du nombre de lots revient au prix pour une vente. */
    private void layoutFields() {
        int x = leftPos + PAD;
        int y = topPos + FIELDS_Y + 2;
        int priceW = INNER_W - LOT_W - TAB_GAP - (buybackKind ? LOTS_W + TAB_GAP : 0);
        place(lotBox, x, y, LOT_W);
        place(priceBox, x + LOT_W + TAB_GAP, y, priceW);
        place(lotsBox, x + LOT_W + TAB_GAP + priceW + TAB_GAP, y, LOTS_W);
    }

    private static void place(EditBox box, int x, int y, int w) {
        box.setX(x + 3);
        box.setY(y);
        box.setWidth(w - 6);
    }

    private void applyView() {
        boolean offers = menu.view() == StallOwnerMenu.VIEW_OFFERS;
        lotBox.visible = offers;
        priceBox.visible = offers;
        lotsBox.visible = offers && buybackKind;
        if (!offers) {
            setFocused(null);
        }
    }

    private void setView(int view) {
        menu.setView(view);
        if (view == StallOwnerMenu.VIEW_LEDGER) {
            ledgerPage = 0;
            requestLedger();
        }
        if (view == StallOwnerMenu.VIEW_OFFERS) {
            loadSelection();
        }
        applyView();
    }

    private void requestLedger() {
        StallLedgerCache.clear();
        ledgerScroll.scrollTo(0);
        BenchNetwork.CHANNEL.sendToServer(StallActionPacket.requestLedger(menu.pos(), ledgerPage));
    }

    @Nullable
    private MarketStallBlockEntity stall() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(menu.pos()) instanceof MarketStallBlockEntity be ? be : null;
    }

    private int slots() {
        return MarketStallBlockEntity.offerSlots();
    }

    private int pages() {
        return (slots() + CELLS_PER_PAGE - 1) / CELLS_PER_PAGE;
    }

    /** Ce que montre la case {@code index} : modèle, lot, prix, lots restants (-1 pour une vente) ; vide si libre. */
    private record Cell(ItemStack template, int lot, long price, int lotsLeft) {}

    private Optional<Cell> cell(int index) {
        MarketStallBlockEntity be = stall();
        if (be == null || index < 0 || index >= slots()) {
            return Optional.empty();
        }
        if (buybackKind) {
            StallBuyback b = be.buybacks().get(index);
            return b == null ? Optional.empty() : Optional.of(new Cell(b.template(), b.lot(), b.price(), b.lotsLeft()));
        }
        StallOffer o = be.offers().get(index);
        return o == null ? Optional.empty() : Optional.of(new Cell(o.template(), o.lot(), o.price(), -1));
    }

    /** Remplit les champs avec l'offre choisie, s'il y en a une. */
    private void loadSelection() {
        cell(selected).ifPresent(c -> {
            lotBox.setValue(String.valueOf(c.lot()));
            priceBox.setValue(String.valueOf(c.price()));
            if (c.lotsLeft() >= 0) {
                lotsBox.setValue(String.valueOf(Math.max(1, c.lotsLeft())));
            }
        });
    }

    private static long parse(EditBox box) {
        try {
            return box.getValue().isEmpty() ? -1L : Long.parseLong(box.getValue());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** Les valeurs saisies respectent-elles les bornes, et un objet est-il sur le curseur ? Le serveur revérifie tout. */
    private boolean canDefine() {
        long lot = parse(lotBox);
        long price = parse(priceBox);
        long lots = parse(lotsBox);
        boolean values = lot >= 1 && lot <= StallOffer.MAX_LOT && price >= 1 && price <= EconomyConfig.invoiceMaxAmount()
                && (!buybackKind || (lots >= 1 && lots <= StallBuyback.MAX_LOTS));
        return values && !menu.getCarried().isEmpty();
    }

    private void define() {
        int lot = (int) parse(lotBox);
        long price = parse(priceBox);
        BenchNetwork.CHANNEL.sendToServer(buybackKind
                ? StallActionPacket.setBuyback(menu.pos(), selected, lot, price, (int) parse(lotsBox))
                : StallActionPacket.setOffer(menu.pos(), selected, lot, price));
    }

    private void clear() {
        BenchNetwork.CHANNEL.sendToServer(buybackKind
                ? StallActionPacket.clearBuyback(menu.pos(), selected)
                : StallActionPacket.clearOffer(menu.pos(), selected));
    }

    // ---------------------------------------------------------------------------------------------
    // Géométrie
    // ---------------------------------------------------------------------------------------------

    private int tabX(int index) {
        return leftPos + PAD + index * (TAB_W + TAB_GAP);
    }

    private Optional<Integer> tabAt(double mx, double my) {
        for (int i = 0; i < TABS.size(); i++) {
            if (BenchTheme.inside(mx, my, tabX(i), topPos + TAB_Y, TAB_W, TAB_H)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private int cellX(int col) {
        return leftPos + StallOwnerMenu.STOCK_X - 1 + col * 18;
    }

    /** L'indice de la case d'offre sous la souris, ou -1. */
    private int cellAt(double mx, double my) {
        for (int col = 0; col < CELLS_PER_PAGE; col++) {
            int index = page * CELLS_PER_PAGE + col;
            if (index < slots() && BenchTheme.inside(mx, my, cellX(col), topPos + CELLS_Y, 18, 18)) {
                return index;
            }
        }
        return -1;
    }

    private int defineX() {
        return leftPos + PAD + PREVIEW_W + TAB_GAP;
    }

    private int clearX() {
        return defineX() + DEFINE_W + TAB_GAP;
    }

    // ---------------------------------------------------------------------------------------------
    // Souris et clavier
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Optional<Integer> hit = tabAt(mx, my);
        if (hit.isPresent() && button == 0) {
            Tab tab = TABS.get(hit.get());
            click();
            if (tab.view() < 0) {
                if (Minecraft.getInstance().gameMode != null) {
                    Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, StallOwnerMenu.BUTTON_CASH);
                }
            } else if (tab.view() != menu.view()) {
                setView(tab.view());
            }
            return true;
        }
        if (button == 0 && menu.view() == StallOwnerMenu.VIEW_OFFERS && offersClicked(mx, my)) {
            return true;
        }
        if (button == 0 && menu.view() == StallOwnerMenu.VIEW_LEDGER && ledgerClicked(mx, my)) {
            return true;
        }
        boolean onField = (lotBox.visible && lotBox.isMouseOver(mx, my)) || (priceBox.visible && priceBox.isMouseOver(mx, my))
                || (lotsBox.visible && lotsBox.isMouseOver(mx, my));
        if (!onField) {
            setFocused(null);
            lotBox.setFocused(false);
            priceBox.setFocused(false);
            lotsBox.setFocused(false);
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean offersClicked(double mx, double my) {
        int y = topPos + SUB_Y;
        for (int k = 0; k < 2; k++) {
            int x = leftPos + PAD + k * (SUB_W + TAB_GAP);
            if (BenchTheme.inside(mx, my, x, y, SUB_W, SUB_H)) {
                if (buybackKind != (k == 1)) {
                    buybackKind = k == 1;
                    click();
                    layoutFields();
                    applyView();
                    loadSelection();
                }
                return true;
            }
        }
        if (pages() > 1 && BenchTheme.inside(mx, my, leftPos + PAD + 2 * (SUB_W + TAB_GAP), y, PAGE_W, SUB_H)) {
            page = (page + 1) % pages();
            click();
            return true;
        }
        int index = cellAt(mx, my);
        if (index >= 0) {
            selected = index;
            click();
            loadSelection();
            return true;
        }
        if (BenchTheme.inside(mx, my, defineX(), topPos + ACTIONS_Y, DEFINE_W, ACTION_H)) {
            if (canDefine()) {
                click();
                define();
            }
            return true;
        }
        if (BenchTheme.inside(mx, my, clearX(), topPos + ACTIONS_Y, CLEAR_W, ACTION_H)) {
            if (cell(selected).isPresent()) {
                click();
                clear();
            }
            return true;
        }
        return false;
    }

    private boolean ledgerClicked(double mx, double my) {
        int y = topPos + LEDGER_NAV_Y;
        StallLedgerPacket shown = shownLedger();
        int pages = shown == null ? 1 : shown.pages();
        if (BenchTheme.inside(mx, my, leftPos + PAD, y, 16, SUB_H)) {
            if (ledgerPage > 0) {
                ledgerPage--;
                click();
                requestLedger();
            }
            return true;
        }
        if (BenchTheme.inside(mx, my, leftPos + MAIN_W - PAD - 16, y, 16, SUB_H)) {
            if (ledgerPage + 1 < pages) {
                ledgerPage++;
                click();
                requestLedger();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (menu.view() == StallOwnerMenu.VIEW_LEDGER) {
            ledgerScroll.scrollBy(-delta);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    /** Un champ qui a le focus garde les touches (la touche de l'inventaire ne ferme pas l'écran pendant la saisie). */
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key != GLFW.GLFW_KEY_ESCAPE && getFocused() instanceof EditBox box && box.visible && box.isFocused()) {
            return box.keyPressed(key, scanCode, modifiers) || box.canConsumeInput();
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        lotBox.tick();
        priceBox.tick();
        lotsBox.tick();
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Nullable
    private StallLedgerPacket shownLedger() {
        StallLedgerPacket last = StallLedgerCache.last();
        return last != null && last.pos().equals(menu.pos()) ? last : null;
    }

    // ---------------------------------------------------------------------------------------------
    // Dessin
    // ---------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        super.render(g, mx, my, partialTick);
        if (menu.view() == StallOwnerMenu.VIEW_OFFERS && menu.getCarried().isEmpty()) {
            int index = cellAt(mx, my);
            if (index >= 0) {
                g.renderComponentTooltip(font, cellTooltip(index), mx, my);
                return;
            }
            if (BenchTheme.inside(mx, my, defineX(), topPos + ACTIONS_Y, DEFINE_W, ACTION_H)) {
                List<FormattedCharSequence> help = font.split(Component.translatable(GUI + "offers.help"), 200);
                g.renderTooltip(font, help, mx, my);
                return;
            }
        }
        renderTooltip(g, mx, my);
    }

    private List<Component> cellTooltip(int index) {
        List<Component> lines = new ArrayList<>();
        Optional<Cell> c = cell(index);
        if (c.isEmpty()) {
            lines.add(Component.translatable(GUI + "offers.empty"));
            return lines;
        }
        lines.add(c.get().template().getHoverName());
        lines.add(Component.translatable(GUI + "offers.lot_line", c.get().lot()));
        lines.add(Component.translatable(GUI + "offers.price_line", CoinMath.format(c.get().price())));
        if (c.get().lotsLeft() >= 0) {
            lines.add(Component.translatable(GUI + "offers.left_line", c.get().lotsLeft()));
        }
        return lines;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        BenchTheme.plate(g, leftPos, topPos, MAIN_W, imageHeight);
        BenchTheme.rivets(g, leftPos, topPos, MAIN_W, imageHeight);
        BenchTheme.rule(g, leftPos + PAD, topPos + 15, INNER_W);
        if (menu.view() == StallOwnerMenu.VIEW_STOCK) {
            BenchTheme.well(g, leftPos + StallOwnerMenu.STOCK_X - 2, topPos + StallOwnerMenu.STOCK_Y - 2, 9 * 18 + 2, 3 * 18 + 2);
        }
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                BenchTheme.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
            }
        }
        for (int i = 0; i < TABS.size(); i++) {
            Tab tab = TABS.get(i);
            boolean current = tab.view() == menu.view();
            boolean hover = BenchTheme.inside(mx, my, tabX(i), topPos + TAB_Y, TAB_W, TAB_H);
            StallShopScreen.fittedButton(g, font, tabX(i), topPos + TAB_Y, TAB_W, TAB_H, Component.translatable(GUI + "tab." + tab.key()).getString(),
                    hover && !current, true, current);
        }
        if (menu.view() == StallOwnerMenu.VIEW_OFFERS) {
            drawOffers(g, mx, my);
        } else if (menu.view() == StallOwnerMenu.VIEW_LEDGER) {
            drawLedger(g, mx, my);
        }
    }

    private void drawOffers(GuiGraphics g, int mx, int my) {
        int y = topPos + SUB_Y;
        for (int k = 0; k < 2; k++) {
            int x = leftPos + PAD + k * (SUB_W + TAB_GAP);
            boolean current = (k == 1) == buybackKind;
            StallShopScreen.fittedButton(g, font, x, y, SUB_W, SUB_H, Component.translatable(GUI + (k == 0 ? "offers.sales" : "offers.buybacks"))
                    .getString(), !current && BenchTheme.inside(mx, my, x, y, SUB_W, SUB_H), true, current);
        }
        if (pages() > 1) {
            int x = leftPos + PAD + 2 * (SUB_W + TAB_GAP);
            StallShopScreen.fittedButton(g, font, x, y, PAGE_W, SUB_H, Component.translatable(GUI + "offers.page", page + 1, pages()).getString(),
                    BenchTheme.inside(mx, my, x, y, PAGE_W, SUB_H), true, false);
        }
        for (int col = 0; col < CELLS_PER_PAGE; col++) {
            int index = page * CELLS_PER_PAGE + col;
            if (index >= slots()) {
                break;
            }
            int x = cellX(col);
            BenchTheme.slot(g, x, topPos + CELLS_Y);
            if (index == selected) {
                g.fill(x + 1, topPos + CELLS_Y + 1, x + 17, topPos + CELLS_Y + 17, BenchTheme.ROW_SELECTED);
            }
            Optional<Cell> c = cell(index);
            if (c.isPresent()) {
                ItemStack shown = c.get().template().copyWithCount(c.get().lot());
                g.renderItem(shown, x + 1, topPos + CELLS_Y + 1);
                g.renderItemDecorations(font, shown, x + 1, topPos + CELLS_Y + 1);
                if (c.get().lotsLeft() == 0) {
                    g.fill(x + 1, topPos + CELLS_Y + 1, x + 17, topPos + CELLS_Y + 17, 0x90000000);
                }
            }
        }
        // Champs : un creux sous chacun.
        for (EditBox box : List.of(lotBox, priceBox, lotsBox)) {
            if (box.visible) {
                BenchTheme.well(g, box.getX() - 3, topPos + FIELDS_Y, box.getWidth() + 6, FIELD_H);
            }
        }
        long price = parse(priceBox);
        if (price >= 1) {
            PriceLabel.draw(g, font, price, leftPos + PAD, topPos + ACTIONS_Y + 2, PREVIEW_W,
                    price <= EconomyConfig.invoiceMaxAmount() ? BenchTheme.TEXT : BenchTheme.TEXT_BAD);
        }
        boolean define = canDefine();
        StallShopScreen.fittedButton(g, font, defineX(), topPos + ACTIONS_Y, DEFINE_W, ACTION_H,
                Component.translatable(GUI + "offers.define").getString(),
                define && BenchTheme.inside(mx, my, defineX(), topPos + ACTIONS_Y, DEFINE_W, ACTION_H), define, true);
        boolean clear = cell(selected).isPresent();
        StallShopScreen.fittedButton(g, font, clearX(), topPos + ACTIONS_Y, CLEAR_W, ACTION_H,
                Component.translatable(GUI + "offers.clear").getString(),
                clear && BenchTheme.inside(mx, my, clearX(), topPos + ACTIONS_Y, CLEAR_W, ACTION_H), clear, false);
    }

    private void drawLedger(GuiGraphics g, int mx, int my) {
        int navY = topPos + LEDGER_NAV_Y;
        StallLedgerPacket shown = shownLedger();
        int pages = shown == null ? 1 : shown.pages();
        BenchTheme.button(g, font, leftPos + PAD, navY, 16, SUB_H, "<", BenchTheme.inside(mx, my, leftPos + PAD, navY, 16, SUB_H),
                ledgerPage > 0, false);
        int rightX = leftPos + MAIN_W - PAD - 16;
        BenchTheme.button(g, font, rightX, navY, 16, SUB_H, ">", BenchTheme.inside(mx, my, rightX, navY, 16, SUB_H),
                ledgerPage + 1 < pages, false);
        String label = BenchTheme.ellipsize(font, Component.translatable(GUI + "ledger.page", ledgerPage + 1, pages).getString(),
                INNER_W - 40);
        g.drawString(font, label, leftPos + (MAIN_W - font.width(label)) / 2, navY + 2, BenchTheme.TEXT, false);

        int top = topPos + LEDGER_Y;
        int listW = INNER_W - SCROLL_W - 1;
        int listH = LEDGER_ROWS * LINE_H;
        BenchTheme.well(g, leftPos + PAD - 1, top - 1, listW + 2, listH + 2);
        List<StallLedger.Line> lines = shown == null ? List.of() : shown.lines();
        ledgerScroll.setSize(lines.size(), LEDGER_ROWS);
        BenchTheme.scrollBar(g, leftPos + PAD + listW + 1, top - 1, SCROLL_W, listH + 2, ledgerScroll, false);
        if (lines.isEmpty()) {
            String text = BenchTheme.ellipsize(font, Component.translatable(GUI + (shown == null ? "ledger.waiting" : "ledger.empty"))
                    .getString(), listW - 8);
            g.drawString(font, text, leftPos + PAD + (listW - font.width(text)) / 2, top + listH / 2 - 4, BenchTheme.TEXT_DIM, false);
            return;
        }
        for (int v = 0; v < LEDGER_ROWS; v++) {
            int i = ledgerScroll.first() + v;
            if (i >= lines.size()) {
                break;
            }
            drawLine(g, lines.get(i), leftPos + PAD + 2, top + v * LINE_H + 1, listW - 4);
        }
    }

    /**
     * Une entrée sur deux lignes, toutes à la même échelle {@link #LINE_SCALE} : en haut le genre d'opération (jamais
     * tronqué), puis le nombre et l'objet (tronqués en premier, s'il le faut), le montant en pièces à droite ; en
     * dessous l'heure (atténuée) et l'acteur.
     */
    private void drawLine(GuiGraphics g, StallLedger.Line line, int x, int y, int width) {
        int priceW = 0;
        if (line.amount() != 0L) {
            int natural = PriceLabel.width(font, Math.abs(line.amount()));
            int max = Math.min(Math.round(natural * LINE_SCALE), width * 3 / 5);
            priceW = PriceLabel.draw(g, font, Math.abs(line.amount()), x + width - max, y + 1, max,
                    line.amount() < 0L || line.kind() == StallLedger.Kind.CASH_OUT ? BenchTheme.TEXT_BAD : BenchTheme.TEXT_OK);
        }
        String kind = Component.translatable(GUI + "ledger.kind." + line.kind().name().toLowerCase(Locale.ROOT)).getString();
        drawScaled(g, kind, x, y + 1, LINE_SCALE, BenchTheme.TEXT);
        if (!line.itemId().isEmpty()) {
            String what = (line.kind() == StallLedger.Kind.STAFF && line.count() > 0 ? "+" : "") + line.count() + " x "
                    + itemName(line.itemId());
            int kindW = Math.round(font.width(kind + " ") * LINE_SCALE);
            int room = Math.max(8, width - kindW - priceW - 4);
            drawScaled(g, BenchTheme.ellipsize(font, what, Math.round(room / LINE_SCALE)), x + kindW, y + 1, LINE_SCALE,
                    BenchTheme.TEXT);
        }
        String time = TIME.format(Instant.ofEpochMilli(line.epochMillis()));
        int timeW = Math.round(font.width(time + " ") * LINE_SCALE);
        drawScaled(g, time, x, y + LINE_H / 2 + 1, LINE_SCALE, BenchTheme.TEXT_DIM);
        drawScaled(g, BenchTheme.ellipsize(font, line.actorName(), Math.round((width - timeW) / LINE_SCALE)), x + timeW,
                y + LINE_H / 2 + 1, LINE_SCALE, BenchTheme.TEXT_DIM);
    }

    private void drawScaled(GuiGraphics g, String text, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static String itemName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        return item == null ? id : item.getDescription().getString();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        float scale = Math.min(1.0F, INNER_W / (float) Math.max(1, font.width(title.getString())));
        g.pose().pushPose();
        g.pose().translate(titleLabelX, titleLabelY + (1.0F - scale) * 4.0F, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, title.getString(), 0, 0, BenchTheme.TEXT, false);
        g.pose().popPose();
        if (menu.view() != StallOwnerMenu.VIEW_LEDGER) {
            g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, BenchTheme.TEXT_DIM, false);
        }
    }
}
