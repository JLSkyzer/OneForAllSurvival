package dev.oneforallsurvival.bench.client;

import dev.oneforallsurvival.bench.BenchConfig;
import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.BenchOrders;
import dev.oneforallsurvival.bench.BenchRecipe;
import dev.oneforallsurvival.bench.CancelPacket;
import dev.oneforallsurvival.bench.EngineerBenchMenu;
import dev.oneforallsurvival.bench.OpenStockPacket;
import dev.oneforallsurvival.bench.OrderPacket;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

/**
 * L'écran de l'établi : onglets de mods à gauche, recherche et liste au centre, détail à droite, file
 * en bas. Cette classe tient l'état partagé (catégorie, recherche, sélection, quantité) et distribue
 * l'entrée ; chaque panneau dessine et traite sa zone. Tout le décor passe par {@link BenchTheme}.
 *
 * <p>Le client ne décide de rien : « Crafter » envoie une commande, le serveur la valide et prélève.
 */
public final class EngineerBenchScreen extends AbstractContainerScreen<EngineerBenchMenu> {

    // Dimensions de référence, en pixels d'interface : conçues pour 426 x 240 (1280 x 720 à l'échelle 3).
    private static final int MAX_W = 440;
    private static final int MAX_H = 270;
    private static final int MIN_W = 380;
    private static final int MIN_H = 232;
    private static final int FRAME = 5;
    private static final int TITLE_H = 14;
    private static final int QUEUE_H = 66;
    private static final int TABS_W = 72;
    private static final int DETAIL_W = 168;
    private static final int GAP = 3;
    private static final int SEARCH_H = 14;
    private static final int STOCK_W = 78;

    private final CategoryPane categories = new CategoryPane(this, font());
    private final RecipeListPane list = new RecipeListPane(this, font());
    private final DetailPane detail = new DetailPane(this, font());
    private final QueuePane queue = new QueuePane(this, font());
    private final List<BenchPane> panes = List.of(categories, list, detail, queue);

    private EditBox search;
    private String category = "";
    private BenchRecipe selected;
    private int quantity = 1;

    private List<BenchRecipe> catalogSeen;
    private List<BenchRecipe> sorted = List.of();
    private List<BenchRecipe> shown = List.of();
    private int inventorySignature;
    private final Map<BenchRecipe, Boolean> affordable = new IdentityHashMap<>();
    private BenchPane dragged;

    // Zones de la barre de titre, posées au dessin.
    private int stockX;
    private int stockY;
    private int speedX;
    private int speedW;

    public EngineerBenchScreen(EngineerBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        BenchClientState.invalidateNames();
    }

    private static net.minecraft.client.gui.Font font() {
        return net.minecraft.client.Minecraft.getInstance().font;
    }

    // ================================================================================================
    // Mise en page
    // ================================================================================================

    @Override
    protected void init() {
        imageWidth = Math.max(MIN_W, Math.min(MAX_W, width - 8));
        imageHeight = Math.max(MIN_H, Math.min(MAX_H, height - 4));
        super.init();

        int innerX = leftPos + FRAME;
        int innerW = imageWidth - FRAME * 2;
        int bodyY = topPos + FRAME + TITLE_H + 3;
        int queueY = topPos + imageHeight - FRAME - QUEUE_H;
        int bodyH = queueY - GAP - bodyY;
        int listW = innerW - TABS_W - DETAIL_W - GAP * 2;

        categories.place(innerX, bodyY, TABS_W, bodyH);
        int listX = innerX + TABS_W + GAP;
        list.place(listX, bodyY + SEARCH_H + GAP, listW, bodyH - SEARCH_H - GAP);
        detail.place(listX + listW + GAP, bodyY, DETAIL_W, bodyH);
        queue.place(innerX, queueY, innerW, QUEUE_H);

        String keep = search == null ? "" : search.getValue();
        search = new EditBox(font(), listX + 4, bodyY + 3, listW - 8 - 26, 10, Component.empty());
        search.setBordered(false);
        search.setMaxLength(48);
        search.setValue(keep);
        search.setResponder(text -> refilter(false));
        addWidget(search);
        setInitialFocus(search);
        search.setFocused(true);

        stockX = leftPos + imageWidth - FRAME - STOCK_W;
        stockY = topPos + FRAME;
        catalogSeen = null; // force la reconstruction avec les nouvelles tailles
        syncCatalog();
    }

    // ================================================================================================
    // État partagé, lu et écrit par les panneaux
    // ================================================================================================

    String category() {
        return category;
    }

    public void setCategory(String mod) {
        category = mod == null ? "" : mod;
        refilter(false);
    }

    BenchRecipe selected() {
        return selected;
    }

    public void select(BenchRecipe recipe) {
        selected = recipe;
        quantity = Math.max(1, Math.min(quantity, BenchOrders.MAX_COUNT));
    }

    int quantity() {
        return quantity;
    }

    public void setQuantity(int value) {
        quantity = Math.max(1, Math.min(BenchOrders.MAX_COUNT, value));
    }

    /** Pour la démonstration de développement : écrit dans la barre de recherche comme le ferait le clavier. */
    public void setSearchText(String text) {
        search.setValue(text);
    }

    /** Pour la démonstration de développement : choisit une recette de la liste montrée par son identifiant. */
    public boolean selectById(ResourceLocation id) {
        for (BenchRecipe recipe : sorted) {
            if (recipe.id().equals(id)) {
                selected = recipe;
                list.reveal(recipe);
                return true;
            }
        }
        return false;
    }

    /** Les recettes que la liste montre en ce moment, dans son ordre. */
    public List<BenchRecipe> shownRecipes() {
        return shown;
    }

    int queueSize() {
        return BenchClientState.queue().size();
    }

    int queueMax() {
        return BenchConfig.maxQueue();
    }

    int queueFree() {
        return Math.max(0, queueMax() - queueSize());
    }

    boolean affordable(BenchRecipe recipe) {
        return affordable.computeIfAbsent(recipe, r -> BenchCost.maxUnits(r, inventory(), 1) >= 1);
    }

    private List<ItemStack> inventory() {
        return minecraft.player.getInventory().items;
    }

    double manualSpeed() {
        return BenchConfig.manualSpeed();
    }

    double poweredSpeed() {
        return BenchConfig.poweredSpeed();
    }

    /** L'état de la machine, lu sur le bloc tel que le client le connaît. */
    private MachineState machineState() {
        if (minecraft.level == null) {
            return MachineState.STOPPED;
        }
        BlockState state = minecraft.level.getBlockState(menu.pos());
        return state.hasProperty(ElectricMachineBlock.STATE) ? state.getValue(ElectricMachineBlock.STATE)
                : MachineState.STOPPED;
    }

    /** Vitesse de la tête de file maintenant : alimentée seulement si l'établi tourne. */
    double currentSpeed() {
        return machineState() == MachineState.RUNNING ? poweredSpeed() : manualSpeed();
    }

    // ================================================================================================
    // Actions
    // ================================================================================================

    /** Envoie la commande ; le serveur valide, prélève et répond par la file. */
    public void order() {
        if (selected != null) {
            BenchNetwork.CHANNEL.sendToServer(new OrderPacket(selected.id(), quantity, menu.pos()));
        }
    }

    /** Annule une fabrication de la file ; le serveur rend les ingrédients payés. */
    public void cancel(UUID entry) {
        BenchNetwork.CHANNEL.sendToServer(new CancelPacket(entry));
    }

    private void openStock() {
        BenchNetwork.CHANNEL.sendToServer(new OpenStockPacket(menu.pos()));
    }

    // ================================================================================================
    // Catalogue, filtre, inventaire
    // ================================================================================================

    /** Suit l'arrivée d'un nouveau catalogue et les changements d'inventaire. */
    private void syncCatalog() {
        List<BenchRecipe> catalog = BenchClientState.catalog();
        if (catalog != catalogSeen) {
            catalogSeen = catalog;
            List<BenchRecipe> byName = new ArrayList<>(catalog);
            byName.sort(Comparator.comparing((BenchRecipe r) -> BenchClientState.normalize(
                    BenchClientState.displayName(r))).thenComparing(r -> r.id().toString()));
            sorted = byName;
            categories.rebuild(catalog);
            if (selected != null) {
                BenchRecipe same = null;
                for (BenchRecipe r : catalog) {
                    if (r.id().equals(selected.id())) {
                        same = r;
                        break;
                    }
                }
                selected = same;
            }
            affordable.clear();
            refilter(true);
        }
        int signature = BenchCost.signature(inventory());
        if (signature != inventorySignature) {
            inventorySignature = signature;
            affordable.clear();
        }
        detail.refresh(selected, inventory());
    }

    private void refilter(boolean keepScroll) {
        String query = search == null ? "" : search.getValue();
        shown = BenchClientState.filter(sorted, category, query);
        list.setItems(shown, keepScroll);
        if (selected != null && !keepScroll) {
            list.reveal(selected);
        }
    }

    // ================================================================================================
    // Dessin
    // ================================================================================================

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Tout est dessiné dans render().
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Pas d'étiquettes de menu : le titre est dessiné par render().
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        syncCatalog();
        renderBackground(g);
        BenchTheme.plate(g, leftPos, topPos, imageWidth, imageHeight);
        BenchTheme.rivets(g, leftPos, topPos, imageWidth, imageHeight);
        renderTitleBar(g, mx, my);

        categories.render(g, mx, my);
        renderSearch(g, mx, my);
        list.render(g, mx, my);
        detail.render(g, mx, my);
        queue.render(g, mx, my);

        // Une seule infobulle, la plus précise : celle du premier panneau survolé.
        if (!tooltipTitleBar(g, mx, my)) {
            for (BenchPane pane : panes) {
                if (pane.tooltip(g, mx, my)) {
                    break;
                }
            }
        }
    }

    private void renderTitleBar(GuiGraphics g, int mx, int my) {
        int ty = topPos + FRAME;
        String name = BenchTheme.ellipsize(font, title.getString(), 150);
        g.drawString(font, name, leftPos + FRAME + 3, ty + 3, BenchTheme.BRASS_LIGHT, false);

        // La vitesse du moment, avec une lampe : verte quand l'établi est alimenté.
        MachineState state = machineState();
        String key = switch (state) {
            case RUNNING -> "gui.oneforallsurvival.bench.speed.powered";
            case NO_POWER -> "gui.oneforallsurvival.bench.speed.no_power";
            case FAULT -> "gui.oneforallsurvival.bench.speed.fault";
            default -> "gui.oneforallsurvival.bench.speed.manual";
        };
        double speed = state == MachineState.RUNNING ? poweredSpeed() : manualSpeed();
        String text = Component.translatable(key, BenchFormat.speed(speed, Locale.getDefault())).getString();
        int lampColor = switch (state) {
            case RUNNING -> BenchTheme.TEXT_OK;
            case NO_POWER, FAULT -> BenchTheme.TEXT_BAD;
            default -> BenchTheme.TEXT_DIM;
        };
        int room = stockX - 6 - (leftPos + FRAME + 3 + 150 + 8) - 9;
        String shownText = BenchTheme.ellipsize(font, text, room);
        speedW = font.width(shownText) + 9;
        speedX = stockX - 8 - speedW;
        g.fill(speedX, ty + 3, speedX + 6, ty + 9, BenchTheme.METAL_DARK);
        g.fill(speedX + 1, ty + 4, speedX + 5, ty + 8, lampColor);
        g.drawString(font, shownText, speedX + 9, ty + 3, BenchTheme.TEXT, false);

        int stock = BenchClientState.stockItems();
        String label = Component.translatable("gui.oneforallsurvival.bench.stock", stock).getString();
        BenchTheme.button(g, font, stockX, stockY, STOCK_W, TITLE_H - 1, label,
                BenchTheme.inside(mx, my, stockX, stockY, STOCK_W, TITLE_H - 1), true, stock > 0);
    }

    private boolean tooltipTitleBar(GuiGraphics g, int mx, int my) {
        if (BenchTheme.inside(mx, my, stockX, stockY, STOCK_W, TITLE_H - 1)) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.oneforallsurvival.bench.stock.tooltip"));
            tip.add(Component.translatable("gui.oneforallsurvival.bench.stock.count", BenchClientState.stockItems())
                    .withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, tip, mx, my);
            return true;
        }
        if (BenchTheme.inside(mx, my, speedX - 2, topPos + FRAME, speedW + 4, TITLE_H)) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.oneforallsurvival.bench.speed.tooltip.manual",
                    BenchFormat.speed(manualSpeed(), Locale.getDefault())));
            tip.add(Component.translatable("gui.oneforallsurvival.bench.speed.tooltip.powered",
                    BenchFormat.speed(poweredSpeed(), Locale.getDefault())).withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, tip, mx, my);
            return true;
        }
        return false;
    }

    private void renderSearch(GuiGraphics g, int mx, int my) {
        int bx = search.getX() - 4;
        int by = search.getY() - 3;
        int bw = search.getWidth() + 8 + 26;
        BenchTheme.well(g, bx, by, bw, SEARCH_H);
        if (search.getValue().isEmpty()) {
            String hint = Component.translatable("gui.oneforallsurvival.bench.search").getString();
            g.drawString(font, BenchTheme.ellipsize(font, hint, search.getWidth() - 2), search.getX(),
                    search.getY() + 1, BenchTheme.TEXT_DIM, false);
        }
        search.render(g, mx, my, 0);
        String hits = Integer.toString(shown.size());
        g.drawString(font, hits, bx + bw - 4 - font.width(hits), by + 3, BenchTheme.TEXT_DIM, false);
    }

    // ================================================================================================
    // Entrée
    // ================================================================================================

    @Override
    protected void containerTick() {
        super.containerTick();
        search.tick();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && BenchTheme.inside(mx, my, stockX, stockY, STOCK_W, TITLE_H - 1)) {
            openStock();
            return true;
        }
        for (BenchPane pane : panes) {
            if (pane.mouseClicked(mx, my, button)) {
                dragged = pane;
                break;
            }
        }
        // La recherche garde toujours le clavier : taper « e » ne ferme pas l'écran.
        if (BenchTheme.inside(mx, my, search.getX(), search.getY(), search.getWidth(), 10)) {
            search.mouseClicked(mx, my, button);
        }
        setFocused(search);
        search.setFocused(true);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return dragged != null && dragged.mouseDragged(mx, my);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        for (BenchPane pane : panes) {
            pane.mouseReleased();
        }
        dragged = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        for (BenchPane pane : panes) {
            if (pane.mouseScrolled(mx, my, delta)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_UP -> list.moveSelection(-1);
            case GLFW.GLFW_KEY_DOWN -> list.moveSelection(1);
            case GLFW.GLFW_KEY_PAGE_UP -> list.page(-1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> list.page(1);
            default -> {
                if (search.keyPressed(key, scan, modifiers)) {
                    return true;
                }
                // La touche d'inventaire ferme d'ordinaire l'écran ; ici elle appartient à la recherche.
                return minecraft.options.keyInventory.matches(key, scan);
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return search.charTyped(c, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
